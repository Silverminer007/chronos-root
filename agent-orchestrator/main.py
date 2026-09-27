#!/usr/bin/env python3
"""
Agent Orchestrator Poller - Main Entry Point

Runs a single poll cycle. Invoked by systemd timer every 5 minutes.

Usage:
    main.py --repo <owner/repo> --state-file <path> --log-file <path>
"""

import argparse
import os
from pathlib import Path
from poller import Poller


def main():
    parser = argparse.ArgumentParser(description="Agent Orchestrator Poller")
    parser.add_argument("--repo", required=True, help="GitHub repo (owner/repo)")
    parser.add_argument("--state-file", default="/var/lib/agent-orchestrator/state.json",
                        help="Path to state.json")
    parser.add_argument("--log-file", default="/var/log/agent-orchestrator/poller.log",
                        help="Path to log file")

    args = parser.parse_args()

    # Ensure directories exist
    Path(args.state_file).parent.mkdir(parents=True, exist_ok=True)
    Path(args.log_file).parent.mkdir(parents=True, exist_ok=True)

    # Run poller
    poller = Poller(
        repo=args.repo,
        state_file=args.state_file,
        log_file=args.log_file
    )
    poller.run_once()


if __name__ == "__main__":
    main()
