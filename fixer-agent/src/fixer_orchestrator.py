"""Orchestrator for coordinating the code review finding remediation process."""

from enum import Enum
from typing import List, Callable, Protocol, Optional
from dataclasses import dataclass
from src.finding_parser import Finding, Severity
from src.fix_applicator import FixApplicator, FixResult


class OrchestrationResult(Enum):
    """Result of the fixing orchestration."""
    SUCCESS = "success"
    PARTIAL_SUCCESS = "partial_success"
    ALL_FIXES_FAILED = "all_fixes_failed"
    NO_FINDINGS = "no_findings"


class GitHandler(Protocol):
    """Interface for git operations."""
    def commit(self, message: str, author: str = None) -> bool:
        """Create a commit with the given message."""
        ...

    def push(self, branch: str) -> bool:
        """Push the branch to remote."""
        ...


class GitHubHandler(Protocol):
    """Interface for GitHub operations."""
    def post_comment(self, comment: str) -> bool:
        """Post a comment on the PR."""
        ...

    def add_label(self, label: str) -> bool:
        """Add a label to the PR."""
        ...


@dataclass
class FixSummary:
    """Summary of a fixed finding."""
    finding: Finding
    success: bool
    error_reason: Optional[str] = None


class FixerOrchestrator:
    """Orchestrates the process of fixing code review findings."""

    def __init__(self, base_dir: str, ticket_id: int):
        """Initialize the orchestrator.

        Args:
            base_dir: Base directory containing the source files
            ticket_id: GitHub ticket ID for this fix session
        """
        self.base_dir = base_dir
        self.ticket_id = ticket_id
        self.applicator = FixApplicator(base_dir)
        self.fix_summaries: List[FixSummary] = []

    def orchestrate(
        self,
        findings: List[Finding],
        github_handler: GitHubHandler,
        git_handler: GitHandler,
        test_runner: Callable[[], bool],
    ) -> OrchestrationResult:
        """Orchestrate the fixing of all findings.

        Args:
            findings: List of findings to fix
            github_handler: Handler for GitHub operations
            git_handler: Handler for git operations
            test_runner: Callable that runs tests and returns True if passing

        Returns:
            OrchestrationResult indicating success or failure
        """
        if not findings:
            return OrchestrationResult.NO_FINDINGS

        # Sort findings by severity
        sorted_findings = sorted(findings, key=lambda f: f.severity.value)

        # Apply fixes in priority order
        successful_fixes = []
        failed_fixes = []

        for finding in sorted_findings:
            if self._apply_fix(finding, test_runner):
                successful_fixes.append(finding)
                self.fix_summaries.append(FixSummary(finding=finding, success=True))
            else:
                failed_fixes.append(finding)
                self.fix_summaries.append(FixSummary(
                    finding=finding,
                    success=False,
                    error_reason="Failed to apply fix"
                ))

        # Determine overall result
        if not successful_fixes and failed_fixes:
            # All fixes failed
            github_handler.add_label('agent-failed')
            self._post_failure_comment(github_handler, failed_fixes)
            return OrchestrationResult.ALL_FIXES_FAILED
        elif successful_fixes and failed_fixes:
            # Some fixes succeeded
            commit_message = self._build_commit_message(successful_fixes)
            git_handler.commit(commit_message)
            git_handler.push(f"feature/{self.ticket_id}-fixer-agent")
            self._post_partial_summary(github_handler, successful_fixes, failed_fixes)
            return OrchestrationResult.PARTIAL_SUCCESS
        else:
            # All fixes succeeded
            commit_message = self._build_commit_message(successful_fixes)
            git_handler.commit(commit_message)
            git_handler.push(f"feature/{self.ticket_id}-fixer-agent")
            self._post_success_comment(github_handler, successful_fixes)
            return OrchestrationResult.SUCCESS

    def _apply_fix(
        self,
        finding: Finding,
        test_runner: Callable[[], bool],
    ) -> bool:
        """Apply a single fix and verify with tests.

        Args:
            finding: The finding to fix
            test_runner: Callable to verify the fix

        Returns:
            True if fix was successfully applied and tests pass
        """
        # Generate a basic fix function based on the finding
        # In a real implementation, this would use LLM or KB to determine the fix
        def generate_fix(content: str, line_num: int) -> str:
            # For now, return content unchanged
            # A real implementation would generate a fix based on:
            # 1. The finding summary and suggestion
            # 2. Knowledge base patterns
            # 3. LLM-based code understanding
            return content

        result = self.applicator.apply(
            finding=finding,
            fix_func=generate_fix,
            test_callback=test_runner,
            max_retries=3
        )

        return result == FixResult.SUCCESS

    def _build_commit_message(self, fixed_findings: List[Finding]) -> str:
        """Build a commit message for all fixed findings.

        Args:
            fixed_findings: List of successfully fixed findings

        Returns:
            Formatted commit message
        """
        lines = [f"fix: address code-review findings for #{self.ticket_id}\n"]
        lines.append("Fixed:")
        for finding in fixed_findings:
            lines.append(f"- {finding.summary}")
        lines.append(f"\nCo-Authored-By: Claude Haiku 4.5 <noreply@anthropic.com>")
        return '\n'.join(lines)

    def _post_success_comment(
        self,
        github_handler: GitHubHandler,
        fixed_findings: List[Finding],
    ) -> None:
        """Post a success comment summarizing fixes."""
        lines = ["✅ All findings fixed!\n"]
        lines.append("**Fixed issues:**")
        for finding in fixed_findings:
            lines.append(f"- `{finding.file}:{finding.line}` — {finding.summary}")
        github_handler.post_comment('\n'.join(lines))

    def _post_partial_summary(
        self,
        github_handler: GitHubHandler,
        successful_fixes: List[Finding],
        failed_fixes: List[Finding],
    ) -> None:
        """Post summary of partial fixes."""
        lines = ["⚠️ Partial fixes applied.\n"]
        if successful_fixes:
            lines.append("**Fixed:**")
            for finding in successful_fixes:
                lines.append(f"- `{finding.file}:{finding.line}` — {finding.summary}")
        if failed_fixes:
            lines.append("\n**Could not fix:**")
            for finding in failed_fixes:
                lines.append(f"- `{finding.file}:{finding.line}` — {finding.summary}")
        github_handler.post_comment('\n'.join(lines))

    def _post_failure_comment(
        self,
        github_handler: GitHubHandler,
        failed_findings: List[Finding],
    ) -> None:
        """Post failure comment when no fixes could be applied."""
        lines = ["❌ Could not apply any fixes.\n"]
        lines.append("**Unfixable issues:**")
        for finding in failed_findings:
            lines.append(f"- `{finding.file}:{finding.line}` — {finding.summary}")
        lines.append("\nPlease review manually or improve the KB patterns.")
        github_handler.post_comment('\n'.join(lines))
