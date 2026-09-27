#!/bin/bash
set -e

CHRONOS_ROOT="${CHRONOS_ROOT:-/repo/chronos-root}"
REPO_URL="${GITHUB_REPO_URL:?GITHUB_REPO_URL not set}"
GH_TOKEN="${GH_TOKEN:?GH_TOKEN not set}"
LOG_DIR="${LOG_DIR:-/var/log/agent-orchestrator}"

# Setup
mkdir -p "$LOG_DIR" .agent-state
cd "$CHRONOS_ROOT"

# Clone/update repo
if [ ! -d .git ]; then
  git clone --depth 1 "$REPO_URL" .
fi
git pull origin main

# Run poller
exec "${CHRONOS_ROOT}/agent-orchestrator/scripts/poller.sh" >> "${LOG_DIR}/poller.log" 2>&1
