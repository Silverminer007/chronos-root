"""Parser for extracting code review findings from agent comments."""

import re
from enum import Enum
from dataclasses import dataclass
from typing import List, Optional


class Severity(Enum):
    """Severity levels for code review findings."""
    CRITICAL = 1
    MAJOR = 2
    MINOR = 3


@dataclass
class Finding:
    """Represents a single code review finding."""
    severity: Severity
    file: str
    line: int
    summary: str
    suggestion: Optional[str] = None

    def __str__(self) -> str:
        return f"[{self.severity.name}] {self.file}:{self.line} — {self.summary}"


class FindingParser:
    """Parses code review findings from agent comments."""

    FINDING_PATTERN = re.compile(
        r'\[([A-Z]+)\]\s+([^:]+):(\d+)\s*—\s*([^\n]+(?:\n\s+[^\n]+)*)',
    )

    def parse(self, comment: str) -> List[Finding]:
        """Extract findings from a comment string.

        Args:
            comment: The agent comment containing findings

        Returns:
            List of Finding objects sorted by severity
        """
        if not comment or not comment.strip():
            return []

        findings = []
        for match in self.FINDING_PATTERN.finditer(comment):
            severity_str = match.group(1)
            file = match.group(2).strip()
            line = int(match.group(3))
            summary_with_suggestion = match.group(4)

            # Separate summary from suggestion if present
            summary, suggestion = self._extract_summary_and_suggestion(
                summary_with_suggestion
            )

            try:
                severity = Severity[severity_str]
                finding = Finding(
                    severity=severity,
                    file=file,
                    line=line,
                    summary=summary.strip(),
                    suggestion=suggestion
                )
                findings.append(finding)
            except KeyError:
                # Skip findings with invalid severity
                continue

        return self.sort_by_severity(findings)

    def _extract_summary_and_suggestion(
        self, text: str
    ) -> tuple[str, Optional[str]]:
        """Extract summary and suggestion from finding text.

        Args:
            text: Text containing summary and optional suggestion

        Returns:
            Tuple of (summary, suggestion)
        """
        # Look for "Suggestion:" pattern (case-insensitive, handles whitespace)
        suggestion_match = re.search(
            r'(?:^|\n)\s*Suggestion:\s*(.+)',
            text,
            re.MULTILINE | re.IGNORECASE
        )

        if suggestion_match:
            suggestion_text = suggestion_match.group(1).strip()
            summary = text[:suggestion_match.start()].strip()
            return summary, suggestion_text

        return text.strip(), None

    def sort_by_severity(self, findings: List[Finding]) -> List[Finding]:
        """Sort findings by severity (CRITICAL → MAJOR → MINOR).

        Args:
            findings: List of findings to sort

        Returns:
            Sorted list of findings
        """
        return sorted(findings, key=lambda f: f.severity.value)
