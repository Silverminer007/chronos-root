"""Tests for the finding parser component."""

import unittest
from src.finding_parser import FindingParser, Finding, Severity


class TestFindingParser(unittest.TestCase):
    """Test the finding parser that extracts code review findings from comments."""

    def test_parse_single_finding(self):
        """Parse a single critical finding."""
        comment = "[CRITICAL] src/main.py:42 — Missing error handling on network request"
        parser = FindingParser()
        findings = parser.parse(comment)

        self.assertEqual(len(findings), 1)
        finding = findings[0]
        self.assertEqual(finding.severity, Severity.CRITICAL)
        self.assertEqual(finding.file, "src/main.py")
        self.assertEqual(finding.line, 42)
        self.assertEqual(finding.summary, "Missing error handling on network request")

    def test_parse_multiple_findings(self):
        """Parse multiple findings from a comment."""
        comment = """
[CRITICAL] src/auth.py:15 — SQL injection vulnerability
[MAJOR] src/utils.py:88 — Missing null check
[MINOR] src/config.py:32 — Unused import
        """
        parser = FindingParser()
        findings = parser.parse(comment)

        self.assertEqual(len(findings), 3)
        self.assertEqual(findings[0].severity, Severity.CRITICAL)
        self.assertEqual(findings[1].severity, Severity.MAJOR)
        self.assertEqual(findings[2].severity, Severity.MINOR)

    def test_parse_finding_with_suggestion(self):
        """Parse a finding with a suggestion."""
        comment = """[MAJOR] src/service.py:50 — Logic error in validation
        Suggestion: Use isinstance() instead of type() for type checking"""
        parser = FindingParser()
        findings = parser.parse(comment)

        self.assertEqual(len(findings), 1)
        finding = findings[0]
        self.assertEqual(finding.severity, Severity.MAJOR)
        self.assertIn("isinstance()", finding.suggestion)

    def test_severity_ordering(self):
        """Findings can be ordered by severity."""
        parser = FindingParser()
        findings = [
            Finding(Severity.MINOR, "file.py", 10, "issue 1"),
            Finding(Severity.CRITICAL, "file.py", 20, "issue 2"),
            Finding(Severity.MAJOR, "file.py", 30, "issue 3"),
        ]

        sorted_findings = parser.sort_by_severity(findings)
        severities = [f.severity for f in sorted_findings]

        self.assertEqual(severities, [Severity.CRITICAL, Severity.MAJOR, Severity.MINOR])

    def test_empty_comment(self):
        """Empty comment returns no findings."""
        parser = FindingParser()
        findings = parser.parse("")
        self.assertEqual(findings, [])

    def test_malformed_finding(self):
        """Malformed findings are skipped."""
        comment = """
[CRITICAL] src/main.py:42 — Valid finding
This is not a valid finding line
[MAJOR] — Missing colon and line number
[MINOR] src/file.py:100 — Valid finding
        """
        parser = FindingParser()
        findings = parser.parse(comment)

        # Should extract only the valid findings
        self.assertEqual(len(findings), 2)
        self.assertEqual(findings[0].summary, "Valid finding")
        self.assertEqual(findings[1].summary, "Valid finding")


if __name__ == '__main__':
    unittest.main()
