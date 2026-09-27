import pytest
import tempfile
import os
from pathlib import Path
from unittest.mock import patch, MagicMock
from worktree_manager import WorktreeManager


@pytest.fixture
def temp_dir():
    with tempfile.TemporaryDirectory() as tmpdir:
        yield tmpdir


@pytest.fixture
def mock_run():
    with patch('worktree_manager.subprocess.run') as mock:
        yield mock


class TestWorktreeManager:
    def test_create_worktree_success(self, temp_dir, mock_run):
        """Test creating a new worktree successfully"""
        manager = WorktreeManager(base_path=temp_dir, repo_path="/path/to/repo")

        # Mock successful subprocess call
        mock_run.return_value = MagicMock(returncode=0)

        worktree_path = manager.create_worktree(ticket_id=62, branch_name="feature/62-agent-spawning")

        assert worktree_path is not None
        assert "62" in worktree_path
        mock_run.assert_called_once()

    def test_create_worktree_failure(self, temp_dir, mock_run):
        """Test handling of worktree creation failure"""
        manager = WorktreeManager(base_path=temp_dir, repo_path="/path/to/repo")

        # Mock failed subprocess call
        mock_run.return_value = MagicMock(returncode=1, stderr="error message")

        with pytest.raises(RuntimeError):
            manager.create_worktree(ticket_id=62, branch_name="feature/62-agent-spawning")

    def test_remove_worktree_success(self, temp_dir, mock_run):
        """Test removing a worktree successfully"""
        manager = WorktreeManager(base_path=temp_dir, repo_path="/path/to/repo")
        mock_run.return_value = MagicMock(returncode=0)

        # Create worktree directory first
        worktree_path = f"{temp_dir}/worktree-62"
        os.makedirs(worktree_path)

        success = manager.remove_worktree(worktree_path)

        assert success is True

    def test_remove_worktree_failure(self, temp_dir, mock_run):
        """Test handling of worktree removal failure"""
        manager = WorktreeManager(base_path=temp_dir, repo_path="/path/to/repo")
        mock_run.return_value = MagicMock(returncode=1, stderr="error message")

        # Create worktree directory first
        worktree_path = f"{temp_dir}/worktree-62"
        os.makedirs(worktree_path)

        with pytest.raises(RuntimeError):
            manager.remove_worktree(worktree_path)

    def test_get_worktree_path(self, temp_dir):
        """Test getting the path for a worktree"""
        manager = WorktreeManager(base_path=temp_dir, repo_path="/path/to/repo")

        path = manager.get_worktree_path(ticket_id=62)

        assert path is not None
        assert "62" in path
        assert temp_dir in path

    def test_worktree_exists(self, temp_dir):
        """Test checking if a worktree exists"""
        manager = WorktreeManager(base_path=temp_dir, repo_path="/path/to/repo")

        # Create a fake worktree directory
        fake_path = os.path.join(temp_dir, "worktree-62")
        os.makedirs(fake_path)

        exists = manager.worktree_exists(fake_path)
        assert exists is True

        non_existent = manager.worktree_exists(os.path.join(temp_dir, "non-existent"))
        assert non_existent is False
