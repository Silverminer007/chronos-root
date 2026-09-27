"""
Tests for the Spec Validator agent.
"""

import unittest
from unittest.mock import Mock, patch, MagicMock
import json
import tempfile
import os
from spec_validator import SpecValidator, ValidationResult


class TestSpecValidatorBasics(unittest.TestCase):
    """Test basic spec validation functionality."""

    def setUp(self):
        """Set up test fixtures."""
        self.validator = SpecValidator()

    def test_validate_accepts_dict_spec(self):
        """Validator should accept spec as dictionary."""
        spec = {
            "title": "Test feature",
            "body": "Test body with acceptance criteria:\n- [ ] Criterion 1"
        }
        result = self.validator.validate(spec)
        self.assertIsInstance(result, ValidationResult)

    def test_validates_missing_acceptance_criteria(self):
        """Should detect missing acceptance criteria."""
        spec = {
            "title": "Test feature",
            "body": "Just a vague description without acceptance criteria."
        }
        result = self.validator.validate(spec)
        self.assertFalse(result.is_valid)
        self.assertTrue(any("acceptance criteria" in q.lower() for q in result.questions))

    def test_validates_testable_acceptance_criteria(self):
        """Should recognize testable acceptance criteria."""
        spec = {
            "title": "Add user authentication",
            "body": """
## Acceptance Criteria
- [ ] User can log in with email and password
- [ ] JWT token is returned and stored in httpOnly cookie
- [ ] Expired tokens trigger refresh endpoint
"""
        }
        result = self.validator.validate(spec)
        # Should not complain about missing/untestable criteria
        self.assertTrue(result.is_valid or not any("acceptance criteria" in q.lower() for q in result.questions))

    def test_detects_scope_ambiguity(self):
        """Should detect vague or unbounded scope."""
        spec = {
            "title": "Improve performance",
            "body": "Make things faster and better. Should fix all performance issues."
        }
        result = self.validator.validate(spec)
        self.assertFalse(result.is_valid)
        self.assertTrue(any("scope" in q.lower() or "boundary" in q.lower() for q in result.questions))

    def test_validates_clear_scope(self):
        """Should pass clear, bounded scope."""
        spec = {
            "title": "Optimize database query for appointment listing",
            "body": """
## Scope
- Optimize GET /appointments endpoint response time from ~500ms to <100ms
- Focus on N+1 query problems in Hibernate ORM
- Do not change API contract

## Acceptance Criteria
- [ ] Endpoint returns within 100ms for 1000 appointments
- [ ] Query uses single SELECT with LEFT JOIN, no N+1
- [ ] Unit tests verify query performance
"""
        }
        result = self.validator.validate(spec)
        self.assertTrue(result.is_valid or not any("scope" in q.lower() for q in result.questions))

    def test_detects_missing_dependencies(self):
        """Should prompt for missing dependency documentation."""
        spec = {
            "title": "Add Sentry integration",
            "body": "Integrate Sentry error tracking into frontend."
        }
        result = self.validator.validate(spec)
        # Should ask about dependencies (Sentry account, config, etc.)
        self.assertFalse(result.is_valid)
        # May ask about dependencies or related components
        self.assertTrue(len(result.questions) > 0)

    def test_identifies_ambiguous_terminology(self):
        """Should flag undefined or unclear terms."""
        spec = {
            "title": "Implement the widget thing",
            "body": "Create a widget that does the normal process. Must be performant and stable."
        }
        result = self.validator.validate(spec)
        self.assertFalse(result.is_valid)
        self.assertTrue(any("unclear" in q.lower() or "terminology" in q.lower() or "widget" in q.lower()
                           for q in result.questions))

    def test_returns_questions_when_invalid(self):
        """Invalid specs should return actionable questions."""
        spec = {
            "title": "Add feature",
            "body": "Do stuff"
        }
        result = self.validator.validate(spec)
        self.assertFalse(result.is_valid)
        self.assertTrue(len(result.questions) > 0)
        # Questions should be concise and answerable
        for q in result.questions:
            self.assertGreater(len(q), 10)
            self.assertLess(len(q), 500)

    def test_silent_exit_when_valid(self):
        """Valid specs should return minimal output."""
        spec = {
            "title": "Fix null pointer exception in UserService",
            "body": """
## Problem
UserService.getProfile() throws NPE when user not found.

## Acceptance Criteria
- [ ] getProfile() returns empty Optional<User> instead of throwing
- [ ] API returns 404 when user not found
- [ ] Unit tests cover both paths

## Dependencies
- No external dependencies
"""
        }
        result = self.validator.validate(spec)
        self.assertTrue(result.is_valid)
        self.assertEqual(len(result.questions), 0)


