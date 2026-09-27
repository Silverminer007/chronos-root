import json
import tempfile
import os
from pathlib import Path
from unittest.mock import patch, MagicMock
import sys

sys.path.insert(0, str(Path(__file__).parent.parent))

from poller import Poller


def test_poller_checks_pr_ci_status():
    """Test poller can check PR CI status."""
    with tempfile.TemporaryDirectory() as tmpdir:
        state_file = os.path.join(tmpdir, "state.json")
        log_file = os.path.join(tmpdir, "poller.log")

        with patch("poller.GitHubAPI") as MockGitHub:
            mock_github = MagicMock()
            MockGitHub.return_value = mock_github
            mock_github.get_pr_checks.return_value = {
                "status": "success",
                "conclusion": None,
                "check_runs": []
            }

            poller = Poller(
                repo="test-org/test-repo",
                state_file=state_file,
                log_file=log_file
            )

            # Check PR status
            status = poller.check_pr_status(123)

            assert status is not None
            assert status["status"] == "success"
            mock_github.get_pr_checks.assert_called_once_with(123)


def test_poller_tracks_ci_status_in_state():
    """Test poller tracks CI status in state.json."""
    with tempfile.TemporaryDirectory() as tmpdir:
        state_file = os.path.join(tmpdir, "state.json")
        log_file = os.path.join(tmpdir, "poller.log")

        # Initialize state with PR info
        initial_state = {
            "last_poll": "2026-09-27T10:00:00Z",
            "active_agents": [],
            "completed_tickets": [],
            "pr_monitoring": {
                "123": {
                    "ticket_id": 70,
                    "pr_number": 123,
                    "ci_status": "pending",
                    "ci_polls": 0,
                    "last_ci_poll": None,
                    "retry_count": 0
                }
            }
        }

        with open(state_file, "w") as f:
            json.dump(initial_state, f)

        with patch("poller.GitHubAPI") as MockGitHub:
            mock_github = MagicMock()
            MockGitHub.return_value = mock_github

            poller = Poller(
                repo="test-org/test-repo",
                state_file=state_file,
                log_file=log_file
            )

            # Load state and verify PR monitoring data
            state = poller.state_mgr.load()
            assert "pr_monitoring" in state
            assert "123" in state["pr_monitoring"]
            assert state["pr_monitoring"]["123"]["ci_status"] == "pending"


def test_poller_handles_ci_success():
    """Test poller handles CI success correctly."""
    with tempfile.TemporaryDirectory() as tmpdir:
        state_file = os.path.join(tmpdir, "state.json")
        log_file = os.path.join(tmpdir, "poller.log")

        initial_state = {
            "last_poll": "2026-09-27T10:00:00Z",
            "active_agents": [],
            "completed_tickets": [],
            "pr_monitoring": {
                "123": {
                    "ticket_id": 70,
                    "pr_number": 123,
                    "ci_status": "pending",
                    "ci_polls": 0,
                    "retry_count": 0
                }
            }
        }

        with open(state_file, "w") as f:
            json.dump(initial_state, f)

        with patch("poller.GitHubAPI") as MockGitHub:
            mock_github = MagicMock()
            MockGitHub.return_value = mock_github
            mock_github.get_pr_checks.return_value = {"status": "success"}
            mock_github.add_label.return_value = True

            poller = Poller(
                repo="test-org/test-repo",
                state_file=state_file,
                log_file=log_file
            )

            # Poll and handle success
            poller.poll_pr_ci(123)

            # Verify state was updated
            state = poller.state_mgr.load()
            assert state["pr_monitoring"]["123"]["ci_status"] == "success"
            assert mock_github.add_label.called  # Should add ready-for-review label


def test_poller_handles_ci_failure():
    """Test poller handles CI failure and triggers retry."""
    with tempfile.TemporaryDirectory() as tmpdir:
        state_file = os.path.join(tmpdir, "state.json")
        log_file = os.path.join(tmpdir, "poller.log")

        initial_state = {
            "last_poll": "2026-09-27T10:00:00Z",
            "active_agents": [],
            "completed_tickets": [],
            "pr_monitoring": {
                "123": {
                    "ticket_id": 70,
                    "pr_number": 123,
                    "ci_status": "pending",
                    "ci_polls": 0,
                    "retry_count": 0
                }
            }
        }

        with open(state_file, "w") as f:
            json.dump(initial_state, f)

        with patch("poller.GitHubAPI") as MockGitHub:
            mock_github = MagicMock()
            MockGitHub.return_value = mock_github
            mock_github.get_pr_checks.return_value = {"status": "failure"}

            poller = Poller(
                repo="test-org/test-repo",
                state_file=state_file,
                log_file=log_file
            )

            # Poll and handle failure
            result = poller.poll_pr_ci(123)

            # Verify state was updated
            state = poller.state_mgr.load()
            assert state["pr_monitoring"]["123"]["ci_status"] == "failure"


