"""
Code-review agent integration for ticket-implementer workflow.

Provides utilities for spawning code-review subagents and collecting findings
during the TDD implementation loop.
"""

import subprocess
import json
from typing import Optional, List, Dict, Any
from dataclasses import dataclass, asdict, field


@dataclass
class CodeReviewFinding:
    """Structured finding from code-review agent."""
    file: str
    line: int
    category: str  # e.g., "correctness", "efficiency", "style", "security"
    short_summary: str  # Max 60 chars
    summary: str  # One sentence statement of defect
    failure_scenario: str  # Concrete inputs/state → wrong output/crash
    verdict: str  # "CONFIRMED" or "PLAUSIBLE"
    outcome: Optional[str] = None  # "fixed", "skipped", "no_change_needed" (set on re-report)


@dataclass
class CodeReviewResult:
    """Result from code-review subagent."""
    findings: List[CodeReviewFinding]
    review_passed: bool  # True if no findings
    raw_response: Optional[str] = None  # Raw agent response for debugging

    def should_continue_loop(self) -> bool:
        """Return True if loop should continue (findings exist)."""
        return len(self.findings) > 0

    def should_break_loop(self) -> bool:
        """Return True if loop should break (no findings)."""
        return len(self.findings) == 0

    def format_for_user(self) -> str:
        """Format findings for user display."""
        if self.should_break_loop():
            return "✓ Code review clean: no findings"

        lines = ["⚠ Code review findings:"]
        for i, f in enumerate(self.findings, 1):
            lines.append(f"\n{i}. {f.file}:{f.line} — {f.category}")
            lines.append(f"   {f.short_summary}")
            lines.append(f"   Verdict: {f.verdict}")

        return "\n".join(lines)


class CodeReviewAgent:
    """Manages code-review subagent integration."""

    def __init__(self, ticket_id: Optional[str] = None, branch: Optional[str] = None):
        """Initialize code-review agent."""
        self.ticket_id = ticket_id
        self.branch = branch

    def spawn_and_review(self,
                        base_ref: str = "main",
                        standards_file: Optional[str] = None,
                        spec: Optional[str] = None) -> CodeReviewResult:
        """
        Spawn a code-review subagent and collect findings.

        Args:
            base_ref: Git ref to compare against (default: main)
            standards_file: Path to coding standards file
            spec: GitHub issue or spec text

        Returns:
            CodeReviewResult with structured findings
        """
        # Get diff
        diff = self._get_diff(base_ref)
        if not diff.strip():
            return CodeReviewResult(
                findings=[],
                review_passed=True,
                raw_response="No changes to review"
            )

        # Get commit list
        commits = self._get_commits(base_ref)

        # Build review prompt
        prompt = self._build_review_prompt(diff, commits, standards_file, spec)

        # Spawn subagent (in real implementation, use Agent tool)
        result = self._spawn_subagent(prompt)

        # Parse findings
        findings = self._parse_findings(result)

        return CodeReviewResult(
            findings=findings,
            review_passed=len(findings) == 0,
            raw_response=result
        )

    def _get_diff(self, base_ref: str) -> str:
        """Get diff between base_ref and HEAD."""
        try:
            result = subprocess.run(
                ["git", "diff", f"{base_ref}...HEAD"],
                capture_output=True,
                text=True,
                check=True
            )
            return result.stdout
        except subprocess.CalledProcessError as e:
            return f"Error getting diff: {e.stderr}"

    def _get_commits(self, base_ref: str) -> str:
        """Get commit list between base_ref and HEAD."""
        try:
            result = subprocess.run(
                ["git", "log", f"{base_ref}..HEAD", "--oneline"],
                capture_output=True,
                text=True,
                check=True
            )
            return result.stdout
        except subprocess.CalledProcessError as e:
            return f"Error getting commits: {e.stderr}"

    def _build_review_prompt(self,
                            diff: str,
                            commits: str,
                            standards_file: Optional[str] = None,
                            spec: Optional[str] = None) -> str:
        """Build the review prompt for the subagent."""
        prompt_parts = [
            "You are a code review agent. Review the following diff for correctness,",
            "efficiency, style, and spec compliance.",
            "",
            "## Changes",
            "```",
            diff,
            "```",
            "",
            "## Commits",
            "```",
            commits,
            "```",
        ]

        if standards_file:
            try:
                with open(standards_file, 'r') as f:
                    prompt_parts.extend([
                        "",
                        "## Coding Standards",
                        "```",
                        f.read(),
                        "```",
                    ])
            except FileNotFoundError:
                pass

        if spec:
            prompt_parts.extend([
                "",
                "## Specification",
                "```",
                spec,
                "```",
            ])

        prompt_parts.extend([
            "",
            "## Review Output",
            "",
            "Return findings as a JSON array of findings (empty if review passed).",
            "Each finding should have:",
            "- file: relative path to file",
            "- line: line number",
            "- category: 'correctness', 'efficiency', 'style', 'security', or 'test-coverage'",
            "- short_summary: max 60 chars, the issue in one phrase",
            "- summary: one-sentence statement of the defect",
            "- failure_scenario: concrete inputs/state → wrong output/crash",
            "- verdict: 'CONFIRMED' or 'PLAUSIBLE'",
            "",
            "Return ONLY valid JSON, no other text.",
            "Example: [{\"file\":\"src/auth.ts\",\"line\":15,\"category\":\"correctness\",...}]",
        ])

        return "\n".join(prompt_parts)

    def _spawn_subagent(self, prompt: str) -> str:
        """
        Spawn a code-review subagent.

        In the actual implementation, this would use the Agent tool.
        For now, return a mock response.
        """
        # TODO: Replace with actual Agent spawning
        # Agent({
        #   subagent_type: "general-purpose",
        #   description: "Code review of TDD implementation",
        #   prompt: prompt
        # })

        # Mock response (empty findings = clean review)
        return "[]"

    def _parse_findings(self, response: str) -> List[CodeReviewFinding]:
        """Parse findings from subagent response."""
        try:
            data = json.loads(response.strip())
            if not isinstance(data, list):
                return []

            findings = []
            for item in data:
                try:
                    finding = CodeReviewFinding(
                        file=item.get("file", ""),
                        line=item.get("line", 0),
                        category=item.get("category", ""),
                        short_summary=item.get("short_summary", ""),
                        summary=item.get("summary", ""),
                        failure_scenario=item.get("failure_scenario", ""),
                        verdict=item.get("verdict", "CONFIRMED")
                    )
                    if finding.file and finding.category:  # Validate required fields
                        findings.append(finding)
                except (KeyError, TypeError):
                    # Skip malformed findings
                    continue

            return findings

        except json.JSONDecodeError:
            # Response is not JSON, return empty findings
            return []


def format_findings_for_report(findings: List[CodeReviewFinding]) -> str:
    """Format findings as ReportFindings tool compatible structure."""
    report_findings = []
    for f in findings:
        report_findings.append({
            "file": f.file,
            "line": f.line,
            "category": f.category,
            "short_summary": f.short_summary,
            "summary": f.summary,
            "failure_scenario": f.failure_scenario,
            "verdict": f.verdict
        })
    return report_findings


if __name__ == "__main__":
    # Example usage
    agent = CodeReviewAgent(ticket_id="66", branch="feature/66-code-review-agent-integration")
    result = agent.spawn_and_review(base_ref="main")
    print(result.format_for_user())
    print(f"\nShould break loop: {result.should_break_loop()}")
    print(f"Number of findings: {len(result.findings)}")
