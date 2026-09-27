from datetime import datetime
from pathlib import Path
from typing import List, Dict, Any

from state import StateManager
from github_api import GitHubAPI
from error_recovery import ErrorRecoveryManager


class Poller:
    def __init__(self, repo: str, state_file: str, log_file: str, worktrees_dir: str = None):
        self.repo = repo
        self.state_mgr = StateManager(state_file)
        self.github = GitHubAPI(repo)
        self.recovery_mgr = ErrorRecoveryManager(state_file, log_file, repo=repo)
        self.log_file = log_file
        self.worktrees_dir = worktrees_dir
        Path(log_file).parent.mkdir(parents=True, exist_ok=True)

    def _log(self, message: str) -> None:
        """Log a message with timestamp."""
        timestamp = datetime.utcnow().isoformat() + "Z"
        log_line = f"[{timestamp}] {message}\n"
        with open(self.log_file, "a") as f:
            f.write(log_line)

    def _discover_ready_for_agent(self) -> List[Dict[str, Any]]:
        """Discover issues labeled ready-for-agent and apply in-progress label."""
        self._log("Discovering ready-for-agent issues")
        issues = self.github.list_ready_for_agent()

        for issue in issues:
            # Apply in-progress label to claim it
            if self.github.add_label(issue["number"], "in-progress"):
                self._log(f"Claimed issue #{issue['number']}")
            else:
                self._log(f"Failed to claim issue #{issue['number']}")

        return issues

    def run_once(self) -> None:
        """Run one poll cycle."""
        self._log("Poll cycle started")

        # Recover from state corruption if needed
        try:
            state = self.state_mgr.load()
        except Exception:
            self._log("Recovering from corrupted state")
            state = self.recovery_mgr.recover_corrupted_state()

        # Check for agent timeouts
        timed_out = self.recovery_mgr.detect_timeouts()
        for agent in timed_out:
            self._log(f"Handling timeout for ticket #{agent['ticket_id']}")
            state = self.recovery_mgr.handle_timeout(agent)

        # Check for agent crashes
        crashed = self.recovery_mgr.detect_crashes()
        for agent in crashed:
            self._log(f"Handling crash for ticket #{agent['ticket_id']}")
            action = self.recovery_mgr.handle_crash(agent, crash_count=1)
            state = self.state_mgr.load()
            self._log(f"Crash action for ticket #{agent['ticket_id']}: {action}")

        # Clean up orphaned worktrees if configured
        if self.worktrees_dir:
            state = self.state_mgr.load()
            cleaned = self.recovery_mgr.cleanup_orphaned_worktrees(self.worktrees_dir, state)
            if cleaned > 0:
                self._log(f"Cleaned up {cleaned} orphaned worktrees")

        # Discover new tickets
        state = self.state_mgr.load()
        state["last_poll"] = datetime.utcnow().isoformat() + "Z"
        tickets = self._discover_ready_for_agent()
        self._log(f"Found {len(tickets)} ready-for-agent issues")

        # Save updated state
        self.state_mgr.save(state)
        self._log("State saved")
