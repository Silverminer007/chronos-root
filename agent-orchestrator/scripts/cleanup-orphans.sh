#!/bin/bash
# Cleanup orphaned worktrees and processes
#
# Usage:
#   cleanup-orphans.sh <worktrees-dir> <state-file> [--force]

set -e

WORKTREES_DIR="${1:-./.claude/worktrees}"
STATE_FILE="${2:-./state.json}"
FORCE_CLEANUP="${3:-}"

LOG_FILE="${LOG_FILE:-/var/log/agent-orchestrator/cleanup.log}"

# Ensure log directory exists
mkdir -p "$(dirname "$LOG_FILE")"

log() {
    local timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    echo "[$timestamp] $*" | tee -a "$LOG_FILE"
}

log "Starting orphaned worktree cleanup"

if [ ! -d "$WORKTREES_DIR" ]; then
    log "ERROR: Worktrees directory not found: $WORKTREES_DIR"
    exit 1
fi

if [ ! -f "$STATE_FILE" ]; then
    log "ERROR: State file not found: $STATE_FILE"
    exit 1
fi

# Extract active worktree paths from state.json
active_paths=$(grep -o '"worktree_path": "[^"]*"' "$STATE_FILE" 2>/dev/null | cut -d'"' -f4 | sort -u || true)

cleaned=0
now=$(date +%s)
timeout_seconds=21600  # 6 hours

cd "$WORKTREES_DIR" || exit 1

for wt_dir in worktree-*; do
    [ -d "$wt_dir" ] || continue

    # Check if path is in active list
    is_active=false
    while read -r active_path; do
        [ -z "$active_path" ] && continue
        if [ "$(cd "$WORKTREES_DIR" && pwd)/$wt_dir" = "$active_path" ]; then
            is_active=true
            break
        fi
    done <<< "$active_paths"

    if [ "$is_active" = false ]; then
        # Check if older than 6 hours
        mtime=$(stat -c %Y "$wt_dir" 2>/dev/null || echo 0)
        age=$((now - mtime))

        if [ $age -gt $timeout_seconds ] || [ "$FORCE_CLEANUP" = "--force" ]; then
            log "Cleaning up orphaned worktree: $wt_dir (age: $age seconds)"

            # Kill any processes in the worktree
            if command -v fuser &> /dev/null; then
                fuser -k -9 "$wt_dir" 2>/dev/null || true
            fi

            # Remove worktree using git
            if [ -d "$wt_dir/.git" ]; then
                git worktree remove "$wt_dir" --force 2>/dev/null || {
                    log "WARN: git worktree remove failed, force removing directory"
                    rm -rf "$wt_dir"
                }
            else
                rm -rf "$wt_dir"
            fi

            ((cleaned++))
        fi
    fi
done

log "Cleanup complete: removed $cleaned orphaned worktrees"
exit 0
