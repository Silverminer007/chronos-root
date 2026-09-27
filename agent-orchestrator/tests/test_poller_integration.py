import pytest
import tempfile
import json
from unittest.mock import patch, MagicMock
from pathlib import Path

from poller import Poller
from state import State


@pytest.fixture
def temp_dirs():
    with tempfile.TemporaryDirectory() as state_dir, \
         tempfile.TemporaryDirectory() as log_dir, \
         tempfile.TemporaryDirectory() as worktree_dir:
        yield {
            'state': state_dir,
            'log': log_dir,
            'worktree': worktree_dir
        }


class TestPollerIntegration:
    def test_poller_initializes_with_all_components(self, temp_dirs):
        """Test that poller initializes with worktree manager and agent spawner"""
        poller = Poller(
            repo="owner/repo",
            repo_path="/path/to/repo",
            state_file=f"{temp_dirs['state']}/state.json",
            log_file=f"{temp_dirs['log']}/poller.log",
            worktree_base=temp_dirs['worktree']
        )

        assert poller is not None
        assert poller.worktree_mgr is not None
        assert poller.agent_spawner is not None
        assert poller.state is not None

    @patch('poller.GitHubAPI')
    def test_poller_discovers_tickets(self, mock_github_class, temp_dirs):
        """Test that poller discovers ready-for-agent tickets"""
        mock_github = MagicMock()
        mock_github.list_ready_for_agent.return_value = [
            {"number": 62, "title": "Test ticket", "body": "Test body", "labels": []}
        ]
        mock_github.add_label.return_value = True
        mock_github_class.return_value = mock_github

        poller = Poller(
            repo="owner/repo",
            repo_path="/path/to/repo",
            state_file=f"{temp_dirs['state']}/state.json",
            log_file=f"{temp_dirs['log']}/poller.log",
            worktree_base=temp_dirs['worktree']
        )

        with patch.object(poller.worktree_mgr, 'create_worktree') as mock_create_wt, \
             patch.object(poller.agent_spawner, 'spawn_agent') as mock_spawn:
            mock_create_wt.return_value = f"{temp_dirs['worktree']}/worktree-62"
            mock_spawn.return_value = 1234  # PID

            poller.run_once()

            # Verify state was updated
            assert len(poller.state.active_agents) > 0
            assert poller.state.active_agents[0].ticket_id == 62

    @patch('poller.GitHubAPI')
    def test_poller_spawns_agent_for_new_ticket(self, mock_github_class, temp_dirs):
        """Test that poller spawns agent for newly discovered ticket"""
        mock_github = MagicMock()
        mock_github.list_ready_for_agent.return_value = [
            {"number": 62, "title": "Test ticket", "body": "Test body", "labels": []}
        ]
        mock_github.add_label.return_value = True
        mock_github_class.return_value = mock_github

        poller = Poller(
            repo="owner/repo",
            repo_path="/path/to/repo",
            state_file=f"{temp_dirs['state']}/state.json",
            log_file=f"{temp_dirs['log']}/poller.log",
            worktree_base=temp_dirs['worktree']
        )

        with patch.object(poller.worktree_mgr, 'create_worktree') as mock_create_wt, \
             patch.object(poller.agent_spawner, 'spawn_agent') as mock_spawn:
            mock_create_wt.return_value = f"{temp_dirs['worktree']}/worktree-62"
            mock_spawn.return_value = 1234

            poller.run_once()

            # Verify worktree was created
            mock_create_wt.assert_called_once()
            # Verify agent was spawned
            mock_spawn.assert_called_once()

    @patch('poller.GitHubAPI')
    def test_poller_skips_already_claimed_ticket(self, mock_github_class, temp_dirs):
        """Test that poller skips tickets that already have active agents"""
        mock_github = MagicMock()
        mock_github.list_ready_for_agent.return_value = [
            {"number": 62, "title": "Test ticket", "body": "Test body", "labels": []}
        ]
        mock_github.add_label.return_value = True
        mock_github_class.return_value = mock_github

        poller = Poller(
            repo="owner/repo",
            repo_path="/path/to/repo",
            state_file=f"{temp_dirs['state']}/state.json",
            log_file=f"{temp_dirs['log']}/poller.log",
            worktree_base=temp_dirs['worktree']
        )

        # Pre-add an active agent to simulate one already running
        from state import AgentState
        agent = AgentState(
            ticket_id=62,
            agent_type="tdd",
            worktree_path=f"{temp_dirs['worktree']}/worktree-62",
            started_at="2026-09-27T12:00:00Z",
            pid=1234,
            status="running"
        )
        poller.state.add_active_agent(agent)

        with patch.object(poller.worktree_mgr, 'create_worktree') as mock_create_wt, \
             patch.object(poller.agent_spawner, 'spawn_agent') as mock_spawn, \
             patch.object(poller.agent_spawner, 'is_agent_running') as mock_is_running:
            mock_is_running.return_value = True

            poller.run_once()

            # Verify worktree was NOT created (agent already exists)
            mock_create_wt.assert_not_called()
            mock_spawn.assert_not_called()

    def test_poller_logs_activities(self, temp_dirs):
        """Test that poller logs all activities"""
        poller = Poller(
            repo="owner/repo",
            repo_path="/path/to/repo",
            state_file=f"{temp_dirs['state']}/state.json",
            log_file=f"{temp_dirs['log']}/poller.log",
            worktree_base=temp_dirs['worktree']
        )

        with patch('poller.GitHubAPI'):
            with patch.object(poller, 'run_once', wraps=poller.run_once):
                pass

        # Create a simple log message
        poller._log("Test message")

        # Verify log file exists and contains message
        log_path = Path(f"{temp_dirs['log']}/poller.log")
        assert log_path.exists()

        with open(log_path, 'r') as f:
            content = f.read()
            assert "Test message" in content
            assert "[" in content  # Timestamp
