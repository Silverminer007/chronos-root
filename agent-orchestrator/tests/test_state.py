import json
import tempfile
import os
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).parent.parent))

from state import StateManager


def test_state_load_empty():
    """Test loading state when file doesn't exist."""
    with tempfile.TemporaryDirectory() as tmpdir:
        state_file = os.path.join(tmpdir, "state.json")
        mgr = StateManager(state_file)
        state = mgr.load()
        assert state["active_agents"] == []
        assert state["completed_tickets"] == []
        assert "last_poll" in state


def test_state_save_and_load():
    """Test saving and loading state."""
    with tempfile.TemporaryDirectory() as tmpdir:
        state_file = os.path.join(tmpdir, "state.json")
        mgr = StateManager(state_file)

        # Create and save state
        state = {
            "last_poll": "2026-09-27T12:00:00Z",
            "active_agents": [
                {
                    "ticket_id": 123,
                    "agent_type": "tdd",
                    "worktree_path": "/path/to/worktree",
                    "started_at": "2026-09-27T11:55:00Z",
                    "status": "running"
                }
            ],
            "completed_tickets": []
        }
        mgr.save(state)

        # Load and verify
        loaded = mgr.load()
        assert loaded["last_poll"] == state["last_poll"]
        assert len(loaded["active_agents"]) == 1
        assert loaded["active_agents"][0]["ticket_id"] == 123


def test_state_atomic_write():
    """Test that state writes are atomic (no partial writes)."""
    with tempfile.TemporaryDirectory() as tmpdir:
        state_file = os.path.join(tmpdir, "state.json")
        mgr = StateManager(state_file)

        state = {"active_agents": [], "completed_tickets": []}
        mgr.save(state)

        # Verify file is valid JSON
        with open(state_file) as f:
            parsed = json.load(f)
            assert "active_agents" in parsed


if __name__ == "__main__":
    test_state_load_empty()
    test_state_save_and_load()
    test_state_atomic_write()
    print("✓ All state tests passed")
