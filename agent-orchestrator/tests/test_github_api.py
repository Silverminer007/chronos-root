import pytest
from unittest.mock import patch, MagicMock
from github_api import GitHubAPI


@pytest.fixture
def github_api():
    return GitHubAPI("owner/repo")


class TestGitHubAPI:
    """Tests for GitHub API wrapper."""

    @patch('github_api.subprocess.run')
    def test_list_ready_for_agent_success(self, mock_run, github_api):
        """List issues with ready-for-agent label."""
        mock_result = MagicMock()
        mock_result.returncode = 0
        mock_result.stdout = '[{"number": 62, "title": "Test"}]'
        mock_run.return_value = mock_result

        issues = github_api.list_ready_for_agent()

        assert len(issues) == 1
        assert issues[0]["number"] == 62

    @patch('github_api.subprocess.run')
    def test_list_ready_for_agent_failure(self, mock_run, github_api):
        """Return empty list on API failure."""
        mock_result = MagicMock()
        mock_result.returncode = 1
        mock_run.return_value = mock_result

        issues = github_api.list_ready_for_agent()

        assert issues == []

    @patch('github_api.subprocess.run')
    def test_list_ready_for_agent_bad_json(self, mock_run, github_api):
        """Return empty list on JSON parse error."""
        mock_result = MagicMock()
        mock_result.returncode = 0
        mock_result.stdout = '{invalid json'
        mock_run.return_value = mock_result

        issues = github_api.list_ready_for_agent()

        assert issues == []

    @patch('github_api.subprocess.run')
    def test_add_label_success(self, mock_run, github_api):
        """Add label to issue."""
        mock_result = MagicMock()
        mock_result.returncode = 0
        mock_run.return_value = mock_result

        success = github_api.add_label(62, "in-progress")

        assert success is True
        mock_run.assert_called_once()

    @patch('github_api.subprocess.run')
    def test_add_label_failure(self, mock_run, github_api):
        """Handle label add failure."""
        mock_result = MagicMock()
        mock_result.returncode = 1
        mock_run.return_value = mock_result

        success = github_api.add_label(62, "in-progress")

        assert success is False

    @patch('github_api.subprocess.run')
    def test_remove_label_success(self, mock_run, github_api):
        """Remove label from issue."""
        mock_result = MagicMock()
        mock_result.returncode = 0
        mock_run.return_value = mock_result

        success = github_api.remove_label(62, "ready-for-agent")

        assert success is True
        mock_run.assert_called_once()

    @patch('github_api.subprocess.run')
    def test_remove_label_failure(self, mock_run, github_api):
        """Handle label remove failure."""
        mock_result = MagicMock()
        mock_result.returncode = 1
        mock_run.return_value = mock_result

        success = github_api.remove_label(62, "ready-for-agent")

        assert success is False

    @patch('github_api.subprocess.run')
    def test_post_comment_success(self, mock_run, github_api):
        """Post comment on issue."""
        mock_result = MagicMock()
        mock_result.returncode = 0
        mock_run.return_value = mock_result

        success = github_api.post_comment(62, "Agent starting work")

        assert success is True
        mock_run.assert_called_once()

    @patch('github_api.subprocess.run')
    def test_post_comment_failure(self, mock_run, github_api):
        """Handle comment post failure."""
        mock_result = MagicMock()
        mock_result.returncode = 1
        mock_run.return_value = mock_result

        success = github_api.post_comment(62, "Agent starting work")

        assert success is False
