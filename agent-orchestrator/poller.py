from datetime import datetime, timezone
from pathlib import Path
from typing import List, Dict, Any
from enum import Enum

from state import StateManager
from github_api import GitHubAPI
from error_recovery import ErrorRecoveryManager


class CIStatus(str, Enum):
    PENDING = "pending"
    SUCCESS = "success"
    FAILURE = "failure"
    UNKNOWN = "unknown"


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
        timestamp = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
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

    def _get_pr_state(self, state: Dict[str, Any], pr_number: int) -> Dict[str, Any]:
        """Get PR monitoring state, initializing if needed."""
        if "pr_monitoring" not in state:
            state["pr_monitoring"] = {}

        pr_key = str(pr_number)
        if pr_key not in state["pr_monitoring"]:
            state["pr_monitoring"][pr_key] = {
                "pr_number": pr_number,
                "ticket_id": None,
                "ci_status": CIStatus.PENDING.value,
                "ci_polls": 0,
                "last_ci_poll": None,
                "retry_count": 0
            }
        return state["pr_monitoring"][pr_key]

    def check_pr_status(self, pr_number: int) -> Dict[str, Any]:
        """Check the CI status of a PR."""
        return self.github.get_pr_checks(pr_number)

    def poll_pr_ci(self, pr_number: int) -> None:
        """Poll and handle PR CI status."""
        state = self.state_mgr.load()
        pr_state = self._get_pr_state(state, pr_number)

        # Check if polling should continue
        if not self.should_continue_polling(pr_number):
            self._log(f"CI polling timeout for PR #{pr_number}")
            if pr_state["ci_status"] == CIStatus.PENDING.value and pr_state.get("ticket_id"):
                self.github.add_label(pr_state["ticket_id"], "agent-failed")
                self.github.post_comment(
                    pr_state["ticket_id"],
                    f"CI pending for >2.5m on PR #{pr_number}, manual investigation needed"
                )
            return

        # Get PR status
        status_info = self.check_pr_status(pr_number)
        ci_status = status_info.get("status", CIStatus.UNKNOWN.value)

        # Update state
        pr_state["ci_status"] = ci_status
        pr_state["ci_polls"] = pr_state.get("ci_polls", 0) + 1
        pr_state["last_ci_poll"] = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")

        self._log(f"PR #{pr_number} CI status: {ci_status} (poll #{pr_state['ci_polls']})")

        if ci_status == CIStatus.SUCCESS.value:
            if pr_state.get("ticket_id"):
                self.github.add_label(pr_state["ticket_id"], "ready-for-review")
            self._log(f"PR #{pr_number} CI passed, labeled ready-for-review")
        elif ci_status == CIStatus.FAILURE.value:
            if self.should_retry_pr(pr_number):
                pr_state["retry_count"] = pr_state.get("retry_count", 0) + 1
                self._log(f"PR #{pr_number} CI failed, retry #{pr_state['retry_count']}")
            else:
                if pr_state.get("ticket_id"):
                    self.github.add_label(pr_state["ticket_id"], "agent-failed")
                    self.github.post_comment(
                        pr_state["ticket_id"],
                        f"CI failed after 3 retry cycles on PR #{pr_number}, manual review needed"
                    )
                self._log(f"PR #{pr_number} CI failed, max retries exceeded")

        self.state_mgr.save(state)

    def should_retry_pr(self, pr_number: int) -> bool:
        """Check if PR should be retried (max 3 retries)."""
        state = self.state_mgr.load()
        pr_state = self._get_pr_state(state, pr_number)
        retry_count = pr_state.get("retry_count", 0)
        return retry_count < 3

    def should_continue_polling(self, pr_number: int) -> bool:
        """Check if polling should continue (max 5 polls = 2.5 minutes)."""
        state = self.state_mgr.load()
        pr_state = self._get_pr_state(state, pr_number)
        polls = pr_state.get("ci_polls", 0)
        return polls < 5

    def _discover_prs_needing_polling(self) -> List[int]:
        """Discover PRs with active agents awaiting CI results."""
        state = self.state_mgr.load()
        pr_numbers = []

        if "pr_monitoring" in state:
            for pr_key, pr_data in state["pr_monitoring"].items():
                ci_status = pr_data.get("ci_status")
                if ci_status in (CIStatus.PENDING.value, CIStatus.FAILURE.value):
                    pr_numbers.append(pr_data.get("pr_number"))

        return pr_numbers

    def _spawn_code_review_agent(self, pr_number: int, ticket_id: int) -> None:
        """Spawn Code Review agent for a failed PR."""
        self._log(f"Spawning Code Review agent for PR #{pr_number}, ticket #{ticket_id}")

    def _spawn_fixer_agent(self, pr_number: int, ticket_id: int, findings: List[str]) -> None:
        """Spawn Fixer agent to address code review findings."""
        self._log(f"Spawning Fixer agent for PR #{pr_number}, ticket #{ticket_id}")

    def can_spawn_agent(self) -> bool:
        """Check if we can spawn a new agent (max 3 concurrent agents)."""
        self.state_mgr.prune_dead_agents()
        return self.state_mgr.count_active_agents() < 3

    def register_agent(self, ticket_id: int, agent_type: str, pid: int, worktree_path: str) -> None:
        """Register a spawned agent."""
        self.state_mgr.add_agent(ticket_id, agent_type, pid, worktree_path)
        self._log(f"Registered agent for ticket #{ticket_id}: {agent_type} (PID {pid})")

    def unregister_agent(self, ticket_id: int) -> None:
        """Unregister a completed agent."""
        self.state_mgr.remove_agent(ticket_id)
        self._log(f"Unregistered agent for ticket #{ticket_id}")

    def run_once(self) -> None:
        """Run one poll cycle."""
        self._log("Poll cycle started")

        # Prune dead agents first
        self.state_mgr.prune_dead_agents()

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

        # Poll CI status for PRs awaiting results
        prs_to_poll = self._discover_prs_needing_polling()
        for pr_number in prs_to_poll:
            self.poll_pr_ci(pr_number)

        # Discover new tickets
        state = self.state_mgr.load()
        state["last_poll"] = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
        tickets = self._discover_ready_for_agent()
        self._log(f"Found {len(tickets)} ready-for-agent issues")

        # Save updated state
        self.state_mgr.save(state)
        self._log("State saved")
