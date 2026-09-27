#!/usr/bin/env python3
"""Agent Orchestrator - main entry point for one poll cycle.

Discovers ready-for-agent tickets on GitHub, spawns agents in isolated
worktrees, and tracks their progress. Invoked by systemd timer every 5 minutes.

Usage:
    main.py --repo owner/repo --repo-path /path/to/repo --state-file /path/state.json
"""

import argparse
import os
from pathlib import Path
from poller import Poller


def main():
    parser = argparse.ArgumentParser(description="Agent Orchestrator Poller")
    parser.add_argument("--repo", required=True, help="GitHub repo (owner/repo)")
    parser.add_argument("--repo-path", required=True, help="Local path to the git repository")
    parser.add_argument("--state-file", default="/var/lib/agent-orchestrator/state.json",
                        help="Path to state.json")
    parser.add_argument("--log-file", default="/var/log/agent-orchestrator/poller.log",
                        help="Path to log file")
    parser.add_argument("--worktree-base", default="/var/lib/agent-orchestrator/worktrees",
                        help="Base directory for worktrees")

    args = parser.parse_args()

    # Ensure directories exist
    Path(args.state_file).parent.mkdir(parents=True, exist_ok=True)
    Path(args.log_file).parent.mkdir(parents=True, exist_ok=True)
    Path(args.worktree_base).mkdir(parents=True, exist_ok=True)

    # Run poller
    poller = Poller(
        repo=args.repo,
        repo_path=args.repo_path,
        state_file=args.state_file,
        log_file=args.log_file,
        worktree_base=args.worktree_base
    )
    poller.run_once()


if __name__ == "__main__":
    main()
