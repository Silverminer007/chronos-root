import json
import tempfile
import os
from pathlib import Path
from datetime import datetime, timedelta
from unittest.mock import patch, MagicMock
import sys

sys.path.insert(0, str(Path(__file__).parent.parent))

from error_recovery import ErrorRecoveryManager, AgentTimeout, AgentCrash, StateCorruption


def test_detect_agent_timeout():
    """Test detection of agent timeout (> 6 hours)."""
    with tempfile.TemporaryDirectory() as tmpdir:
        state_file = os.path.join(tmpdir, "state.json")

        # Create state with agent running for > 6 hours
        six_hours_ago = (datetime.utcnow() - timedelta(hours=7)).isoformat() + "Z"
        state = {
            "last_poll": datetime.utcnow().isoformat() + "Z",
            "active_agents": [
                {
                    "ticket_id": 123,
                    "agent_type": "tdd",
                    "worktree_path": "/path/to/worktree",
                    "started_at": six_hours_ago,
                    "status": "running",
                    "pid": 1234
                }
            ],
            "completed_tickets": []
        }

        with open(state_file, 'w') as f:
            json.dump(state, f)

        recovery = ErrorRecoveryManager(state_file, log_file=os.path.join(tmpdir, "recovery.log"))
        timed_out = recovery.detect_timeouts()

        assert len(timed_out) == 1
        assert timed_out[0]["ticket_id"] == 123


def test_detect_agent_crash():
    """Test detection of agent crash (PID doesn't exist)."""
    with tempfile.TemporaryDirectory() as tmpdir:
        state_file = os.path.join(tmpdir, "state.json")

        # Create state with non-existent PID
        state = {
            "last_poll": datetime.utcnow().isoformat() + "Z",
            "active_agents": [
                {
                    "ticket_id": 124,
                    "agent_type": "code-review",
                    "worktree_path": "/path/to/worktree",
                    "started_at": datetime.utcnow().isoformat() + "Z",
                    "status": "running",
                    "pid": 999999  # Non-existent PID
                }
            ],
            "completed_tickets": []
        }

        with open(state_file, 'w') as f:
            json.dump(state, f)

        recovery = ErrorRecoveryManager(state_file, log_file=os.path.join(tmpdir, "recovery.log"))
        crashed = recovery.detect_crashes()

        assert len(crashed) == 1
        assert crashed[0]["ticket_id"] == 124


def test_handle_agent_timeout():
    """Test handling of timed-out agent."""
    with tempfile.TemporaryDirectory() as tmpdir:
        state_file = os.path.join(tmpdir, "state.json")
        log_file = os.path.join(tmpdir, "recovery.log")

        six_hours_ago = (datetime.utcnow() - timedelta(hours=7)).isoformat() + "Z"
        state = {
            "last_poll": datetime.utcnow().isoformat() + "Z",
            "active_agents": [
                {
                    "ticket_id": 125,
                    "agent_type": "tdd",
                    "worktree_path": "/path/to/worktree",
                    "started_at": six_hours_ago,
                    "status": "running",
                    "pid": 1234
                }
            ],
            "completed_tickets": []
        }

        with open(state_file, 'w') as f:
            json.dump(state, f)

        with patch("error_recovery.os.kill") as mock_kill:
            with patch("error_recovery.GitHubAPI") as MockGitHub:
                mock_github = MagicMock()
                MockGitHub.return_value = mock_github
                mock_github.add_label.return_value = True

                recovery = ErrorRecoveryManager(state_file, log_file=log_file)
                updated_state = recovery.handle_timeout({
                    "ticket_id": 125,
                    "agent_type": "tdd",
                    "pid": 1234,
                    "started_at": six_hours_ago
                })

                # Verify SIGTERM was attempted
                mock_kill.assert_called()

                # Verify agent was removed from state
                assert len(updated_state["active_agents"]) == 0


def test_recover_corrupted_state():
    """Test recovery from corrupted state.json."""
    with tempfile.TemporaryDirectory() as tmpdir:
        state_file = os.path.join(tmpdir, "state.json")
        log_file = os.path.join(tmpdir, "recovery.log")

        # Create corrupted JSON file
        with open(state_file, 'w') as f:
            f.write("{invalid json}")

        recovery = ErrorRecoveryManager(state_file, log_file=log_file)
        recovered = recovery.recover_corrupted_state()

        # Verify backup was created
        backup_file = state_file + ".backup"
        assert Path(backup_file).exists()

        # Verify recovered state is valid
        assert "active_agents" in recovered
        assert "completed_tickets" in recovered


