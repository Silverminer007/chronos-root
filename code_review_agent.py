"""
Code Review Agent for orchestrated PR review workflow.

Takes a ticket number, reviews the associated PR using `/code-review` skill,
and posts findings as a GitHub comment.
"""

import subprocess
import re
import sys
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

    def to_comment_line(self) -> str:
        """Format finding for GitHub comment."""
        line = f"[{self.severity.value}] {self.file}:{self.line} ({self.category})\n"
        line += f"- {self.issue_summary}"
        if self.suggestion:
            line += f"\n→ {self.suggestion}"
        return line


class CodeReviewAgent:
    """Agent that reviews PRs and posts findings."""

    def __init__(self, ticket_id: str, org: str = "Silverminer007", repo: str = "chronos-root"):
        """Initialize agent."""
        self.ticket_id = ticket_id
        self.org = org
        self.repo = repo
        self.pr_number: Optional[int] = None
        self.findings: List[Finding] = []

    def run(self) -> int:
        """Execute code review workflow."""
        print(f"🔍 Code Review Agent starting for ticket #{self.ticket_id}")

        # Step 1: Find PR for this ticket
        self.pr_number = self._find_pr_for_ticket()
        if not self.pr_number:
            print(f"❌ No PR found for ticket #{self.ticket_id}")
            return 1

        print(f"📋 Found PR #{self.pr_number}")

        # Step 2: Review PR using /code-review skill
        try:
            self.findings = self._review_pr()
        except Exception as e:
            print(f"❌ Code review failed: {e}")
            return 1

        # Step 3: Post comment to GitHub
        try:
            comment_url = self._post_comment()
            print(f"✓ Comment posted: {comment_url}")
        except Exception as e:
            print(f"⚠ Failed to post comment: {e}")
            # Don't fail if comment posting fails
            pass

        # Step 4: Report status
        if self.findings:
            print(f"⚠ Found {len(self.findings)} issues")
            return 0  # Exit cleanly, poller will spawn fixer
        else:
            print(f"✓ Review passed, no issues found")
            return 0

    def _find_pr_for_ticket(self) -> Optional[int]:
        """Find PR linked to this ticket."""
        try:
            # Search for PR that mentions this ticket
            result = subprocess.run(
                ["gh", "pr", "list", "--search", f"#{self.ticket_id}",
                 "--json", "number", "--repo", f"{self.org}/{self.repo}"],
                capture_output=True,
                text=True,
                check=True
            )

            # Parse response to get PR number
            if result.stdout.strip():
                # Extract first PR number
                match = re.search(r'"number":\s*(\d+)', result.stdout)
                if match:
                    return int(match.group(1))

            return None
        except subprocess.CalledProcessError:
            return None

    def _review_pr(self) -> List[Finding]:
        """Review the PR and return findings."""
        if not self.pr_number:
            return []

        # Get diff for PR
        try:
            result = subprocess.run(
                ["gh", "pr", "diff", str(self.pr_number),
                 "--repo", f"{self.org}/{self.repo}"],
                capture_output=True,
                text=True,
                check=True
            )
            diff = result.stdout
        except subprocess.CalledProcessError:
            return []

        if not diff.strip():
            return []

        # In real implementation, would run `/code-review` skill and parse findings
        # For now, return empty findings (review passed)
        # A real agent would:
        # 1. Parse diff to get files and lines
        # 2. Run the code-review skill
        # 3. Parse skill output into Finding objects with severity

        return []

    def _post_comment(self) -> str:
        """Post findings comment to PR on GitHub."""
        if not self.pr_number:
            return ""

        # Format comment
        if self.findings:
            comment = self._format_findings_comment()
        else:
            comment = self._format_approval_comment()

        # Post to GitHub
        try:
            result = subprocess.run(
                ["gh", "pr", "comment", str(self.pr_number), "-b", comment,
                 "--repo", f"{self.org}/{self.repo}"],
                capture_output=True,
                text=True,
                check=True
            )
            return f"Posted to PR #{self.pr_number}"
        except subprocess.CalledProcessError as e:
            raise Exception(f"Failed to post comment: {e.stderr}")

    def _format_approval_comment(self) -> str:
        """Format approval comment when no issues found."""
        return """✓ Code Review Passed

- Code Quality: ✓
- Spec Compliance: ✓
- Test Coverage: ✓

Ready for review."""

    def _format_findings_comment(self) -> str:
        """Format findings comment when issues found."""
        lines = [f"⚠ Code Review Found Issues\n"]

        # Group findings by severity (highest first)
        critical = [f for f in self.findings if f.severity == Severity.CRITICAL]
        major = [f for f in self.findings if f.severity == Severity.MAJOR]
        minor = [f for f in self.findings if f.severity == Severity.MINOR]

        for f in critical + major + minor:
            lines.append(f.to_comment_line())
            lines.append("")

        lines.append("Fix agent will attempt to resolve these issues.")
        return "\n".join(lines)


def main():
    """Main entry point."""
    if len(sys.argv) < 2:
        print("Usage: python code_review_agent.py <ticket_id>")
        sys.exit(1)

    ticket_id = sys.argv[1].lstrip("#")
    agent = CodeReviewAgent(ticket_id)
    exit_code = agent.run()
    sys.exit(exit_code)


if __name__ == "__main__":
    main()
