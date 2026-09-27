import json
import os
import signal
import tempfile
from datetime import datetime, timedelta
from pathlib import Path
from typing import List, Dict, Any, Optional
import subprocess

from state import StateManager
from github_api import GitHubAPI


class AgentTimeout(Exception):
    pass


class AgentCrash(Exception):
    pass


class StateCorruption(Exception):
    pass


class ErrorRecoveryManager:
    """Handles error detection, recovery, and cleanup."""

    AGENT_TIMEOUT_HOURS = 6
    GRACEFUL_SHUTDOWN_TIMEOUT_SECONDS = 300

    RETRY_LIMITS = {
        "spec-validator": 0,
        "tdd": 1,
        "code-review": 0,
        "fixer": 3
    }

    def __init__(self, state_file: str, log_file: str, repo: str = None):
        self.state_mgr = StateManager(state_file)
        self.log_file = log_file
        self.state_file = state_file
        self.github = GitHubAPI(repo) if repo else None
        Path(log_file).parent.mkdir(parents=True, exist_ok=True)

    def load_state(self) -> dict:
        """Load state with corruption detection."""
        try:
            return self.state_mgr.load()
        except Exception as e:
            self.log_error(
                ticket_id=None,
                agent_type="system",
                level="ERROR",
                message="Failed to load state",
                details={"error": str(e)}
            )
            raise StateCorruption(f"Failed to load state: {e}")

    def detect_timeouts(self) -> List[Dict[str, Any]]:
        """Detect agents running longer than AGENT_TIMEOUT_HOURS."""
        state = self.load_state()
        timed_out = []

        now = datetime.utcnow()
        timeout_threshold = now - timedelta(hours=self.AGENT_TIMEOUT_HOURS)

        for agent in state.get("active_agents", []):
            try:
                started_str = agent["started_at"].rstrip("Z")
                started = datetime.fromisoformat(started_str)
                if started < timeout_threshold:
                    timed_out.append(agent)
                    self.log_error(
                        ticket_id=agent["ticket_id"],
                        agent_type=agent["agent_type"],
                        level="WARN",
                        message="Agent timeout detected",
                        details={
                            "started_at": agent["started_at"],
                            "timeout_hours": self.AGENT_TIMEOUT_HOURS
                        }
                    )
            except Exception as e:
                self.log_error(
                    ticket_id=agent.get("ticket_id"),
                    agent_type=agent.get("agent_type"),
                    level="ERROR",
                    message="Failed to parse agent start time",
                    details={"error": str(e)}
                )

        return timed_out

    def detect_crashes(self) -> List[Dict[str, Any]]:
        """Detect agents whose processes no longer exist."""
        state = self.load_state()
        crashed = []

        for agent in state.get("active_agents", []):
            pid = agent.get("pid")
            if pid is None:
                continue

            # Check if process exists by sending signal 0 (no-op)
            try:
                os.kill(pid, 0)
            except (OSError, ProcessLookupError):
                crashed.append(agent)
                self.log_error(
                    ticket_id=agent["ticket_id"],
                    agent_type=agent["agent_type"],
                    level="WARN",
                    message="Agent process not found",
                    details={"pid": pid}
                )

        return crashed

    def handle_timeout(self, agent: Dict[str, Any]) -> dict:
        """Handle timed-out agent: kill process, cleanup worktree, label issue."""
        state = self.load_state()

        ticket_id = agent["ticket_id"]
        pid = agent.get("pid")

        # Kill agent process
        if pid:
            try:
                os.kill(pid, signal.SIGTERM)
                self.log_error(
                    ticket_id=ticket_id,
                    agent_type=agent["agent_type"],
                    level="INFO",
                    message="Sent SIGTERM to timed-out agent",
                    details={"pid": pid}
                )
                # Try SIGKILL if still running
                try:
                    import time
                    time.sleep(1)
                    os.kill(pid, signal.SIGKILL)
                except:
                    pass
            except ProcessLookupError:
                pass

        # Cleanup worktree
        worktree_path = agent.get("worktree_path")
        if worktree_path:
            self._cleanup_worktree(worktree_path)

        # Label issue as agent-failed
        if self.github:
            self.github.add_label(ticket_id, "agent-failed")
            self.github.post_comment(
                ticket_id,
                f"Agent timeout after {self.AGENT_TIMEOUT_HOURS} hours of inactivity"
            )

        # Remove from state
        state["active_agents"] = [
            a for a in state["active_agents"]
            if a["ticket_id"] != ticket_id
        ]

        self.state_mgr.save(state)
        return state

    def handle_crash(self, agent: Dict[str, Any], crash_count: int = 1) -> Optional[str]:
        """Handle crashed agent: decide whether to retry or mark failed."""
        state = self.load_state()
        ticket_id = agent["ticket_id"]
        agent_type = agent["agent_type"]

        self.log_error(
            ticket_id=ticket_id,
            agent_type=agent_type,
            level="WARN",
            message=f"Agent crash detected (attempt {crash_count})",
            details={"crash_count": crash_count}
        )

        # Check retry logic
        if self.should_retry_agent(agent_type, crash_count):
            action = "retry"
            self.log_error(
                ticket_id=ticket_id,
                agent_type=agent_type,
                level="INFO",
                message=f"Will retry agent ({crash_count}/{self.RETRY_LIMITS.get(agent_type, 0)})",
                details={}
            )
        else:
            action = "failed"
            if self.github:
                self.github.add_label(ticket_id, "agent-failed")
                self.github.post_comment(
                    ticket_id,
                    f"Agent failed after {crash_count} crash(es). Manual intervention required."
                )
            self.log_error(
                ticket_id=ticket_id,
                agent_type=agent_type,
                level="ERROR",
                message="Agent exceeded retry limit",
                details={"crash_count": crash_count}
            )

        # Cleanup worktree
        worktree_path = agent.get("worktree_path")
        if worktree_path:
            self._cleanup_worktree(worktree_path)

        # Remove from active agents
        state["active_agents"] = [
            a for a in state["active_agents"]
            if a["ticket_id"] != ticket_id
        ]

        self.state_mgr.save(state)
        return action

    def recover_corrupted_state(self) -> dict:
        """Recover from corrupted state.json."""
        backup_path = self.state_file + ".backup"

        # Check if file exists and is valid
        if Path(self.state_file).exists():
            try:
                with open(self.state_file) as f:
                    json.load(f)
                return self.state_mgr.load()
            except (json.JSONDecodeError, IOError):
                # Rename corrupted file to backup
                Path(self.state_file).rename(backup_path)
                self.log_error(
                    ticket_id=None,
                    agent_type="system",
                    level="ERROR",
                    message="State file corrupted, created backup",
                    details={"backup_location": backup_path}
                )

        # Create fresh state
        fresh_state = self.state_mgr._default_state()
        self.state_mgr.save(fresh_state)

        return fresh_state

    def find_orphaned_worktrees(self, worktrees_dir: str, state: dict) -> List[str]:
        """Find worktree directories not referenced in state."""
        active_paths = {
            agent.get("worktree_path")
            for agent in state.get("active_agents", [])
            if agent.get("worktree_path")
        }

        orphaned = []

        try:
            for entry in Path(worktrees_dir).iterdir():
                if entry.is_dir() and entry.name.startswith("worktree-"):
                    if entry.resolve() not in active_paths and str(entry.resolve()) not in active_paths:
                        orphaned.append(str(entry))
                        self.log_error(
                            ticket_id=None,
                            agent_type="system",
                            level="WARN",
                            message="Found orphaned worktree",
                            details={"path": str(entry)}
                        )
        except FileNotFoundError:
            pass

        return orphaned

    def cleanup_orphaned_worktrees(self, worktrees_dir: str, state: dict) -> int:
        """Clean up orphaned worktrees."""
        orphaned = self.find_orphaned_worktrees(worktrees_dir, state)
        cleaned = 0

        for path in orphaned:
            try:
                self._cleanup_worktree(path)
                cleaned += 1
            except Exception as e:
                self.log_error(
                    ticket_id=None,
                    agent_type="system",
                    level="ERROR",
                    message="Failed to cleanup orphaned worktree",
                    details={"path": path, "error": str(e)}
                )

        return cleaned

    def _cleanup_worktree(self, worktree_path: str) -> None:
        """Clean up a git worktree."""
        try:
            # Kill any processes in the worktree
            subprocess.run(
                f"fuser -k -9 {worktree_path} 2>/dev/null || true",
                shell=True,
                timeout=5
            )

            # Remove worktree with git
            subprocess.run(
                ["git", "worktree", "remove", worktree_path, "--force"],
                timeout=10,
                capture_output=True
            )

            self.log_error(
                ticket_id=None,
                agent_type="system",
                level="INFO",
                message="Cleaned up worktree",
                details={"path": worktree_path}
            )
        except Exception as e:
            # Force removal if git worktree command fails
            try:
                import shutil
                shutil.rmtree(worktree_path, ignore_errors=True)
            except:
                pass

    def should_retry_agent(self, agent_type: str, crash_count: int) -> bool:
        """Determine if agent should be retried based on type and crash count."""
        max_retries = self.RETRY_LIMITS.get(agent_type, 0)
        return crash_count <= max_retries

    def handle_graceful_shutdown(self, timeout_seconds: int = None) -> None:
        """Handle graceful shutdown: wait for agents, kill remaining, save state."""
        if timeout_seconds is None:
            timeout_seconds = self.GRACEFUL_SHUTDOWN_TIMEOUT_SECONDS

        state = self.load_state()

        self.log_error(
            ticket_id=None,
            agent_type="system",
            level="INFO",
            message="Graceful shutdown initiated",
            details={"timeout_seconds": timeout_seconds}
        )

        import time
        start_time = time.time()

        # Send SIGTERM to all agents
        for agent in state.get("active_agents", []):
            pid = agent.get("pid")
            if pid:
                try:
                    os.kill(pid, signal.SIGTERM)
                except ProcessLookupError:
                    pass

        # Wait for agents to finish
        while time.time() - start_time < timeout_seconds:
            state = self.load_state()
            active_pids = [a.get("pid") for a in state.get("active_agents", []) if a.get("pid")]

            still_running = []
            for pid in active_pids:
                try:
                    os.kill(pid, 0)
                    still_running.append(pid)
                except ProcessLookupError:
                    pass

            if not still_running:
                break

            time.sleep(1)

        # Kill remaining agents
        state = self.load_state()
        for agent in state.get("active_agents", []):
            pid = agent.get("pid")
            if pid:
                try:
                    os.kill(pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass

        # Mark agents as terminated
        for agent in state.get("active_agents", []):
            agent["status"] = "terminated"

        self.state_mgr.save(state)

        self.log_error(
            ticket_id=None,
            agent_type="system",
            level="INFO",
            message="Graceful shutdown complete",
            details={}
        )

    def log_error(self, ticket_id: Optional[int], agent_type: str, level: str, message: str, details: Dict[str, Any]) -> None:
        """Log structured error with context."""
        timestamp = datetime.utcnow().isoformat() + "Z"

        details_str = ""
        if details:
            details_str = " | " + " ".join(f"{k}={v}" for k, v in details.items())

        log_line = f"[{timestamp}] [{level}] [ticket={ticket_id}] [{agent_type}] {message}{details_str}\n"

        with open(self.log_file, "a") as f:
            f.write(log_line)
