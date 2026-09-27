import subprocess
import os
import logging
import signal
import time
from enum import Enum
from typing import Optional


logger = logging.getLogger(__name__)


class AgentType(Enum):
    """Types of agents that can be spawned."""
    TDD = "tdd"
    CODE_REVIEW = "code-review"
    SPEC_VALIDATOR = "spec-validator"
    FIXER = "fixer"


class AgentSpawner:
    """Spawns and manages agent processes in isolated worktrees."""

    def __init__(self, repo_path: str, claude_code_path: str = "claude"):
        """
        Initialize AgentSpawner.

        Args:
            repo_path: Path to the git repository
            claude_code_path: Path to the claude CLI executable
        """
        self.repo_path = repo_path
        self.claude_code_path = claude_code_path

    def spawn_agent(
        self,
        ticket_id: int,
        agent_type: AgentType,
        worktree_path: str,
        branch_name: str
    ) -> Optional[int]:
        """
        Spawn an agent in a worktree to work on a ticket.

        Args:
            ticket_id: GitHub issue number
            agent_type: Type of agent to spawn
            worktree_path: Path to the isolated worktree
            branch_name: Git branch name for the ticket

        Returns:
            Process ID of the spawned agent, or None if spawn failed
        """
        logger.info(
            f"Spawning {agent_type.value} agent for ticket #{ticket_id} "
            f"in {worktree_path}"
        )

        command = self._build_agent_command(
            ticket_id=ticket_id,
            agent_type=agent_type,
            worktree_path=worktree_path,
            branch_name=branch_name
        )

        try:
            # Spawn agent process in background
            process = subprocess.Popen(
                command,
                cwd=worktree_path,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True
            )

            logger.info(
                f"Successfully spawned {agent_type.value} agent with PID {process.pid}"
            )
            return process.pid

        except Exception as e:
            logger.error(f"Failed to spawn agent: {str(e)}")
            return None

    def _build_agent_command(
        self,
        ticket_id: int,
        agent_type: AgentType,
        worktree_path: str,
        branch_name: str
    ) -> list:
        """
        Build agent invocation command.

        NOTE: This is a placeholder for the actual agent invocation mechanism.
        Future implementation will integrate with proper agent spawning via
        Claude Code SDK or dedicated agent scripts.
        """

        agent_cmd_map = {
            AgentType.TDD: [self.claude_code_path, "agent:tdd"],
            AgentType.CODE_REVIEW: [self.claude_code_path, "agent:code-review"],
            AgentType.SPEC_VALIDATOR: [self.claude_code_path, "agent:spec-validator"],
            AgentType.FIXER: [self.claude_code_path, "agent:fixer"],
        }

        base_cmd = agent_cmd_map.get(agent_type)
        if not base_cmd:
            raise ValueError(f"Unknown agent type: {agent_type}")

        return base_cmd + [f"--ticket={ticket_id}", f"--branch={branch_name}"]

    def is_agent_running(self, pid: int) -> bool:
        """
        Check if an agent process is still running.

        Args:
            pid: Process ID to check

        Returns:
            True if process is running, False otherwise
        """
        try:
            # Sending signal 0 checks if process exists without sending signal
            os.kill(pid, 0)
            return True
        except ProcessLookupError:
            return False
        except Exception as e:
            logger.error(f"Failed to check process status: {str(e)}")
            return False

    def terminate_agent(self, pid: int) -> bool:
        """
        Terminate an agent process gracefully, then forcefully if needed.

        Args:
            pid: Process ID to terminate

        Returns:
            True if successfully terminated, False if process not found
        """
        if not self.is_agent_running(pid):
            logger.warning(f"Process {pid} is not running")
            return False

        logger.info(f"Terminating agent process {pid}")

        try:
            # Try SIGTERM first (graceful shutdown)
            os.kill(pid, signal.SIGTERM)

            # Check if process terminated within 2.5 seconds
            for _ in range(5):
                if not self.is_agent_running(pid):
                    logger.info(f"Process {pid} terminated successfully")
                    return True
                time.sleep(0.5)

            # Force kill if still running
            logger.warning(f"Process {pid} did not terminate, sending SIGKILL")
            os.kill(pid, signal.SIGKILL)
            return True

        except ProcessLookupError:
            logger.info(f"Process {pid} already terminated")
            return True
        except Exception as e:
            logger.error(f"Failed to terminate process {pid}: {str(e)}")
            return False

    def get_agent_info(self, pid: int) -> Optional[dict]:
        """
        Get runtime information about an agent process (memory usage, etc).

        Args:
            pid: Process ID

        Returns:
            Dictionary with process info, or None if process not found
        """
        try:
            with open(f"/proc/{pid}/status", 'r') as f:
                status = {}
                for line in f:
                    if line.startswith("VmRSS:"):
                        status["memory_kb"] = int(line.split()[1])
                return status
        except FileNotFoundError:
            return None
        except Exception as e:
            logger.error(f"Failed to get process info for {pid}: {str(e)}")
            return None
