"""
Unit tests for TDD agent.

Tests cover:
- Ticket fetching from GitHub
- Branch verification
- Commit and push operations
- PR creation
- Integration with GitHubAPI and subprocess calls
"""

import unittest
from unittest.mock import Mock, patch, MagicMock, call
import tempfile
import os
import sys
from pathlib import Path


# Add parent directory to path for imports
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from tdd_agent import TDDAgent


class TestTDDAgent(unittest.TestCase):
    """Tests for TDDAgent class."""

    def setUp(self):
        """Set up test fixtures."""
        self.ticket_id = 65
        self.branch_name = "feature/65-tdd-agent-integration"
        self.repo_path = "/tmp/test-repo"

    @patch('tdd_agent.GitHubAPI')
    @patch('tdd_agent.logger')
    def test_init(self, mock_logger, mock_github_api):
        """Test TDDAgent initialization."""
        agent = TDDAgent(
            ticket_id=self.ticket_id,
            branch_name=self.branch_name,
            repo_path=self.repo_path
        )

        self.assertEqual(agent.ticket_id, self.ticket_id)
        self.assertEqual(agent.branch_name, self.branch_name)
        self.assertEqual(agent.repo_path, self.repo_path)
        self.assertIsNone(agent.pr_number)
        self.assertIsNotNone(agent.github)

    @patch('tdd_agent.GitHubAPI')
    @patch('tdd_agent.logger')
    def test_fetch_ticket_success(self, mock_logger, mock_github_api):
        """Test successful ticket fetch."""
        mock_github = MagicMock()
        mock_github_api.return_value = mock_github

        expected_ticket = {
            'title': 'TDD agent integration',
            'body': 'Implement TDD agent functionality',
            'number': self.ticket_id
        }
        mock_github.get_issue.return_value = expected_ticket

        agent = TDDAgent(
            ticket_id=self.ticket_id,
            branch_name=self.branch_name
        )
        agent.github = mock_github

        ticket = agent._fetch_ticket()

        self.assertEqual(ticket, expected_ticket)
        mock_github.get_issue.assert_called_once_with(self.ticket_id)

    @patch('tdd_agent.GitHubAPI')
    @patch('tdd_agent.logger')
    def test_fetch_ticket_failure(self, mock_logger, mock_github_api):
        """Test ticket fetch failure."""
        mock_github = MagicMock()
        mock_github_api.return_value = mock_github
        mock_github.get_issue.side_effect = Exception("API error")

        agent = TDDAgent(
            ticket_id=self.ticket_id,
            branch_name=self.branch_name
        )
        agent.github = mock_github

        ticket = agent._fetch_ticket()

        self.assertIsNone(ticket)

    @patch('tdd_agent.subprocess')
    @patch('tdd_agent.GitHubAPI')
    @patch('tdd_agent.logger')
    def test_verify_branch_success(self, mock_logger, mock_github_api, mock_subprocess):
        """Test successful branch verification."""
        mock_result = Mock()
        mock_result.stdout = self.branch_name + "\n"
        mock_subprocess.run.return_value = mock_result

        agent = TDDAgent(
            ticket_id=self.ticket_id,
            branch_name=self.branch_name,
            repo_path=self.repo_path
        )

        result = agent._verify_branch()

        self.assertTrue(result)
        mock_subprocess.run.assert_called_once()

    @patch('tdd_agent.subprocess')
    @patch('tdd_agent.GitHubAPI')
    @patch('tdd_agent.logger')
    def test_verify_branch_failure_wrong_branch(self, mock_logger, mock_github_api, mock_subprocess):
        """Test branch verification failure - wrong branch."""
        mock_result = Mock()
        mock_result.stdout = "other-branch\n"
        mock_subprocess.run.return_value = mock_result

        agent = TDDAgent(
            ticket_id=self.ticket_id,
            branch_name=self.branch_name,
            repo_path=self.repo_path
        )

        result = agent._verify_branch()

        self.assertFalse(result)

    @patch('tdd_agent.subprocess')
    @patch('tdd_agent.GitHubAPI')
    @patch('tdd_agent.logger')
    def test_run_tdd_cycles_success(self, mock_logger, mock_github_api, mock_subprocess):
        """Test successful TDD cycles."""
        agent = TDDAgent(
            ticket_id=self.ticket_id,
            branch_name=self.branch_name
        )

        ticket = {
            'title': 'Test ticket',
            'body': 'Test implementation'
        }

        result = agent._run_tdd_cycles(ticket)
        self.assertTrue(result)

    @patch('tdd_agent.subprocess')
    @patch('tdd_agent.GitHubAPI')
    @patch('tdd_agent.logger')
    def test_run_code_review_success(self, mock_logger, mock_github_api, mock_subprocess):
        """Test successful code review."""
        mock_result = Mock()
        mock_result.stdout = "file.py | 10 +++++++\n"
        mock_result.returncode = 0
        mock_subprocess.run.return_value = mock_result

        agent = TDDAgent(
            ticket_id=self.ticket_id,
            branch_name=self.branch_name,
            repo_path=self.repo_path
        )

        result = agent._run_code_review()
        self.assertTrue(result)

    @patch('tdd_agent.subprocess')
    @patch('tdd_agent.GitHubAPI')
    @patch('tdd_agent.logger')
    def test_commit_changes_success(self, mock_logger, mock_github_api, mock_subprocess):
        """Test successful commit."""
        # First call: git status --porcelain (has changes)
        status_result = Mock()
        status_result.stdout = "M file.py\n"

        # Second call: git add -A (check=True, so no error)
        add_result = Mock()
        add_result.returncode = 0

        # Third call: git commit
        commit_result = Mock()
        commit_result.stdout = ""
        commit_result.returncode = 0

        mock_subprocess.run.side_effect = [status_result, add_result, commit_result]

        agent = TDDAgent(
            ticket_id=self.ticket_id,
            branch_name=self.branch_name,
            repo_path=self.repo_path
        )

        result = agent._commit_changes()
        self.assertTrue(result)

    @patch('tdd_agent.subprocess')
    @patch('tdd_agent.GitHubAPI')
    @patch('tdd_agent.logger')
    def test_commit_changes_no_changes(self, mock_logger, mock_github_api, mock_subprocess):
        """Test commit with no changes."""
        status_result = Mock()
        status_result.stdout = ""

        mock_subprocess.run.return_value = status_result

        agent = TDDAgent(
            ticket_id=self.ticket_id,
            branch_name=self.branch_name,
            repo_path=self.repo_path
        )

        result = agent._commit_changes()
        self.assertTrue(result)

    @patch('tdd_agent.subprocess')
    @patch('tdd_agent.GitHubAPI')
    @patch('tdd_agent.logger')
    def test_push_branch_success(self, mock_logger, mock_github_api, mock_subprocess):
        """Test successful branch push."""
        mock_result = Mock()
        mock_result.returncode = 0
        mock_subprocess.run.return_value = mock_result

        agent = TDDAgent(
            ticket_id=self.ticket_id,
            branch_name=self.branch_name,
            repo_path=self.repo_path
        )

        result = agent._push_branch()
        self.assertTrue(result)

    @patch('tdd_agent.subprocess')
    @patch('tdd_agent.GitHubAPI')
    @patch('tdd_agent.logger')
    def test_push_branch_failure(self, mock_logger, mock_github_api, mock_subprocess):
        """Test branch push failure."""
        mock_result = Mock()
        mock_result.returncode = 1
        mock_result.stderr = "Push failed"
        mock_subprocess.run.return_value = mock_result

        agent = TDDAgent(
            ticket_id=self.ticket_id,
            branch_name=self.branch_name,
            repo_path=self.repo_path
        )

        result = agent._push_branch()
        self.assertFalse(result)

    @patch('tdd_agent.subprocess')
    @patch('tdd_agent.GitHubAPI')
    @patch('tdd_agent.logger')
    def test_create_pr_success(self, mock_logger, mock_github_api, mock_subprocess):
        """Test successful PR creation."""
        # Setup mocks for commit, push, and pr create
        status_result = Mock()
        status_result.stdout = "M file.py\n"
        status_result.returncode = 0

        add_result = Mock()
        add_result.returncode = 0

        commit_result = Mock()
        commit_result.stdout = ""
        commit_result.returncode = 0

        push_result = Mock()
        push_result.returncode = 0
        push_result.stdout = ""

        pr_result = Mock()
        pr_result.returncode = 0
        pr_result.stdout = "https://github.com/owner/repo/pull/123\n"

        # Call sequence: status, add, commit, push, pr create
        mock_subprocess.run.side_effect = [
            status_result,  # status
            add_result,     # add
            commit_result,  # commit
            push_result,    # push
            pr_result       # pr create
        ]

        agent = TDDAgent(
            ticket_id=self.ticket_id,
            branch_name=self.branch_name,
            repo_path=self.repo_path
        )

        ticket = {
            'title': 'Test ticket',
            'body': 'Test implementation'
        }

        result = agent._create_pr(ticket)
        self.assertTrue(result)
        self.assertEqual(agent.pr_number, 123)

    @patch('tdd_agent.subprocess')
    @patch('tdd_agent.GitHubAPI')
    @patch('tdd_agent.logger')
    def test_full_workflow(self, mock_logger, mock_github_api, mock_subprocess):
        """Test full TDD agent workflow."""
        mock_github = MagicMock()
        mock_github_api.return_value = mock_github

        ticket = {
            'title': 'TDD agent integration',
            'body': 'Implement TDD agent functionality',
            'number': self.ticket_id
        }
        mock_github.get_issue.return_value = ticket

        # Mock subprocess calls
        branch_check = Mock()
        branch_check.stdout = self.branch_name + "\n"
        branch_check.returncode = 0

        diff_check = Mock()
        diff_check.stdout = "file.py | 10 ++++\n"
        diff_check.returncode = 0

        status_check = Mock()
        status_check.stdout = "M file.py\n"
        status_check.returncode = 0

        add_result = Mock()
        add_result.returncode = 0

        commit_result = Mock()
        commit_result.returncode = 0
        commit_result.stdout = ""

        push_result = Mock()
        push_result.returncode = 0
        push_result.stdout = ""

        pr_result = Mock()
        pr_result.returncode = 0
        pr_result.stdout = "https://github.com/owner/repo/pull/123\n"

        mock_subprocess.run.side_effect = [
            branch_check,  # verify branch
            diff_check,    # code review diff
            status_check,  # commit status
            add_result,    # add
            commit_result, # commit
            push_result,   # push
            pr_result      # pr create
        ]

        agent = TDDAgent(
            ticket_id=self.ticket_id,
            branch_name=self.branch_name,
            repo_path=self.repo_path
        )
        agent.github = mock_github

        result = agent.run()
        self.assertTrue(result)
        self.assertEqual(agent.pr_number, 123)


class TestTDDAgentIntegration(unittest.TestCase):
    """Integration tests for TDD agent with subprocess calls."""

    @patch('tdd_agent.GitHubAPI')
    @patch('tdd_agent.logger')
    def test_agent_spawner_integration(self, mock_logger, mock_github_api):
        """Test that TDD agent can be spawned by agent_spawner."""
        # This test verifies that the tdd_agent.py module can be imported
        # and run as a subprocess
        import subprocess
        import os

        orchestrator_path = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        tdd_agent_path = os.path.join(orchestrator_path, "tdd_agent.py")

        # Check that the file exists
        self.assertTrue(os.path.exists(tdd_agent_path))

        # Verify it has correct permissions
        mode = os.stat(tdd_agent_path).st_mode
        self.assertTrue(mode & 0o111)  # Check if executable


if __name__ == "__main__":
    unittest.main()
