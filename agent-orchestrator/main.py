#!/usr/bin/env python3
"""
Agent Orchestrator Poller - Main Entry Point

Runs a single poll cycle. Invoked by systemd timer every 5 minutes.

Usage:
    main.py --repo <owner/repo> --state-file <path> --log-file <path> [--worktrees-dir <path>]
"""

import argparse
import os
import signal
from pathlib import Path
from poller import Poller
from error_recovery import ErrorRecoveryManager


class GracefulShutdownHandler:
    def __init__(self, recovery_mgr):
        self.recovery_mgr = recovery_mgr
        self.shutdown_requested = False
        signal.signal(signal.SIGTERM, self._handle_sigterm)
        signal.signal(signal.SIGINT, self._handle_sigint)

    def _handle_sigterm(self, signum, frame):
        self.shutdown_requested = True
        self.recovery_mgr.handle_graceful_shutdown()
        exit(0)

    def _handle_sigint(self, signum, frame):
        self.shutdown_requested = True
        self.recovery_mgr.handle_graceful_shutdown()
        exit(0)


def main():
    parser = argparse.ArgumentParser(description="Agent Orchestrator Poller")
    parser.add_argument("--repo", required=True, help="GitHub repo (owner/repo)")
    parser.add_argument("--state-file", default="/var/lib/agent-orchestrator/state.json",
                        help="Path to state.json")
    parser.add_argument("--log-file", default="/var/log/agent-orchestrator/poller.log",
                        help="Path to log file")
    parser.add_argument("--worktrees-dir", help="Path to worktrees directory for orphan cleanup")

    args = parser.parse_args()

    # Ensure directories exist
    Path(args.state_file).parent.mkdir(parents=True, exist_ok=True)
    Path(args.log_file).parent.mkdir(parents=True, exist_ok=True)

    # Setup graceful shutdown handler
    recovery_mgr = ErrorRecoveryManager(args.state_file, args.log_file, repo=args.repo)
    GracefulShutdownHandler(recovery_mgr)

    # Run poller
    poller = Poller(
        repo=args.repo,
        state_file=args.state_file,
        log_file=args.log_file,
        worktrees_dir=args.worktrees_dir
    )
    poller.run_once()


if __name__ == "__main__":
    main()
