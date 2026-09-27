#!/bin/bash
# Agent Orchestrator Poller - Main Script
#
# Coordinates timeout detection, crash recovery, and orphan cleanup
# Invoked by systemd timer every 5 minutes
#
# Usage:
#   poller.sh --repo <owner/repo> --state-file <path> --log-file <path> \
#     [--worktrees-dir <path>] [--cleanup-interval <minutes>]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO=""
STATE_FILE="/var/lib/agent-orchestrator/state.json"
LOG_FILE="/var/log/agent-orchestrator/poller.log"
WORKTREES_DIR="${HOME}/.claude/worktrees"
CLEANUP_INTERVAL=30  # Run cleanup every 30 minutes
LAST_CLEANUP_FILE="${STATE_FILE%.json}.last_cleanup"

log() {
    local timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    echo "[$timestamp] $*" | tee -a "$LOG_FILE"
}

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --repo) REPO="$2"; shift 2 ;;
        --state-file) STATE_FILE="$2"; shift 2 ;;
        --log-file) LOG_FILE="$2"; shift 2 ;;
        --worktrees-dir) WORKTREES_DIR="$2"; shift 2 ;;
        --cleanup-interval) CLEANUP_INTERVAL="$2"; shift 2 ;;
        *) echo "Unknown option: $1"; exit 1 ;;
    esac
done

if [ -z "$REPO" ]; then
    echo "Error: --repo is required"
    exit 1
fi

log "Poller started (repo: $REPO)"

# Ensure directories exist
mkdir -p "$(dirname "$STATE_FILE")"
mkdir -p "$(dirname "$LOG_FILE")"

# Run Python poller with error recovery
cd "$(dirname "$SCRIPT_DIR")"
python3 main.py \
    --repo "$REPO" \
    --state-file "$STATE_FILE" \
    --log-file "$LOG_FILE" \
    --worktrees-dir "$WORKTREES_DIR"

POLLER_EXIT=$?
if [ $POLLER_EXIT -ne 0 ]; then
    log "ERROR: Python poller failed with exit code $POLLER_EXIT"
    exit $POLLER_EXIT
fi

# Periodically cleanup orphaned worktrees
should_cleanup=false
if [ ! -f "$LAST_CLEANUP_FILE" ]; then
    should_cleanup=true
else
    last_cleanup=$(cat "$LAST_CLEANUP_FILE")
    minutes_since=$(($(date +%s) - last_cleanup))
    if [ $((minutes_since / 60)) -ge $CLEANUP_INTERVAL ]; then
        should_cleanup=true
    fi
fi

if [ "$should_cleanup" = true ]; then
    log "Running orphan cleanup cycle"
    bash "$SCRIPT_DIR/cleanup-orphans.sh" "$WORKTREES_DIR" "$STATE_FILE"
    echo "$(date +%s)" > "$LAST_CLEANUP_FILE"
else
    log "Skipping cleanup (not yet due)"
fi

log "Poller cycle complete"
exit 0
