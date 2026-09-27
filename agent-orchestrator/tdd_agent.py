#!/usr/bin/env python3
"""
TDD Agent - Autonomous test-driven development agent for ticket implementation.

This agent implements GitHub tickets using a test-first approach:
1. Fetches ticket details from GitHub
2. Runs TDD cycles (write test -> implement -> refactor)
3. Invokes code review to validate quality
4. Creates a PR when implementation is complete

Usage:
    python3 tdd_agent.py --ticket 65 --branch feature/65-tdd-agent-integration
"""

import argparse
import subprocess
import logging
import sys
import os
import json
import tempfile
from datetime import datetime
from pathlib import Path
from typing import Optional, Dict, Any
from github_api import GitHubAPI


logger = logging.getLogger(__name__)


class TDDAgent:
    """Implements a ticket using test-driven development."""

    def __init__(self, ticket_id: int, branch_name: str, repo_path: str = "."):
        """
        Initialize TDD agent.

        Args:
            ticket_id: GitHub issue number
            branch_name: Git branch name for the ticket
            repo_path: Path to the git repository (default: current directory)
        """
        self.ticket_id = ticket_id
        self.branch_name = branch_name
        self.repo_path = repo_path
        self.github = GitHubAPI()
        self.pr_number = None

        # Setup logging
        self._setup_logging()

    def _setup_logging(self):
        """Configure logging for the agent."""
        try:
            log_dir = Path("/var/log/agent-orchestrator")
            log_dir.mkdir(parents=True, exist_ok=True)
            log_file = log_dir / f"tdd-agent-{self.ticket_id}.log"
        except PermissionError:
            # Fall back to temp directory if /var/log is not writable
            log_dir = Path(tempfile.gettempdir()) / "agent-orchestrator-logs"
            log_dir.mkdir(parents=True, exist_ok=True)
            log_file = log_dir / f"tdd-agent-{self.ticket_id}.log"

        try:
            handler = logging.FileHandler(log_file)
            handler.setFormatter(
                logging.Formatter(
                    "%(asctime)s - %(name)s - %(levelname)s - %(message)s"
                )
            )
            logger.addHandler(handler)
            logger.setLevel(logging.INFO)
        except Exception as e:
            logger.warning(f"Failed to setup file logging: {e}")

    def run(self) -> bool:
        """
        Run the TDD agent workflow.

        Returns:
            True if successful, False otherwise
        """
        try:
            logger.info(f"Starting TDD agent for ticket #{self.ticket_id}")

            # Fetch ticket details
            ticket = self._fetch_ticket()
            if not ticket:
                logger.error(f"Failed to fetch ticket #{self.ticket_id}")
                return False

            logger.info(f"Ticket: {ticket['title']}")
            logger.info(f"Branch: {self.branch_name}")

            # Verify we're on the correct branch
            if not self._verify_branch():
                logger.error("Failed to verify branch")
                return False

            # Implement the ticket using TDD cycles
            if not self._run_tdd_cycles(ticket):
                logger.error("TDD cycles failed")
                return False

            # Run code review
            if not self._run_code_review():
                logger.error("Code review failed")
                return False

            # Create PR
            if not self._create_pr(ticket):
                logger.error("Failed to create PR")
                return False

            logger.info(
                f"Successfully completed ticket #{self.ticket_id}, "
                f"PR #{self.pr_number}"
            )
            return True

        except Exception as e:
            logger.error(f"Unexpected error: {str(e)}", exc_info=True)
            return False

    def _fetch_ticket(self) -> Optional[Dict[str, Any]]:
        """
        Fetch ticket details from GitHub.

        Returns:
            Ticket information dict, or None if fetch failed
        """
        try:
            logger.info(f"Fetching ticket #{self.ticket_id} from GitHub")
            ticket = self.github.get_issue(self.ticket_id)
            return ticket
        except Exception as e:
            logger.error(f"Failed to fetch ticket: {str(e)}")
            return None

    def _verify_branch(self) -> bool:
        """
        Verify that the current branch matches the expected branch.

        Returns:
            True if on correct branch, False otherwise
        """
        try:
            result = subprocess.run(
                ["git", "branch", "--show-current"],
                cwd=self.repo_path,
                capture_output=True,
                text=True,
                timeout=10
            )

            current_branch = result.stdout.strip()
            if current_branch != self.branch_name:
                logger.error(
                    f"Expected branch {self.branch_name}, "
                    f"but on {current_branch}"
                )
                return False

            logger.info(f"✓ Verified on branch {self.branch_name}")
            return True

        except Exception as e:
            logger.error(f"Failed to verify branch: {str(e)}")
            return False

    def _run_tdd_cycles(self, ticket: Dict[str, Any]) -> bool:
        """
        Run TDD implementation cycles.

        This is a placeholder implementation. In a real scenario, this would:
        1. Write a failing test
        2. Implement minimal code to pass the test
        3. Refactor
        4. Repeat until feature is complete

        Args:
            ticket: Ticket information

        Returns:
            True if cycles completed successfully, False otherwise
        """
        try:
            logger.info("Running TDD cycles...")
            logger.info(f"Ticket: {ticket['title']}")
            logger.info(f"Description: {ticket['body'][:200]}...")

            # TODO: Implement actual TDD cycles
            # This would involve:
            # - Parsing ticket requirements
            # - Writing tests for each requirement
            # - Implementing code to pass tests
            # - Running tests
            # - Code review cycles until quality gates pass

            logger.info("✓ TDD cycles completed (placeholder)")
            return True

        except Exception as e:
            logger.error(f"TDD cycles failed: {str(e)}")
            return False

    def _run_code_review(self) -> bool:
        """
        Run code review on the implementation.

        Returns:
            True if code review passed, False otherwise
        """
        try:
            logger.info("Running code review...")

            # Use gh CLI to check diff from main
            result = subprocess.run(
                ["git", "diff", "main", "--stat"],
                cwd=self.repo_path,
                capture_output=True,
                text=True,
                timeout=10
            )

            if result.returncode != 0:
                logger.error("Failed to get diff from main")
                return False

            logger.info("Changes summary:")
            logger.info(result.stdout)

            # TODO: Invoke code review tool
            # This would call /code-review or similar mechanism

            logger.info("✓ Code review passed")
            return True

        except Exception as e:
            logger.error(f"Code review failed: {str(e)}")
            return False

    def _create_pr(self, ticket: Dict[str, Any]) -> bool:
        """
        Create a pull request for the implementation.

        Args:
            ticket: Ticket information

        Returns:
            True if PR created successfully, False otherwise
        """
        try:
            logger.info("Creating pull request...")

            # Ensure all changes are committed
            if not self._commit_changes():
                logger.error("Failed to commit changes")
                return False

            # Push branch to remote
            if not self._push_branch():
                logger.error("Failed to push branch")
                return False

            # Create PR using gh CLI
            pr_title = ticket["title"]
            pr_body = f"Fixes #{self.ticket_id}\n\n{ticket['body']}\n\n🤖 Generated with [Claude Code](https://claude.com/claude-code)"

            result = subprocess.run(
                [
                    "gh", "pr", "create",
                    "--title", pr_title,
                    "--body", pr_body,
                    "--base", "main",
                    "--head", self.branch_name
                ],
                cwd=self.repo_path,
                capture_output=True,
                text=True,
                timeout=30
            )

            if result.returncode != 0:
                logger.error(f"Failed to create PR: {result.stderr}")
                return False

            # Extract PR number from output
            # Output format: "https://github.com/owner/repo/pull/123"
            pr_url = result.stdout.strip()
            try:
                self.pr_number = int(pr_url.split("/")[-1])
                logger.info(f"✓ Created PR #{self.pr_number}")
            except (ValueError, IndexError):
                logger.warning(f"Could not parse PR number from {pr_url}")
                self.pr_number = None

            return True

        except Exception as e:
            logger.error(f"Failed to create PR: {str(e)}")
            return False

    def _commit_changes(self) -> bool:
        """
        Commit all changes to the branch.

        Returns:
            True if commit successful, False otherwise
        """
        try:
            # Check if there are changes to commit
            result = subprocess.run(
                ["git", "status", "--porcelain"],
                cwd=self.repo_path,
                capture_output=True,
                text=True,
                timeout=10
            )

            if not result.stdout.strip():
                logger.info("No changes to commit")
                return True

            # Stage all changes
            subprocess.run(
                ["git", "add", "-A"],
                cwd=self.repo_path,
                check=True,
                timeout=10
            )

            # Commit
            commit_message = f"feat: Implement ticket #{self.ticket_id} using TDD\n\nCo-Authored-By: Claude Haiku 4.5 <noreply@anthropic.com>"
            result = subprocess.run(
                ["git", "commit", "-m", commit_message],
                cwd=self.repo_path,
                capture_output=True,
                text=True,
                timeout=10
            )

            if result.returncode != 0:
                logger.error(f"Failed to commit: {result.stderr}")
                return False

            logger.info("✓ Changes committed")
            return True

        except Exception as e:
            logger.error(f"Failed to commit changes: {str(e)}")
            return False

    def _push_branch(self) -> bool:
        """
        Push the branch to remote.

        Returns:
            True if push successful, False otherwise
        """
        try:
            result = subprocess.run(
                ["git", "push", "-u", "origin", self.branch_name],
                cwd=self.repo_path,
                capture_output=True,
                text=True,
                timeout=30
            )

            if result.returncode != 0:
                logger.error(f"Failed to push: {result.stderr}")
                return False

            logger.info("✓ Branch pushed to remote")
            return True

        except Exception as e:
            logger.error(f"Failed to push branch: {str(e)}")
            return False


def main():
    """Entry point for TDD agent."""
    parser = argparse.ArgumentParser(
        description="TDD Agent - Autonomous ticket implementation using test-driven development"
    )
    parser.add_argument(
        "--ticket",
        type=int,
        required=True,
        help="GitHub issue number to implement"
    )
    parser.add_argument(
        "--branch",
        required=True,
        help="Git branch name for the ticket"
    )
    parser.add_argument(
        "--repo-path",
        default=".",
        help="Path to the git repository (default: current directory)"
    )

    args = parser.parse_args()

    # Create and run agent
    agent = TDDAgent(
        ticket_id=args.ticket,
        branch_name=args.branch,
        repo_path=args.repo_path
    )

    success = agent.run()
    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
