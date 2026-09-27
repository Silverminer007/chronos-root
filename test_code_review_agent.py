"""
Integration tests for CodeReviewAgent module.

Tests that the code-review integration works end-to-end.
"""

import unittest
import json
import tempfile
import os
from code_review_integration import (
    CodeReviewAgent, CodeReviewFinding, CodeReviewResult, format_findings_for_report
)


class TestCodeReviewAgentIntegration(unittest.TestCase):
    """Test CodeReviewAgent functionality."""

    def setUp(self):
        """Set up test fixtures."""
        self.agent = CodeReviewAgent(ticket_id="66", branch="feature/66-test")

    def test_parse_empty_findings(self):
        """Parse response with no findings."""
        response = "[]"
        findings = self.agent._parse_findings(response)
        self.assertEqual(len(findings), 0)

    def test_parse_single_finding(self):
        """Parse response with one finding."""
        response = json.dumps([{
            "file": "src/auth.ts",
            "line": 15,
            "category": "correctness",
            "short_summary": "Missing null check",
            "summary": "Function doesn't validate input before use",
            "failure_scenario": "Passing undefined → crash",
            "verdict": "CONFIRMED"
        }])

        findings = self.agent._parse_findings(response)
        self.assertEqual(len(findings), 1)
        self.assertEqual(findings[0].file, "src/auth.ts")
        self.assertEqual(findings[0].category, "correctness")
        self.assertEqual(findings[0].verdict, "CONFIRMED")

    def test_parse_multiple_findings(self):
        """Parse response with multiple findings."""
        response = json.dumps([
            {
                "file": "src/auth.ts",
                "line": 15,
                "category": "correctness",
                "short_summary": "Missing null check",
                "summary": "Null not validated",
                "failure_scenario": "Undefined → crash",
                "verdict": "CONFIRMED"
            },
            {
                "file": "src/types.ts",
                "line": 20,
                "category": "efficiency",
                "short_summary": "Redundant computation",
                "summary": "Value computed twice",
                "failure_scenario": "Every call wastes time",
                "verdict": "PLAUSIBLE"
            }
        ])

        findings = self.agent._parse_findings(response)
        self.assertEqual(len(findings), 2)
        self.assertEqual(findings[0].file, "src/auth.ts")
        self.assertEqual(findings[1].file, "src/types.ts")

    def test_parse_malformed_json(self):
        """Handle malformed JSON gracefully."""
        response = "not valid json"
        findings = self.agent._parse_findings(response)
        self.assertEqual(len(findings), 0)

    def test_parse_missing_required_fields(self):
        """Skip findings with missing required fields."""
        response = json.dumps([
            {
                "file": "src/auth.ts",
                # Missing line, category, etc.
                "short_summary": "Issue"
            },
            {
                "file": "src/types.ts",
                "line": 20,
                "category": "efficiency",
                "short_summary": "Valid finding",
                "summary": "Summary",
                "failure_scenario": "Scenario",
                "verdict": "CONFIRMED"
            }
        ])

        findings = self.agent._parse_findings(response)
        # Should skip first (incomplete), include second (complete)
        self.assertEqual(len(findings), 1)
        self.assertEqual(findings[0].file, "src/types.ts")

    def test_code_review_result_clean(self):
        """CodeReviewResult with no findings."""
        result = CodeReviewResult(findings=[], review_passed=True)
        self.assertTrue(result.should_break_loop())
        self.assertFalse(result.should_continue_loop())

        formatted = result.format_for_user()
        self.assertIn("clean", formatted.lower())

    def test_code_review_result_with_findings(self):
        """CodeReviewResult with findings."""
        findings = [
            CodeReviewFinding(
                file="src/auth.ts", line=15,
                category="correctness", short_summary="Issue",
                summary="Summary", failure_scenario="Scenario",
                verdict="CONFIRMED"
            )
        ]
        result = CodeReviewResult(findings=findings, review_passed=False)
        self.assertFalse(result.should_break_loop())
        self.assertTrue(result.should_continue_loop())

        formatted = result.format_for_user()
        self.assertIn("findings", formatted.lower())
        self.assertIn("src/auth.ts", formatted)

    def test_build_review_prompt_basic(self):
        """Build review prompt without standards or spec."""
        diff = "diff content"
        commits = "commit list"
        prompt = self.agent._build_review_prompt(diff, commits)

        self.assertIn("diff", prompt.lower())
        self.assertIn("commit", prompt.lower())
        self.assertIn("json", prompt.lower())

    def test_build_review_prompt_with_standards(self):
        """Build review prompt including standards file."""
        diff = "diff content"
        commits = "commit list"

        # Create temp standards file
        with tempfile.NamedTemporaryFile(mode='w', suffix='.md', delete=False) as f:
            f.write("# Coding Standards\n- Use const instead of var")
            standards_file = f.name

        try:
            prompt = self.agent._build_review_prompt(
                diff, commits, standards_file=standards_file
            )
            self.assertIn("Coding Standards", prompt)
            self.assertIn("Use const instead of var", prompt)
        finally:
            os.unlink(standards_file)

    def test_build_review_prompt_with_spec(self):
        """Build review prompt including spec."""
        diff = "diff content"
        commits = "commit list"
        spec = "GitHub issue #66: Implement code-review integration"

        prompt = self.agent._build_review_prompt(diff, commits, spec=spec)
        self.assertIn("Specification", prompt)
        self.assertIn("code-review", prompt)

    def test_format_findings_for_report(self):
        """Format findings in ReportFindings tool format."""
        findings = [
            CodeReviewFinding(
                file="src/auth.ts", line=15,
                category="correctness", short_summary="Issue",
                summary="Summary", failure_scenario="Scenario",
                verdict="CONFIRMED"
            ),
            CodeReviewFinding(
                file="src/types.ts", line=20,
                category="efficiency", short_summary="Inefficiency",
                summary="Redundant", failure_scenario="Perf loss",
                verdict="PLAUSIBLE"
            )
        ]

        report = format_findings_for_report(findings)
        self.assertEqual(len(report), 2)
        self.assertEqual(report[0]["file"], "src/auth.ts")
        self.assertEqual(report[1]["category"], "efficiency")


class TestCodeReviewFindingStructure(unittest.TestCase):
    """Test CodeReviewFinding data structure."""

    def test_finding_creation(self):
        """Create a finding with all fields."""
        finding = CodeReviewFinding(
            file="src/test.ts",
            line=42,
            category="correctness",
            short_summary="Missing validation",
            summary="Input not validated before use",
            failure_scenario="Null input → crash",
            verdict="CONFIRMED"
        )

        self.assertEqual(finding.file, "src/test.ts")
        self.assertEqual(finding.line, 42)
        self.assertEqual(finding.category, "correctness")
        self.assertIsNone(finding.outcome)  # Optional field

    def test_finding_with_outcome(self):
        """Create finding with outcome (re-report case)."""
        finding = CodeReviewFinding(
            file="src/test.ts",
            line=42,
            category="correctness",
            short_summary="Missing validation",
            summary="Input not validated before use",
            failure_scenario="Null input → crash",
            verdict="CONFIRMED",
            outcome="fixed"
        )

        self.assertEqual(finding.outcome, "fixed")


if __name__ == "__main__":
    unittest.main()