class TestValidationResult(unittest.TestCase):
    """Test ValidationResult data structure."""

    def test_result_has_is_valid_field(self):
        """Result should have is_valid boolean."""
        result = ValidationResult(is_valid=True, questions=[])
        self.assertTrue(result.is_valid)

    def test_result_has_questions_list(self):
        """Result should have questions list."""
        questions = ["What is X?", "How does Y work?"]
        result = ValidationResult(is_valid=False, questions=questions)
        self.assertEqual(result.questions, questions)

    def test_result_formats_as_comment(self):
        """Result should format as GitHub comment text."""
        result = ValidationResult(
            is_valid=False,
            questions=["What scope is intended?", "How will you test this?"]
        )
        comment = result.as_github_comment()
        self.assertIn("Ticket underspecified", comment)
        self.assertIn("What scope is intended?", comment)
        self.assertIn("How will you test this?", comment)
        self.assertIn("ready-for-agent", comment)


class TestSpecValidatorGitHubIntegration(unittest.TestCase):
    """Test integration with GitHub API."""

    @patch('spec_validator.subprocess.run')
    def test_fetch_issue_via_gh(self, mock_run):
        """Should fetch issue using gh CLI."""
        mock_run.return_value = MagicMock(
            stdout='{"number": 65, "title": "Test", "body": "Test body"}',
            returncode=0
        )
        validator = SpecValidator()
        issue = validator.fetch_issue(65)

        self.assertEqual(issue['number'], 65)
        self.assertEqual(issue['title'], 'Test')

    @patch('spec_validator.subprocess.run')
    def test_post_comment_to_issue(self, mock_run):
        """Should post comment to GitHub issue."""
        mock_run.return_value = MagicMock(returncode=0)
        validator = SpecValidator()
        result = ValidationResult(
            is_valid=False,
            questions=["What is scope?"]
        )

        validator.post_comment(65, result)

        # Verify gh CLI was called with comment
        self.assertTrue(mock_run.called)
        call_args = mock_run.call_args
        # Should contain gh issue comment command
        self.assertIn('comment', ' '.join(call_args[0][0]))

    @patch('spec_validator.subprocess.run')
    def test_add_label_to_issue(self, mock_run):
        """Should add label to GitHub issue."""
        mock_run.return_value = MagicMock(returncode=0)
        validator = SpecValidator()
        validator.add_label(65, 'agent-needs-input')

        self.assertTrue(mock_run.called)


class TestSpecValidatorKnowledgeBase(unittest.TestCase):
    """Test Knowledge Base integration."""

    @patch('spec_validator.SpecValidator.load_kb')
    def test_uses_kb_for_validation(self, mock_load_kb):
        """Should load and use KB during validation."""
        mock_load_kb.return_value = {
            "patterns": {
                "acceptance_criteria": ["Acceptance Criteria", "Acceptance criteria", "Definition of Done"],
                "scope": ["Scope", "Boundaries", "Out of Scope"]
            }
        }

        validator = SpecValidator()
        spec = {
            "title": "Test",
            "body": "No clear sections"
        }
        result = validator.validate(spec)

        # Should have called load_kb
        mock_load_kb.assert_called()

    def test_load_kb_from_file(self):
        """Should load KB from JSON file if present."""
        with tempfile.TemporaryDirectory() as tmpdir:
            kb_file = os.path.join(tmpdir, 'knowledge-base.json')
            kb_data = {"domain": "chronos", "patterns": []}
            with open(kb_file, 'w') as f:
                json.dump(kb_data, f)

            validator = SpecValidator(kb_path=kb_file)
            kb = validator.load_kb()

            self.assertEqual(kb['domain'], 'chronos')


class TestSpecValidatorCLI(unittest.TestCase):
    """Test command-line interface."""

    @patch('spec_validator.SpecValidator.add_label')
    @patch('spec_validator.SpecValidator.fetch_issue')
    @patch('spec_validator.SpecValidator.post_comment')
    def test_cli_exits_silently_on_valid_spec(self, mock_post, mock_fetch, mock_label):
        """Valid spec should exit with code 0, no output."""
        mock_fetch.return_value = {
            "number": 65,
            "title": "Fix bug",
            "body": """
## Problem
Bug description.

## Acceptance Criteria
- [ ] Fixed

## Dependencies
None
"""
        }

        validator = SpecValidator()
        exit_code = validator.validate_and_respond(65)

        self.assertEqual(exit_code, 0)
        mock_post.assert_not_called()

    @patch('spec_validator.SpecValidator.fetch_issue')
    @patch('spec_validator.SpecValidator.post_comment')
    @patch('spec_validator.SpecValidator.add_label')
    def test_cli_posts_comment_on_invalid_spec(self, mock_label, mock_post, mock_fetch):
        """Invalid spec should post comment and add label."""
        mock_fetch.return_value = {
            "number": 65,
            "title": "Vague feature",
            "body": "Make it better"
        }

        validator = SpecValidator()
        exit_code = validator.validate_and_respond(65)

        self.assertEqual(exit_code, 0)
        mock_post.assert_called_once()
        mock_label.assert_called_with(65, 'agent-needs-input')


if __name__ == '__main__':
    unittest.main()
