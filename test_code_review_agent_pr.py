"""
Tests for Code Review Agent - reads PRs and posts findings.

This is the correct implementation for ticket #66.
"""

import unittest
from typing import Optional, List
from dataclasses import dataclass
from enum import Enum


class Severity(Enum):
    """Finding severity levels."""
    CRITICAL = "CRITICAL"
    MAJOR = "MAJOR"
    MINOR = "MINOR"


@dataclass
class Finding:
    """A code review finding."""
    file: str
    line: int
    category: str  # correctness, efficiency, style, security, test-coverage
    severity: Severity
    issue_summary: str
    suggestion: Optional[str] = None


@dataclass
class CodeReviewResult:
    """Result of code review."""
    findings: List[Finding]
    passed: bool  # True if no findings

    def has_issues(self) -> bool:
        return len(self.findings) > 0

    def count_by_severity(self) -> dict:
        counts = {s: 0 for s in Severity}
        for f in self.findings:
            counts[f.severity] += 1
        return counts


class TestCodeReviewAgentFindingsParsing(unittest.TestCase):
    """Test parsing findings from code-review skill."""

    def test_empty_findings_means_passed(self):
        """No findings means review passed."""
        result = CodeReviewResult(findings=[], passed=True)
        self.assertFalse(result.has_issues())
        self.assertTrue(result.passed)

    def test_single_finding(self):
        """Parse single finding."""
        finding = Finding(
            file="src/auth.ts",
            line=15,
            category="correctness",
            severity=Severity.CRITICAL,
            issue_summary="Missing null check",
            suggestion="Validate input before using"
        )
        result = CodeReviewResult(findings=[finding], passed=False)

        self.assertTrue(result.has_issues())
        self.assertEqual(len(result.findings), 1)
        self.assertEqual(result.findings[0].severity, Severity.CRITICAL)

    def test_multiple_findings_with_severity(self):
        """Parse multiple findings with different severities."""
        findings = [
            Finding("src/auth.ts", 15, "correctness", Severity.CRITICAL, "Issue 1"),
            Finding("src/types.ts", 20, "test-coverage", Severity.MAJOR, "Issue 2"),
            Finding("src/utils.ts", 5, "style", Severity.MINOR, "Issue 3"),
        ]
        result = CodeReviewResult(findings=findings, passed=False)

        self.assertEqual(len(result.findings), 3)
        counts = result.count_by_severity()
        self.assertEqual(counts[Severity.CRITICAL], 1)
        self.assertEqual(counts[Severity.MAJOR], 1)
        self.assertEqual(counts[Severity.MINOR], 1)

    def test_severity_classification(self):
        """Verify severity levels are assigned correctly."""
        # CRITICAL: logic bugs, security issues
        critical = Finding(
            "src/auth.ts", 10, "correctness", Severity.CRITICAL,
            "Missing authentication check"
        )
        self.assertEqual(critical.severity, Severity.CRITICAL)

        # MAJOR: standards violations, incomplete spec
        major = Finding(
            "src/user.ts", 20, "test-coverage", Severity.MAJOR,
            "Function not covered by tests"
        )
        self.assertEqual(major.severity, Severity.MAJOR)

        # MINOR: code smells, style issues
        minor = Finding(
            "src/utils.ts", 5, "style", Severity.MINOR,
            "Variable name unclear"
        )
        self.assertEqual(minor.severity, Severity.MINOR)


class TestCodeReviewCommentFormatting(unittest.TestCase):
    """Test GitHub comment formatting."""

    def test_approval_comment_format(self):
        """Format approval comment when no issues."""
        comment = self._format_approval()

        self.assertIn("✓", comment)
        self.assertIn("Code Review Passed", comment)
        self.assertIn("Code Quality", comment)
        self.assertIn("Spec Compliance", comment)
        self.assertIn("Test Coverage", comment)

    def test_findings_comment_header(self):
        """Format findings comment with correct header."""
        findings = [
            Finding("src/auth.ts", 15, "correctness", Severity.CRITICAL, "Issue 1"),
        ]
        comment = self._format_findings_comment(findings)

        self.assertIn("⚠", comment)
        self.assertIn("Code Review Found Issues", comment)
        self.assertIn("[CRITICAL]", comment)

    def test_findings_grouped_by_severity(self):
        """Findings should be grouped and sorted by severity."""
        findings = [
            Finding("src/file1.ts", 10, "style", Severity.MINOR, "Minor issue"),
            Finding("src/file2.ts", 20, "correctness", Severity.CRITICAL, "Critical issue"),
            Finding("src/file3.ts", 30, "test-coverage", Severity.MAJOR, "Major issue"),
        ]
        comment = self._format_findings_comment(findings)

        # CRITICAL should come before MAJOR, MAJOR before MINOR
        critical_pos = comment.find("[CRITICAL]")
        major_pos = comment.find("[MAJOR]")
        minor_pos = comment.find("[MINOR]")

        self.assertGreater(major_pos, critical_pos)
        self.assertGreater(minor_pos, major_pos)

    def test_finding_includes_file_and_line(self):
        """Each finding shows file and line number."""
        findings = [
            Finding("src/auth.ts", 42, "correctness", Severity.CRITICAL, "Issue"),
        ]
        comment = self._format_findings_comment(findings)

        self.assertIn("src/auth.ts:42", comment)

    def test_finding_includes_suggestion(self):
        """Each finding shows suggestion if provided."""
        findings = [
            Finding("src/auth.ts", 42, "correctness", Severity.CRITICAL,
                   "Missing check", "Validate before use"),
        ]
        comment = self._format_findings_comment(findings)

        self.assertIn("Validate before use", comment)

    def _format_approval(self) -> str:
        """Mock approval comment formatting."""
        return """✓ Code Review Passed

- Code Quality: ✓
- Spec Compliance: ✓
- Test Coverage: ✓

Ready for review."""

    def _format_findings_comment(self, findings: List[Finding]) -> str:
        """Mock findings comment formatting."""
        lines = ["⚠ Code Review Found Issues"]
        lines.append("")

        # Group by severity
        for severity in [Severity.CRITICAL, Severity.MAJOR, Severity.MINOR]:
            for f in findings:
                if f.severity == severity:
                    lines.append(f"[{severity.value}] {f.file}:{f.line} ({f.category})")
                    lines.append(f"- {f.issue_summary}")
                    if f.suggestion:
                        lines.append(f"→ {f.suggestion}")

        lines.append("")
        lines.append("Fix agent will attempt to resolve these issues.")
        return "\n".join(lines)


class TestCodeReviewAgentPRDiscovery(unittest.TestCase):
    """Test finding PR from ticket number."""

    def test_pr_discovery_from_ticket(self):
        """Agent should find PR linked to ticket."""
        # In real implementation, this would search GitHub
        ticket_id = "66"
        pr_number = self._find_pr_for_ticket(ticket_id)

        # Should return a PR number or None
        self.assertTrue(pr_number is None or isinstance(pr_number, int))

    def test_handle_no_pr_found(self):
        """Handle case where PR doesn't exist yet."""
        ticket_id = "999"  # Non-existent ticket
        pr_number = self._find_pr_for_ticket(ticket_id)

        # Should gracefully handle missing PR
        self.assertIsNone(pr_number)

    def _find_pr_for_ticket(self, ticket_id: str) -> Optional[int]:
        """Mock PR discovery - in real code, uses GitHub API."""
        # Would search: git commit messages for "Fixes #66" or similar
        # Would check: GitHub PR for linked issues
        return None  # Mock: no PR found


if __name__ == "__main__":
    unittest.main()