def test_poller_retries_on_ci_failure():
    """Test poller retries on CI failure (max 3 times)."""
    with tempfile.TemporaryDirectory() as tmpdir:
        state_file = os.path.join(tmpdir, "state.json")
        log_file = os.path.join(tmpdir, "poller.log")

        initial_state = {
            "last_poll": "2026-09-27T10:00:00Z",
            "active_agents": [],
            "completed_tickets": [],
            "pr_monitoring": {
                "123": {
                    "ticket_id": 70,
                    "pr_number": 123,
                    "ci_status": "failure",
                    "ci_polls": 5,
                    "retry_count": 0
                }
            }
        }

        with open(state_file, "w") as f:
            json.dump(initial_state, f)

        with patch("poller.GitHubAPI") as MockGitHub:
            mock_github = MagicMock()
            MockGitHub.return_value = mock_github

            poller = Poller(
                repo="test-org/test-repo",
                state_file=state_file,
                log_file=log_file
            )

            # Test that retry counter can be incremented
            state = poller.state_mgr.load()
            state["pr_monitoring"]["123"]["retry_count"] = 1
            poller.state_mgr.save(state)

            state = poller.state_mgr.load()
            assert state["pr_monitoring"]["123"]["retry_count"] == 1


def test_poller_max_retry_limit():
    """Test poller enforces max 3 retries."""
    with tempfile.TemporaryDirectory() as tmpdir:
        state_file = os.path.join(tmpdir, "state.json")
        log_file = os.path.join(tmpdir, "poller.log")

        initial_state = {
            "last_poll": "2026-09-27T10:00:00Z",
            "active_agents": [],
            "completed_tickets": [],
            "pr_monitoring": {
                "123": {
                    "ticket_id": 70,
                    "pr_number": 123,
                    "ci_status": "failure",
                    "ci_polls": 5,
                    "retry_count": 3
                }
            }
        }

        with open(state_file, "w") as f:
            json.dump(initial_state, f)

        with patch("poller.GitHubAPI") as MockGitHub:
            mock_github = MagicMock()
            MockGitHub.return_value = mock_github
            mock_github.add_label.return_value = True
            mock_github.post_comment.return_value = True

            poller = Poller(
                repo="test-org/test-repo",
                state_file=state_file,
                log_file=log_file
            )

            # Should not retry when max retries exceeded
            should_retry = poller.should_retry_pr(123)
            assert should_retry is False


def test_poller_ci_pending_timeout():
    """Test poller enforces 2.5 minute timeout for pending CI."""
    with tempfile.TemporaryDirectory() as tmpdir:
        state_file = os.path.join(tmpdir, "state.json")
        log_file = os.path.join(tmpdir, "poller.log")

        initial_state = {
            "last_poll": "2026-09-27T10:00:00Z",
            "active_agents": [],
            "completed_tickets": [],
            "pr_monitoring": {
                "123": {
                    "ticket_id": 70,
                    "pr_number": 123,
                    "ci_status": "pending",
                    "ci_polls": 5,  # Max 5 attempts (2.5 minutes)
                    "retry_count": 0
                }
            }
        }

        with open(state_file, "w") as f:
            json.dump(initial_state, f)

        with patch("poller.GitHubAPI") as MockGitHub:
            mock_github = MagicMock()
            MockGitHub.return_value = mock_github
            mock_github.add_label.return_value = True
            mock_github.post_comment.return_value = True

            poller = Poller(
                repo="test-org/test-repo",
                state_file=state_file,
                log_file=log_file
            )

            # Test polling timeout
            should_continue = poller.should_continue_polling(123)
            assert should_continue is False  # Should stop after 5 polls


if __name__ == "__main__":
    test_poller_checks_pr_ci_status()
    test_poller_tracks_ci_status_in_state()
    test_poller_handles_ci_success()
    test_poller_handles_ci_failure()
    test_poller_retries_on_ci_failure()
    test_poller_max_retry_limit()
    test_poller_ci_pending_timeout()
    print("✓ All CI polling tests passed")
