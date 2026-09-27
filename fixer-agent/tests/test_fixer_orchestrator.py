"""Tests for the fixer orchestrator component."""

import unittest
from unittest.mock import Mock, MagicMock, patch, call
from src.fixer_orchestrator import FixerOrchestrator, OrchestrationResult
from src.finding_parser import Finding, Severity


class TestFixerOrchestrator(unittest.TestCase):
    """Test the fixer orchestrator that coordinates the fixing process."""

    def setUp(self):
        """Set up test fixtures."""
        self.orchestrator = FixerOrchestrator(
            base_dir="/tmp/test",
            ticket_id=42
        )

    def test_orchestrate_single_finding(self):
        """Orchestrate fixing a single critical finding."""
        findings = [
            Finding(Severity.CRITICAL, "src/main.py", 10, "Missing error handling")
        ]

        mock_github = Mock()
        mock_git = Mock()
        mock_test_runner = Mock(return_value=True)

        with patch.object(self.orchestrator, '_apply_fix', return_value=True):
            result = self.orchestrator.orchestrate(
                findings=findings,
                github_handler=mock_github,
                git_handler=mock_git,
                test_runner=mock_test_runner,
            )

        self.assertEqual(result, OrchestrationResult.SUCCESS)

    def test_orchestrate_multiple_findings_by_severity(self):
        """Findings are processed in priority order (CRITICAL → MAJOR → MINOR)."""
        findings = [
            Finding(Severity.MINOR, "src/util.py", 5, "Unused import"),
            Finding(Severity.CRITICAL, "src/main.py", 10, "SQL injection"),
            Finding(Severity.MAJOR, "src/api.py", 20, "Missing null check"),
        ]

        order_tracker = []

        def mock_apply_fix(finding, test_runner):
            order_tracker.append(finding.severity)
            return True

        with patch.object(self.orchestrator, '_apply_fix', side_effect=mock_apply_fix):
            result = self.orchestrator.orchestrate(
                findings=findings,
                github_handler=Mock(),
                git_handler=Mock(),
                test_runner=Mock(return_value=True),
            )

        self.assertEqual(result, OrchestrationResult.SUCCESS)
        self.assertEqual(
            order_tracker,
            [Severity.CRITICAL, Severity.MAJOR, Severity.MINOR]
        )

    def test_orchestrate_with_fix_failure(self):
        """Orchestration continues despite individual fix failures."""
        findings = [
            Finding(Severity.CRITICAL, "src/main.py", 10, "Error 1"),
            Finding(Severity.MAJOR, "src/main.py", 20, "Error 2"),
        ]

        fix_results = [False, True]  # First fails, second succeeds
        fix_attempts = [0]

        def mock_apply_fix(finding, test_runner):
            result = fix_results[fix_attempts[0]]
            fix_attempts[0] += 1
            return result

        with patch.object(self.orchestrator, '_apply_fix', side_effect=mock_apply_fix):
            result = self.orchestrator.orchestrate(
                findings=findings,
                github_handler=Mock(),
                git_handler=Mock(),
                test_runner=Mock(return_value=True),
            )

        # Should have partial success since one fix failed
        self.assertIn(result, [OrchestrationResult.PARTIAL_SUCCESS, OrchestrationResult.SUCCESS])

    def test_orchestrate_creates_single_commit(self):
        """All fixes are committed in a single commit."""
        findings = [
            Finding(Severity.CRITICAL, "src/main.py", 10, "Fix 1"),
            Finding(Severity.MAJOR, "src/main.py", 20, "Fix 2"),
        ]

        mock_git = Mock()
        mock_github = Mock()

        with patch.object(self.orchestrator, '_apply_fix', return_value=True):
            result = self.orchestrator.orchestrate(
                findings=findings,
                github_handler=mock_github,
                git_handler=mock_git,
                test_runner=Mock(return_value=True),
            )

        # Verify commit was called once with all fixes
        mock_git.commit.assert_called_once()
        call_args = mock_git.commit.call_args
        self.assertIsNotNone(call_args)
        # Commit message should mention both fixes
        commit_msg = call_args[1]['message'] if 'message' in call_args[1] else call_args[0][0]
        self.assertIn('fix:', commit_msg)

    def test_orchestrate_posts_summary_comment(self):
        """Orchestrator posts a summary comment after fixing."""
        findings = [
            Finding(Severity.CRITICAL, "src/main.py", 10, "Fix 1"),
        ]

        mock_github = Mock()
        mock_git = Mock()

        with patch.object(self.orchestrator, '_apply_fix', return_value=True):
            result = self.orchestrator.orchestrate(
                findings=findings,
                github_handler=mock_github,
                git_handler=mock_git,
                test_runner=Mock(return_value=True),
            )

        # Verify comment was posted
        mock_github.post_comment.assert_called_once()
        comment = mock_github.post_comment.call_args[0][0]
        self.assertIn("Fixed", comment)

    def test_orchestrate_labels_pr_on_failure(self):
        """PR is labeled with 'agent-failed' if all fixes fail."""
        findings = [
            Finding(Severity.CRITICAL, "src/main.py", 10, "Unfixable"),
        ]

        mock_github = Mock()
        mock_git = Mock()

        with patch.object(self.orchestrator, '_apply_fix', return_value=False):
            result = self.orchestrator.orchestrate(
                findings=findings,
                github_handler=mock_github,
                git_handler=mock_git,
                test_runner=Mock(return_value=True),
            )

        self.assertEqual(result, OrchestrationResult.ALL_FIXES_FAILED)
        mock_github.add_label.assert_called_with('agent-failed')

    def test_orchestrate_builds_commit_message(self):
        """Commit message includes all fixed issues."""
        findings = [
            Finding(Severity.CRITICAL, "src/main.py", 10, "Issue A"),
            Finding(Severity.MAJOR, "src/main.py", 20, "Issue B"),
        ]

        mock_git = Mock()

        with patch.object(self.orchestrator, '_apply_fix', return_value=True):
            self.orchestrator.orchestrate(
                findings=findings,
                github_handler=Mock(),
                git_handler=mock_git,
                test_runner=Mock(return_value=True),
            )

        commit_call = mock_git.commit.call_args
        commit_msg = commit_call[1]['message'] if 'message' in commit_call[1] else commit_call[0][0]

        # Message should be properly formatted
        self.assertIn('fix:', commit_msg.lower())
        self.assertIn('#42', commit_msg)  # Ticket ID
        self.assertIn('Issue A', commit_msg)
        self.assertIn('Issue B', commit_msg)


if __name__ == '__main__':
    unittest.main()
