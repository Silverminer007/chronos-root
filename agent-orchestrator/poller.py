from datetime import datetime
from pathlib import Path
from typing import List, Dict, Any

from state import StateManager
from github_api import GitHubAPI


class Poller:
    def __init__(self, repo: str, state_file: str, log_file: str):
        self.repo = repo
        self.state_mgr = StateManager(state_file)
        self.github = GitHubAPI(repo)
        self.log_file = log_file
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

        # Load current state
        state = self.state_mgr.load()
        state["last_poll"] = datetime.utcnow().isoformat() + "Z"

        # Discover new tickets
        tickets = self._discover_ready_for_agent()
        self._log(f"Found {len(tickets)} ready-for-agent issues")

        # Save updated state
        self.state_mgr.save(state)
        self._log("State saved")
