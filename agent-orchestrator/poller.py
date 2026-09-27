from datetime import datetime
from pathlib import Path
from typing import List, Dict, Any

from state import State, AgentState, CompletedTicket
from github_api import GitHubAPI
from worktree_manager import WorktreeManager
from agent_spawner import AgentSpawner, AgentType
import logging


logger = logging.getLogger(__name__)


class Poller:
    """Orchestrates agent-based ticket implementation."""

    def __init__(self, repo: str, repo_path: str, state_file: str, log_file: str, worktree_base: str):
        self.repo = repo
        self.repo_path = repo_path
        self.state = State(state_file)
        self.github = GitHubAPI(repo)
        self.worktree_mgr = WorktreeManager(base_path=worktree_base, repo_path=repo_path)
        self.agent_spawner = AgentSpawner(repo_path=repo_path)
        self.log_file = log_file
        Path(log_file).parent.mkdir(parents=True, exist_ok=True)

    def _log(self, message: str) -> None:
        """Log with ISO 8601 timestamp."""
        timestamp = datetime.utcnow().isoformat() + "Z"
        log_line = f"[{timestamp}] {message}\n"
        with open(self.log_file, "a") as f:
            f.write(log_line)

    def _discover_ready_for_agent(self) -> List[Dict[str, Any]]:
        """Discover issues labeled ready-for-agent and claim them."""
        self._log("Discovering ready-for-agent issues")
        issues = self.github.list_ready_for_agent()

        for issue in issues:
            # Skip if already claimed
            if self.state.get_active_agent(issue["number"]):
                self._log(f"Issue #{issue['number']} already has an active agent")
                continue

            # Apply in-progress label to claim it
            if self.github.add_label(issue["number"], "in-progress"):
                self._log(f"Claimed issue #{issue['number']}")
            else:
                self._log(f"Failed to claim issue #{issue['number']}")

        return issues

    def _spawn_agent_for_ticket(self, ticket_id: int, branch_name: str) -> bool:
        """Create worktree and spawn TDD agent for ticket."""
        try:
            # Create worktree
            worktree_path = self.worktree_mgr.create_worktree(
                ticket_id=ticket_id,
                branch_name=branch_name
            )
            self._log(f"Created worktree for ticket #{ticket_id}: {worktree_path}")

            # Spawn TDD agent
            pid = self.agent_spawner.spawn_agent(
                ticket_id=ticket_id,
                agent_type=AgentType.TDD,
                worktree_path=worktree_path,
                branch_name=branch_name
            )

            if pid is None:
                self._log(f"Failed to spawn agent for ticket #{ticket_id}")
                return False

            # Track agent in state
            agent = AgentState(
                ticket_id=ticket_id,
                agent_type=AgentType.TDD.value,
                worktree_path=worktree_path,
                started_at=datetime.utcnow().isoformat() + "Z",
                status="running",
                pid=pid
            )
            self.state.add_active_agent(agent)
            self._log(f"Spawned TDD agent for ticket #{ticket_id} (PID: {pid})")
            return True

        except Exception as e:
            self._log(f"Error spawning agent for ticket #{ticket_id}: {str(e)}")
            return False

    def _poll_active_agents(self) -> None:
        """Check status of active agents and update state."""
        for agent in list(self.state.active_agents):
            if not self.agent_spawner.is_agent_running(agent.pid):
                self._log(
                    f"Agent for ticket #{agent.ticket_id} (PID {agent.pid}) has finished"
                )

                # Move to completed tickets (mark as ready for review)
                completed = CompletedTicket(
                    ticket_id=agent.ticket_id,
                    pr_number=agent.pr_number,
                    status="completed",
                    completed_at=datetime.utcnow().isoformat() + "Z"
                )
                self.state.add_completed_ticket(completed)
                self.state.remove_active_agent(agent.ticket_id)

                # Clean up worktree
                try:
                    self.worktree_mgr.remove_worktree(agent.worktree_path)
                    self._log(f"Cleaned up worktree for ticket #{agent.ticket_id}")
                except Exception as e:
                    self._log(f"Failed to cleanup worktree for ticket #{agent.ticket_id}: {str(e)}")

    def run_once(self) -> None:
        """Run one complete poll cycle."""
        self._log("Poll cycle started")

        # Poll active agents and clean up completed ones
        self._poll_active_agents()

        # Discover new tickets
        tickets = self._discover_ready_for_agent()
        self._log(f"Found {len(tickets)} ready-for-agent issues")

        # Spawn agents for new tickets
        for ticket in tickets:
            ticket_id = ticket["number"]
            branch_name = f"feature/{ticket_id}-ticket"

            # Check if we already have an agent for this ticket
            if not self.state.get_active_agent(ticket_id):
                self._spawn_agent_for_ticket(ticket_id, branch_name)

        # Update last poll time and save state
        self.state.last_poll = datetime.utcnow().isoformat() + "Z"
        self.state.save()
        self._log("Poll cycle completed")
