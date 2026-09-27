import json
import tempfile
import os
from pathlib import Path
from unittest.mock import patch, MagicMock
import sys

sys.path.insert(0, str(Path(__file__).parent.parent))

from poller import Poller


def test_poller_discovers_ready_for_agent():
    """Test poller discovers issues labeled ready-for-agent."""
    with tempfile.TemporaryDirectory() as tmpdir:
        state_file = os.path.join(tmpdir, "state.json")

        mock_issues = [
            {"number": 123, "title": "Feature A"},
            {"number": 124, "title": "Feature B"}
        ]

        with patch("poller.GitHubAPI") as MockGitHub:
            mock_github = MagicMock()
            MockGitHub.return_value = mock_github
            mock_github.list_ready_for_agent.return_value = mock_issues
            mock_github.add_label.return_value = True

            poller = Poller(
                repo="test-org/test-repo",
                state_file=state_file,
                log_file=os.path.join(tmpdir, "poller.log")
            )

            # Run one poll cycle
            tickets = poller._discover_ready_for_agent()

            assert len(tickets) == 2
            assert tickets[0]["number"] == 123
            assert tickets[1]["number"] == 124


def test_poller_applies_in_progress_label():
    """Test poller applies in-progress label when discovering ticket."""
    with tempfile.TemporaryDirectory() as tmpdir:
        state_file = os.path.join(tmpdir, "state.json")
        log_file = os.path.join(tmpdir, "poller.log")

        mock_issues = [{"number": 123, "title": "Feature A"}]

        with patch("poller.GitHubAPI") as MockGitHub:
            mock_github = MagicMock()
            MockGitHub.return_value = mock_github
            mock_github.list_ready_for_agent.return_value = mock_issues
            mock_github.add_label.return_value = True

            poller = Poller(
                repo="test-org/test-repo",
                state_file=state_file,
                log_file=log_file
            )

            tickets = poller._discover_ready_for_agent()

            # Verify add_label was called with in-progress
            calls = [call[0] for call in mock_github.add_label.call_args_list]
            assert any(123 in call for call in calls)


def test_poller_logs_activity():
    """Test poller logs to file."""
    with tempfile.TemporaryDirectory() as tmpdir:
        state_file = os.path.join(tmpdir, "state.json")
        log_file = os.path.join(tmpdir, "poller.log")

        with patch("poller.GitHubAPI"):
            poller = Poller(
                repo="test-org/test-repo",
                state_file=state_file,
                log_file=log_file
            )
            poller._log("Test log message")

            # Verify log file exists and contains message
            assert Path(log_file).exists()
            with open(log_file) as f:
                content = f.read()
                assert "Test log message" in content
                assert "2026-" in content or "202" in content  # Check for timestamp


def test_poller_can_spawn_agent_under_limit():
    """Test poller allows spawning when under 3-agent limit."""
    with tempfile.TemporaryDirectory() as tmpdir:
        state_file = os.path.join(tmpdir, "state.json")

        with patch("poller.GitHubAPI"):
            poller = Poller(
                repo="test-org/test-repo",
                state_file=state_file,
                log_file=os.path.join(tmpdir, "poller.log")
            )

            # Empty state - should allow spawn
            assert poller.can_spawn_agent() is True

            # Add 2 agents with current process PID (alive) - should still allow spawn
            current_pid = os.getpid()
            poller.register_agent(123, "tdd", current_pid, "/path1")
            poller.register_agent(124, "code-review", current_pid, "/path2")
            assert poller.can_spawn_agent() is True


def test_poller_cannot_spawn_agent_at_limit():
    """Test poller rejects spawning when at 3-agent limit."""
    with tempfile.TemporaryDirectory() as tmpdir:
        state_file = os.path.join(tmpdir, "state.json")

        with patch("poller.GitHubAPI"):
            poller = Poller(
                repo="test-org/test-repo",
                state_file=state_file,
                log_file=os.path.join(tmpdir, "poller.log")
            )

            # Add 3 agents with current process PID (alive) - should NOT allow spawn
            current_pid = os.getpid()
            poller.register_agent(123, "tdd", current_pid, "/path1")
            poller.register_agent(124, "code-review", current_pid, "/path2")
            poller.register_agent(125, "tdd", current_pid, "/path3")
            assert poller.can_spawn_agent() is False


if __name__ == "__main__":
    test_poller_discovers_ready_for_agent()
    test_poller_applies_in_progress_label()
    test_poller_logs_activity()
    print("✓ All poller tests passed")
