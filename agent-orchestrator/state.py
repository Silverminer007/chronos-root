import fcntl
import json
import os
import signal
import tempfile
from datetime import datetime
from pathlib import Path


class StateManager:
    def __init__(self, state_file: str):
        self.state_file = state_file
        self.lock_file = state_file + ".lock"
        Path(state_file).parent.mkdir(parents=True, exist_ok=True)

    def _with_lock(self, operation):
        """Execute operation with file locking."""
        with open(self.lock_file, 'w') as lock_f:
            fcntl.flock(lock_f.fileno(), fcntl.LOCK_EX)
            try:
                return operation()
            finally:
                fcntl.flock(lock_f.fileno(), fcntl.LOCK_UN)

    def load(self) -> dict:
        if not Path(self.state_file).exists():
            return self._default_state()
        try:
            with open(self.state_file) as f:
                return json.load(f)
        except (json.JSONDecodeError, IOError):
            return self._default_state()

    def _save_unlocked(self, state: dict) -> None:
        """Internal save without locking (call within _with_lock)."""
        temp_fd, temp_path = tempfile.mkstemp(dir=Path(self.state_file).parent)
        try:
            with open(temp_fd, 'w') as f:
                json.dump(state, f, indent=2)
            Path(temp_path).replace(self.state_file)
        except Exception:
            Path(temp_path).unlink(missing_ok=True)
            raise

    def save(self, state: dict) -> None:
        """Save state with file locking."""
        self._with_lock(lambda: self._save_unlocked(state))

    def add_agent(self, ticket_id: int, agent_type: str, pid: int, worktree_path: str) -> None:
        """Register an active agent."""
        def _do_add():
            state = self.load()
            state["active_agents"].append({
                "ticket_id": ticket_id,
                "agent_type": agent_type,
                "pid": pid,
                "started_at": datetime.utcnow().isoformat() + "Z",
                "worktree_path": worktree_path
            })
            self._save_unlocked(state)
        self._with_lock(_do_add)

    def remove_agent(self, ticket_id: int) -> None:
        """Unregister an active agent by ticket_id."""
        def _do_remove():
            state = self.load()
            state["active_agents"] = [
                agent for agent in state["active_agents"]
                if agent["ticket_id"] != ticket_id
            ]
            self._save_unlocked(state)
        self._with_lock(_do_remove)

    def count_active_agents(self) -> int:
        """Count the number of active agents."""
        state = self.load()
        return len(state["active_agents"])

    def prune_dead_agents(self) -> None:
        """Remove agents whose PIDs no longer exist."""
        def is_process_alive(pid: int) -> bool:
            try:
                os.kill(pid, 0)
                return True
            except (OSError, ProcessLookupError):
                return False

        def _do_prune():
            state = self.load()
            state["active_agents"] = [
                agent for agent in state["active_agents"]
                if is_process_alive(agent["pid"])
            ]
            self._save_unlocked(state)
        self._with_lock(_do_prune)

    @staticmethod
    def _default_state() -> dict:
        return {
            "last_poll": datetime.utcnow().isoformat() + "Z",
            "active_agents": [],
            "completed_tickets": []
        }
