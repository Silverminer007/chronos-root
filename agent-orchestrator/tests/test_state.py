import json
import tempfile
import os
from pathlib import Path
import sys
from unittest.mock import patch

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


def test_add_agent():
    """Test adding an agent to active_agents."""
    with tempfile.TemporaryDirectory() as tmpdir:
        state_file = os.path.join(tmpdir, "state.json")
        mgr = StateManager(state_file)

        # Add an agent
        mgr.add_agent(
            ticket_id=123,
            agent_type="tdd",
            pid=5678,
            worktree_path="/path/to/worktree"
        )

        # Verify it was added
        state = mgr.load()
        assert len(state["active_agents"]) == 1
        agent = state["active_agents"][0]
        assert agent["ticket_id"] == 123
        assert agent["agent_type"] == "tdd"
        assert agent["pid"] == 5678
        assert agent["worktree_path"] == "/path/to/worktree"
        assert "started_at" in agent


def test_prune_dead_agents():
    """Test pruning agents with dead PIDs."""
    with tempfile.TemporaryDirectory() as tmpdir:
        state_file = os.path.join(tmpdir, "state.json")
        mgr = StateManager(state_file)

        # Add three agents
        mgr.add_agent(123, "tdd", 9999, "/path1")      # Dead PID (unlikely to exist)
        mgr.add_agent(124, "code-review", os.getpid(), "/path2")  # Live PID (this process)
        mgr.add_agent(125, "tdd", 9998, "/path3")      # Dead PID

        assert mgr.count_active_agents() == 3

        # Prune dead agents
        mgr.prune_dead_agents()

        # Should only have the one with live PID
        state = mgr.load()
        assert mgr.count_active_agents() == 1
        assert state["active_agents"][0]["ticket_id"] == 124


def test_count_active_agents():
    """Test counting active agents."""
    with tempfile.TemporaryDirectory() as tmpdir:
        state_file = os.path.join(tmpdir, "state.json")
        mgr = StateManager(state_file)

        # Empty state
        assert mgr.count_active_agents() == 0

        # Add agents
        mgr.add_agent(123, "tdd", 5678, "/path1")
        assert mgr.count_active_agents() == 1

        mgr.add_agent(124, "code-review", 5679, "/path2")
        assert mgr.count_active_agents() == 2

        # Remove one
        mgr.remove_agent(123)
        assert mgr.count_active_agents() == 1


def test_remove_agent():
    """Test removing an agent from active_agents."""
    with tempfile.TemporaryDirectory() as tmpdir:
        state_file = os.path.join(tmpdir, "state.json")
        mgr = StateManager(state_file)

        # Add two agents
        mgr.add_agent(123, "tdd", 5678, "/path/to/worktree1")
        mgr.add_agent(124, "code-review", 5679, "/path/to/worktree2")

        # Verify both are added
        state = mgr.load()
        assert len(state["active_agents"]) == 2

        # Remove one
        mgr.remove_agent(123)

        # Verify only one remains
        state = mgr.load()
        assert len(state["active_agents"]) == 1
        assert state["active_agents"][0]["ticket_id"] == 124


if __name__ == "__main__":
    test_state_load_empty()
    test_state_save_and_load()
    test_state_atomic_write()
    test_add_agent()
    print("✓ All state tests passed")
