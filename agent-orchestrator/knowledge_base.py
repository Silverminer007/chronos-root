import json
import tempfile
from datetime import datetime
from pathlib import Path


class KnowledgeBase:
    """In-memory knowledge base for agent orchestrator.

    Stores:
    - Tickets: metadata about GitHub issues
    - Agents: agent types and their capabilities
    - Outcomes: history of agent-ticket relationships and results
    """

    def __init__(self):
        self.tickets = {}  # {ticket_id: {id, title, description, labels, created_at}}
        self.agents = {}   # {agent_type: {type, capabilities, success_rate}}
        self.outcomes = [] # [{ticket_id, agent_type, status, pr_number, timestamp}]
        self.dependencies = {}  # {ticket_id: [list of ticket_ids it depends on]}
        self.config = {}  # {key: value} for orchestrator configuration

    def add_ticket(self, ticket_id: int, title: str, description: str, labels: list = None) -> None:
        """Add or update a ticket in the knowledge base."""
        self.tickets[str(ticket_id)] = {
            "id": ticket_id,
            "title": title,
            "description": description,
            "labels": labels or [],
            "created_at": datetime.utcnow().isoformat() + "Z"
        }

    def add_agent(self, agent_type: str, capabilities: list, success_rate: float = 0.0) -> None:
        """Add or update an agent definition."""
        self.agents[agent_type] = {
            "type": agent_type,
            "capabilities": capabilities,
            "success_rate": success_rate
        }

    def record_outcome(
        self,
        ticket_id: int,
        agent_type: str,
        status: str,
        pr_number: int = None,
        notes: str = None
    ) -> None:
        """Record the outcome of an agent working on a ticket."""
        outcome = {
            "ticket_id": ticket_id,
            "agent_type": agent_type,
            "status": status,
            "timestamp": datetime.utcnow().isoformat() + "Z"
        }
        if pr_number is not None:
            outcome["pr_number"] = pr_number
        if notes is not None:
            outcome["notes"] = notes
        self.outcomes.append(outcome)

    def query_tickets_by_label(self, label: str) -> list:
        """Find all tickets with a given label."""
        return [
            ticket for ticket in self.tickets.values()
            if label in ticket["labels"]
        ]

    def get_agent_stats(self, agent_type: str) -> dict:
        """Compute success rate and stats for an agent type."""
        agent_outcomes = [o for o in self.outcomes if o["agent_type"] == agent_type]
        if not agent_outcomes:
            return {
                "total_attempts": 0,
                "success_count": 0,
                "success_rate": 0.0
            }

        success_count = sum(1 for o in agent_outcomes if o["status"] == "success")
        total = len(agent_outcomes)

        return {
            "total_attempts": total,
            "success_count": success_count,
            "success_rate": success_count / total if total > 0 else 0.0
        }

    def query_agents_by_capability(self, capability: str) -> list:
        """Find all agents with a given capability."""
        return [
            agent for agent in self.agents.values()
            if capability in agent["capabilities"]
        ]

    def find_best_agent_for_capabilities(self, capabilities: list) -> dict:
        """Find agent with highest success rate matching all capabilities."""
        matching_agents = [
            agent for agent in self.agents.values()
            if all(cap in agent["capabilities"] for cap in capabilities)
        ]
        if not matching_agents:
            return None
        return max(matching_agents, key=lambda a: a.get("success_rate", 0))

    def add_dependency(self, ticket_id: int, depends_on: int) -> None:
        """Add a dependency: ticket_id depends on depends_on."""
        ticket_id_str = str(ticket_id)
        if ticket_id_str not in self.dependencies:
            self.dependencies[ticket_id_str] = []
        if depends_on not in self.dependencies[ticket_id_str]:
            self.dependencies[ticket_id_str].append(depends_on)

    def get_ticket_dependencies(self, ticket_id: int) -> list:
        """Get all tickets that ticket_id depends on."""
        return self.dependencies.get(str(ticket_id), [])

    def get_blocking_tickets(self, ticket_id: int) -> list:
        """Get all tickets blocked by ticket_id (tickets that depend on it)."""
        ticket_id_str = str(ticket_id)
        return [
            int(tid) for tid, deps in self.dependencies.items()
            if ticket_id in deps
        ]

    def get_dependency_chain(self, ticket_id: int) -> list:
        """Get full dependency chain for a ticket (including itself)."""
        chain = [ticket_id]
        current = ticket_id
        while True:
            deps = self.get_ticket_dependencies(current)
            if not deps:
                break
            current = deps[0]  # Follow first dependency
            chain.append(current)
        return chain

    def set_config(self, key: str, value) -> None:
        """Store a configuration value."""
        self.config[key] = value

    def get_config(self, key: str, default=None):
        """Retrieve a configuration value."""
        return self.config.get(key, default)

    def to_dict(self) -> dict:
        """Serialize knowledge base to dict for JSON storage."""
        return {
            "tickets": self.tickets,
            "agents": self.agents,
            "outcomes": self.outcomes,
            "dependencies": self.dependencies,
            "config": self.config
        }

    @classmethod
    def from_dict(cls, data: dict) -> "KnowledgeBase":
        """Deserialize knowledge base from dict."""
        kb = cls()
        kb.tickets = data.get("tickets", {})
        kb.agents = data.get("agents", {})
        kb.outcomes = data.get("outcomes", [])
        kb.dependencies = data.get("dependencies", {})
        kb.config = data.get("config", {})
        return kb


class KnowledgeBaseManager:
    """Manages persistence of the knowledge base with atomic writes."""

    def __init__(self, kb_file: str):
        self.kb_file = kb_file
        Path(kb_file).parent.mkdir(parents=True, exist_ok=True)

    def load(self) -> KnowledgeBase:
        """Load knowledge base from disk, returning default if missing."""
        if not Path(self.kb_file).exists():
            return KnowledgeBase()

        try:
            with open(self.kb_file) as f:
                data = json.load(f)
            return KnowledgeBase.from_dict(data)
        except (json.JSONDecodeError, IOError):
            return KnowledgeBase()

    def save(self, kb: KnowledgeBase) -> None:
        """Save knowledge base to disk using atomic rename."""
        temp_fd, temp_path = tempfile.mkstemp(dir=Path(self.kb_file).parent)
        try:
            with open(temp_fd, 'w') as f:
                json.dump(kb.to_dict(), f, indent=2)
            Path(temp_path).replace(self.kb_file)
        except Exception:
            Path(temp_path).unlink(missing_ok=True)
            raise
