import subprocess
import os
import logging
from pathlib import Path
from typing import Optional


logger = logging.getLogger(__name__)


class WorktreeManager:
    """Manages git worktrees for agent-based ticket implementation."""

    def __init__(self, base_path: str, repo_path: str):
        """
        Initialize WorktreeManager.

        Args:
            base_path: Directory where worktrees will be created
            repo_path: Path to the git repository
        """
        self.base_path = Path(base_path)
        self.repo_path = repo_path
        self.base_path.mkdir(parents=True, exist_ok=True)

    def get_worktree_path(self, ticket_id: int) -> str:
        """Get the path where a worktree for a ticket should be created."""
        return str(self.base_path / f"worktree-{ticket_id}")

    def create_worktree(
        self,
        ticket_id: int,
        branch_name: str,
        base_branch: str = "main"
    ) -> Optional[str]:
        """
        Create a new git worktree for an agent to work in.

        Args:
            ticket_id: GitHub issue number
            branch_name: Name for the new branch (e.g., "feature/62-agent-spawning")
            base_branch: Base branch to create worktree from (default: main)

        Returns:
            Path to the created worktree, or None if creation failed

        Raises:
            RuntimeError: If worktree creation fails
        """
        worktree_path = self.get_worktree_path(ticket_id)

        # Skip if worktree already exists
        if self.worktree_exists(worktree_path):
            logger.warning(f"Worktree at {worktree_path} already exists")
            return worktree_path

        logger.info(f"Creating worktree at {worktree_path} with branch {branch_name}")

        try:
            # Create the worktree with a new branch
            result = subprocess.run(
                ["git", "worktree", "add", "-b", branch_name, worktree_path, base_branch],
                cwd=self.repo_path,
                capture_output=True,
                text=True,
                timeout=30
            )

            if result.returncode != 0:
                raise RuntimeError(
                    f"Failed to create worktree: {result.stderr}"
                )

            logger.info(f"Successfully created worktree at {worktree_path}")
            return worktree_path

        except subprocess.TimeoutExpired:
            raise RuntimeError("Worktree creation timed out")
        except Exception as e:
            raise RuntimeError(f"Failed to create worktree: {str(e)}")

    def remove_worktree(self, worktree_path: str) -> bool:
        """
        Remove a git worktree.

        Args:
            worktree_path: Path to the worktree to remove

        Returns:
            True if successful, raises RuntimeError on failure

        Raises:
            RuntimeError: If worktree removal fails
        """
        if not self.worktree_exists(worktree_path):
            logger.warning(f"Worktree at {worktree_path} does not exist")
            return False

        logger.info(f"Removing worktree at {worktree_path}")

        try:
            result = subprocess.run(
                ["git", "worktree", "remove", worktree_path],
                cwd=self.repo_path,
                capture_output=True,
                text=True,
                timeout=30
            )

            if result.returncode != 0:
                raise RuntimeError(
                    f"Failed to remove worktree: {result.stderr}"
                )

            logger.info(f"Successfully removed worktree at {worktree_path}")
            return True

        except subprocess.TimeoutExpired:
            raise RuntimeError("Worktree removal timed out")
        except Exception as e:
            raise RuntimeError(f"Failed to remove worktree: {str(e)}")

    def worktree_exists(self, worktree_path: str) -> bool:
        """Check if a worktree exists at the given path."""
        return os.path.isdir(worktree_path)

    def list_worktrees(self):
        """List all worktrees under the base path."""
        try:
            result = subprocess.run(
                ["git", "worktree", "list"],
                cwd=self.repo_path,
                capture_output=True,
                text=True,
                timeout=10
            )

            if result.returncode != 0:
                logger.error(f"Failed to list worktrees: {result.stderr}")
                return []

            # Parse output: each line is "<path> <sha> [branch]"
            worktrees = []
            for line in result.stdout.strip().split('\n'):
                if line:
                    parts = line.split()
                    if len(parts) > 0:
                        worktrees.append(parts[0])

            return worktrees

        except Exception as e:
            logger.error(f"Failed to list worktrees: {str(e)}")
            return []
