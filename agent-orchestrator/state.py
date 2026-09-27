import json
import os
import logging
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from typing import List, Dict, Any, Optional


logger = logging.getLogger(__name__)


class AgentState:
    """Represents the state of a single running agent."""

    def __init__(
        self,
        ticket_id: int,
        agent_type: str,
        worktree_path: str,
        started_at: str,
        status: str = "running",
        pid: Optional[int] = None,
        ci_poll_rounds: int = 0,
        pr_number: Optional[int] = None
    ):
        self.ticket_id = ticket_id
        self.agent_type = agent_type
        self.worktree_path = worktree_path
        self.started_at = started_at
        self.status = status
        self.pid = pid
        self.ci_poll_rounds = ci_poll_rounds
        self.pr_number = pr_number

    def to_dict(self) -> Dict[str, Any]:
        return {
            "ticket_id": self.ticket_id,
            "agent_type": self.agent_type,
            "worktree_path": self.worktree_path,
            "started_at": self.started_at,
            "status": self.status,
            "pid": self.pid,
            "ci_poll_rounds": self.ci_poll_rounds,
            "pr_number": self.pr_number
        }

    @staticmethod
    def from_dict(data: Dict[str, Any]) -> "AgentState":
        return AgentState(**data)


class CompletedTicket:
    """Represents a completed ticket."""

    def __init__(
        self,
        ticket_id: int,
        pr_number: int,
        status: str,
        completed_at: str
    ):
        self.ticket_id = ticket_id
        self.pr_number = pr_number
        self.status = status
        self.completed_at = completed_at

    def to_dict(self) -> Dict[str, Any]:
        return {
            "ticket_id": self.ticket_id,
            "pr_number": self.pr_number,
            "status": self.status,
            "completed_at": self.completed_at
        }

    @staticmethod
    def from_dict(data: Dict[str, Any]) -> "CompletedTicket":
        return CompletedTicket(**data)


class State:
    """Manages orchestrator state with atomic writes."""

    def __init__(self, file_path: str):
        self.file_path = file_path
        self.last_poll: Optional[str] = None
        self.active_agents: List[AgentState] = []
        self.completed_tickets: List[CompletedTicket] = []
        self.load()

    def load(self):
        """Load state from file. Creates default state if file doesn't exist."""
        if not os.path.exists(self.file_path):
            logger.info(f"State file {self.file_path} does not exist. Creating default state.")
            self.last_poll = None
            self.active_agents = []
            self.completed_tickets = []
            return

        try:
            with open(self.file_path, 'r') as f:
                data = json.load(f)

            self.last_poll = data.get("last_poll")
            self.active_agents = [
                AgentState.from_dict(agent) for agent in data.get("active_agents", [])
            ]
            self.completed_tickets = [
                CompletedTicket.from_dict(ticket) for ticket in data.get("completed_tickets", [])
            ]
        except json.JSONDecodeError:
            logger.error(f"Failed to parse state file {self.file_path}. Resetting to default.")
            self.last_poll = None
            self.active_agents = []
            self.completed_tickets = []

    def save(self):
        """Save state to file with atomic writes."""
        state_data = {
            "last_poll": self.last_poll or datetime.now(timezone.utc).isoformat(),
            "active_agents": [agent.to_dict() for agent in self.active_agents],
            "completed_tickets": [ticket.to_dict() for ticket in self.completed_tickets]
        }

        # Atomic write using temporary file
        Path(self.file_path).parent.mkdir(parents=True, exist_ok=True)
        temp_path = f"{self.file_path}.tmp"
        try:
            with open(temp_path, 'w') as f:
                json.dump(state_data, f, indent=2)
            os.replace(temp_path, self.file_path)
            logger.debug(f"State saved to {self.file_path}")
        except Exception as e:
            logger.error(f"Failed to save state: {e}")
            if os.path.exists(temp_path):
                os.remove(temp_path)
            raise

    def add_active_agent(self, agent: AgentState):
        """Add an active agent to state."""
        self.active_agents.append(agent)
        self.save()

    def remove_active_agent(self, ticket_id: int) -> bool:
        """Remove an active agent by ticket ID. Returns True if found."""
        original_count = len(self.active_agents)
        self.active_agents = [a for a in self.active_agents if a.ticket_id != ticket_id]
        if len(self.active_agents) < original_count:
            self.save()
            return True
        return False

    def get_active_agent(self, ticket_id: int) -> Optional[AgentState]:
        """Get an active agent by ticket ID."""
        for agent in self.active_agents:
            if agent.ticket_id == ticket_id:
                return agent
        return None

    def add_completed_ticket(self, ticket: CompletedTicket):
        """Add a completed ticket to state."""
        self.completed_tickets.append(ticket)
        self.save()


class StateManager:
    """
    State manager for backward compatibility with initial poller scaffold.

    Provides dict-based state interface (load/save) for code that may
    inherit from the scaffold. New code should use State class instead.
    """

    def __init__(self, state_file: str):
        self.state_file = state_file
        Path(state_file).parent.mkdir(parents=True, exist_ok=True)

    def load(self) -> dict:
        if not Path(self.state_file).exists():
            return self._default_state()
        try:
            with open(self.state_file) as f:
                return json.load(f)
        except (json.JSONDecodeError, IOError):
            return self._default_state()

    def save(self, state: dict) -> None:
        # Write to temp file first, then atomic rename (prevents partial writes)
        temp_fd, temp_path = tempfile.mkstemp(dir=Path(self.state_file).parent)
        try:
            with open(temp_fd, 'w') as f:
                json.dump(state, f, indent=2)
            Path(temp_path).replace(self.state_file)
        except Exception:
            Path(temp_path).unlink(missing_ok=True)
            raise

    @staticmethod
    def _default_state() -> dict:
        return {
            "last_poll": datetime.utcnow().isoformat() + "Z",
            "active_agents": [],
            "completed_tickets": []
        }
