import pytest
import tempfile
import json
import os
from pathlib import Path
from state import State, StateManager, AgentState, CompletedTicket


@pytest.fixture
def temp_state_file():
    with tempfile.NamedTemporaryFile(mode='w', delete=False, suffix='.json') as f:
        yield f.name
    if os.path.exists(f.name):
        os.unlink(f.name)


class TestState:
    """Tests for the new agent-aware State class."""

    def test_state_creates_default_on_missing_file(self, temp_state_file):
        """State creates default state when file doesn't exist."""
        os.unlink(temp_state_file)

        state = State(temp_state_file)

        assert state.last_poll is None
        assert state.active_agents == []
        assert state.completed_tickets == []

    def test_state_loads_existing_file(self, temp_state_file):
        """State loads and parses existing state file."""
        state_data = {
            "last_poll": "2026-09-27T12:00:00Z",
            "active_agents": [
                {
                    "ticket_id": 62,
                    "agent_type": "tdd",
                    "worktree_path": "/path",
                    "started_at": "2026-09-27T11:00:00Z",
                    "status": "running",
                    "pid": 1234,
                    "ci_poll_rounds": 0,
                    "pr_number": None
                }
            ],
            "completed_tickets": []
        }

        with open(temp_state_file, 'w') as f:
            json.dump(state_data, f)

        state = State(temp_state_file)

        assert state.last_poll == "2026-09-27T12:00:00Z"
        assert len(state.active_agents) == 1
        assert state.active_agents[0].ticket_id == 62

    def test_state_saves_atomically(self, temp_state_file):
        """State saves with atomic writes (temp file then rename)."""
        state = State(temp_state_file)

        agent = AgentState(
            ticket_id=62,
            agent_type="tdd",
            worktree_path="/path",
            started_at="2026-09-27T11:00:00Z",
            pid=1234
        )
        state.add_active_agent(agent)

        with open(temp_state_file, 'r') as f:
            saved_data = json.load(f)

        assert saved_data["active_agents"][0]["ticket_id"] == 62

    def test_state_handles_corrupted_file(self, temp_state_file):
        """State resets to default on corrupted JSON."""
        with open(temp_state_file, 'w') as f:
            f.write("{invalid json")

        state = State(temp_state_file)

        assert state.active_agents == []
        assert state.completed_tickets == []

    def test_add_active_agent(self, temp_state_file):
        """Adding agent persists to file."""
        state = State(temp_state_file)

        agent = AgentState(
            ticket_id=62,
            agent_type="tdd",
            worktree_path="/path",
            started_at="2026-09-27T11:00:00Z",
            pid=1234,
            status="running"
        )
        state.add_active_agent(agent)

        state2 = State(temp_state_file)
        assert len(state2.active_agents) == 1
        assert state2.active_agents[0].ticket_id == 62

    def test_remove_active_agent(self, temp_state_file):
        """Removing agent removes from state and saves."""
        state = State(temp_state_file)

        agent = AgentState(
            ticket_id=62,
            agent_type="tdd",
            worktree_path="/path",
            started_at="2026-09-27T11:00:00Z",
            pid=1234
        )
        state.add_active_agent(agent)

        removed = state.remove_active_agent(62)
        assert removed is True
        assert len(state.active_agents) == 0

    def test_get_active_agent(self, temp_state_file):
        """Getting agent by ticket ID."""
        state = State(temp_state_file)

        agent = AgentState(
            ticket_id=62,
            agent_type="tdd",
            worktree_path="/path",
            started_at="2026-09-27T11:00:00Z",
            pid=1234
        )
        state.add_active_agent(agent)

        found = state.get_active_agent(62)
        assert found is not None
        assert found.ticket_id == 62


class TestStateManager:
    """Tests for backward-compatibility StateManager."""

    def test_statemanager_creates_default_state(self, temp_state_file):
        """StateManager creates default state on missing file."""
        os.unlink(temp_state_file)

        mgr = StateManager(temp_state_file)
        state = mgr.load()

        assert "last_poll" in state
        assert "active_agents" in state
        assert "completed_tickets" in state

    def test_statemanager_loads_existing_state(self, temp_state_file):
        """StateManager loads and parses existing state."""
        initial_state = {
            "last_poll": "2026-09-27T12:00:00Z",
            "active_agents": [],
            "completed_tickets": []
        }

        with open(temp_state_file, 'w') as f:
            json.dump(initial_state, f)

        mgr = StateManager(temp_state_file)
        state = mgr.load()

        assert state["last_poll"] == "2026-09-27T12:00:00Z"

    def test_statemanager_saves_atomically(self, temp_state_file):
        """StateManager saves with atomic writes."""
        mgr = StateManager(temp_state_file)

        state = {"last_poll": "2026-09-27T13:00:00Z", "active_agents": [], "completed_tickets": []}
        mgr.save(state)

        with open(temp_state_file, 'r') as f:
            saved = json.load(f)

        assert saved["last_poll"] == "2026-09-27T13:00:00Z"
