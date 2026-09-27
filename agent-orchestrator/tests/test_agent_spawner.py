import pytest
import tempfile
from unittest.mock import patch, MagicMock
from agent_spawner import AgentSpawner, AgentType


@pytest.fixture
def agent_spawner():
    return AgentSpawner(
        repo_path="/path/to/repo",
        claude_code_path="/usr/local/bin/claude"
    )


class TestAgentSpawner:
    def test_spawn_tdd_agent(self, agent_spawner):
        """Test spawning a TDD agent"""
        with patch('agent_spawner.subprocess.Popen') as mock_popen:
            mock_popen.return_value = MagicMock(pid=1234)

            agent_pid = agent_spawner.spawn_agent(
                ticket_id=62,
                agent_type=AgentType.TDD,
                worktree_path="/path/to/worktree",
                branch_name="feature/62-agent-spawning"
            )

            assert agent_pid == 1234
            mock_popen.assert_called_once()

    def test_spawn_code_review_agent(self, agent_spawner):
        """Test spawning a code review agent"""
        with patch('agent_spawner.subprocess.Popen') as mock_popen:
            mock_popen.return_value = MagicMock(pid=5678)

            agent_pid = agent_spawner.spawn_agent(
                ticket_id=62,
                agent_type=AgentType.CODE_REVIEW,
                worktree_path="/path/to/worktree",
                branch_name="feature/62-agent-spawning"
            )

            assert agent_pid == 5678

    def test_spawn_spec_validator_agent(self, agent_spawner):
        """Test spawning a spec validator agent"""
        with patch('agent_spawner.subprocess.Popen') as mock_popen:
            mock_popen.return_value = MagicMock(pid=9012)

            agent_pid = agent_spawner.spawn_agent(
                ticket_id=62,
                agent_type=AgentType.SPEC_VALIDATOR,
                worktree_path="/path/to/worktree",
                branch_name="feature/62-agent-spawning"
            )

            assert agent_pid == 9012

    def test_get_agent_status(self, agent_spawner):
        """Test checking agent status"""
        with patch('agent_spawner.os.kill') as mock_kill:
            # Process exists - os.kill returns without error
            mock_kill.return_value = None

            is_running = agent_spawner.is_agent_running(1234)
            assert is_running is True

    def test_agent_not_running(self, agent_spawner):
        """Test detecting when agent is not running"""
        with patch('agent_spawner.os.kill') as mock_kill:
            # Process doesn't exist - os.kill raises ProcessLookupError
            mock_kill.side_effect = ProcessLookupError()

            is_running = agent_spawner.is_agent_running(1234)
            assert is_running is False

    def test_terminate_agent(self, agent_spawner):
        """Test terminating an agent"""
        with patch('agent_spawner.os.kill') as mock_kill:
            mock_kill.return_value = None

            success = agent_spawner.terminate_agent(1234)
            assert success is True

    def test_terminate_nonexistent_agent(self, agent_spawner):
        """Test terminating a non-existent agent"""
        with patch('agent_spawner.os.kill') as mock_kill:
            mock_kill.side_effect = ProcessLookupError()

            success = agent_spawner.terminate_agent(1234)
            assert success is False