def test_find_orphaned_worktrees():
    """Test finding orphaned worktrees."""
    with tempfile.TemporaryDirectory() as tmpdir:
        worktrees_dir = os.path.join(tmpdir, ".worktrees")
        os.makedirs(worktrees_dir, exist_ok=True)

        # Create some orphaned worktree directories
        orphaned1 = os.path.join(worktrees_dir, "worktree-old-123")
        orphaned2 = os.path.join(worktrees_dir, "worktree-old-456")
        os.makedirs(orphaned1)
        os.makedirs(orphaned2)

        # Create state file with no active agents
        state_file = os.path.join(tmpdir, "state.json")
        state = {
            "active_agents": [],
            "completed_tickets": []
        }
        with open(state_file, 'w') as f:
            json.dump(state, f)

        recovery = ErrorRecoveryManager(state_file, log_file=os.path.join(tmpdir, "recovery.log"))
        orphaned = recovery.find_orphaned_worktrees(worktrees_dir, state)

        assert len(orphaned) >= 2


def test_graceful_shutdown_waits_for_agents():
    """Test graceful shutdown waits for active agents."""
    with tempfile.TemporaryDirectory() as tmpdir:
        state_file = os.path.join(tmpdir, "state.json")

        state = {
            "last_poll": datetime.utcnow().isoformat() + "Z",
            "active_agents": [
                {
                    "ticket_id": 126,
                    "agent_type": "tdd",
                    "started_at": datetime.utcnow().isoformat() + "Z",
                    "status": "running",
                    "pid": 1234
                }
            ],
            "completed_tickets": []
        }

        with open(state_file, 'w') as f:
            json.dump(state, f)

        with patch("error_recovery.os.kill"):
            recovery = ErrorRecoveryManager(state_file, log_file=os.path.join(tmpdir, "recovery.log"))

            # Should mark agent as terminated
            recovery.handle_graceful_shutdown(timeout_seconds=1)

            updated_state = recovery.load_state()
            # Verify agents were handled
            assert updated_state is not None


def test_retry_logic_tdd_agent():
    """Test retry logic for TDD agent (max 1 retry)."""
    recovery = ErrorRecoveryManager("dummy.json", log_file="/dev/null")

    can_retry = recovery.should_retry_agent("tdd", crash_count=1)
    assert can_retry is True

    can_retry = recovery.should_retry_agent("tdd", crash_count=2)
    assert can_retry is False


def test_retry_logic_fixer_agent():
    """Test retry logic for fixer agent (max 3 retries)."""
    recovery = ErrorRecoveryManager("dummy.json", log_file="/dev/null")

    for i in range(1, 4):
        can_retry = recovery.should_retry_agent("fixer", crash_count=i)
        assert can_retry is True

    can_retry = recovery.should_retry_agent("fixer", crash_count=4)
    assert can_retry is False


def test_structured_logging():
    """Test structured error logging."""
    with tempfile.TemporaryDirectory() as tmpdir:
        log_file = os.path.join(tmpdir, "recovery.log")

        recovery = ErrorRecoveryManager("dummy.json", log_file=log_file)
        recovery.log_error(
            ticket_id=127,
            agent_type="tdd",
            level="ERROR",
            message="Agent timeout detected",
            details={"timeout_hours": 6}
        )

        # Verify log file contains structured entry
        with open(log_file) as f:
            content = f.read()
            assert "127" in content
            assert "ERROR" in content
            assert "Agent timeout detected" in content


if __name__ == "__main__":
    test_detect_agent_timeout()
    test_detect_agent_crash()
    test_handle_agent_timeout()
    test_recover_corrupted_state()
    test_find_orphaned_worktrees()
    test_graceful_shutdown_waits_for_agents()
    test_retry_logic_tdd_agent()
    test_retry_logic_fixer_agent()
    test_structured_logging()
    print("✓ All error recovery tests passed")
