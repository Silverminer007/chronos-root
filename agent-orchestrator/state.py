import json
import tempfile
from datetime import datetime
from pathlib import Path


class StateManager:
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
