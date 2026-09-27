# Agent Orchestrator

Autonomous agent-driven ticket implementation orchestrator. Polls GitHub for `ready-for-agent` labeled issues and coordinates agent-based implementation, code review, and CI monitoring.

## Architecture

### Components

| Component | Purpose |
|-----------|---------|
| `poller.py` | Main polling loop; discovers tickets, manages state |
| `state.py` | State file (JSON) management with atomic writes |
| `github_api.py` | GitHub API wrapper using `gh` CLI |
| `main.py` | Entry point; runs one poll cycle |
| `tests/` | Unit tests for all modules |

### Data Flow

```
systemd timer (every 5m)
    ↓
main.py --repo owner/repo --state-file /var/lib/... --log-file /var/log/...
    ↓
Poller.run_once()
    ├─ Load state.json
    ├─ Discover ready-for-agent issues via GitHub
    ├─ Apply in-progress label (claim ticket)
    ├─ Update state.json
    └─ Log to /var/log/agent-orchestrator/poller.log
```

## State File Format (state.json)

```json
{
  "last_poll": "2026-09-27T12:00:00Z",
  "active_agents": [
    {
      "ticket_id": 123,
      "agent_type": "spec-validator",
      "worktree_path": "/path/to/.worktrees/worktree-spec-123",
      "started_at": "2026-09-27T11:55:00Z",
      "status": "running"
    },
    {
      "ticket_id": 124,
      "agent_type": "tdd",
      "worktree_path": "/path/to/.worktrees/worktree-tdd-124",
      "started_at": "2026-09-27T11:50:00Z",
      "status": "waiting_for_ci",
      "ci_poll_rounds": 2,
      "pr_number": 45
    }
  ],
  "completed_tickets": [
    {
      "ticket_id": 120,
      "pr_number": 42,
      "status": "ready_for_review",
      "completed_at": "2026-09-27T10:30:00Z"
    }
  ]
}
```

### Fields

- **last_poll** (ISO 8601): Timestamp of last poll cycle
- **active_agents** (list): Agents currently running
  - ticket_id: GitHub issue number
  - agent_type: spec-validator, tdd, code-review, fixer
  - worktree_path: Local git worktree location
  - started_at: When agent was spawned
  - status: running, waiting_for_ci, etc.
  - (optional) ci_poll_rounds: Number of CI checks done
  - (optional) pr_number: GitHub PR number
- **completed_tickets** (list): Successfully completed tickets

## Usage

### Development

```bash
# Install dependencies (minimal; gh CLI must exist)
python3 -m pip install -r requirements.txt

# Run tests
python3 -m pytest tests/ -v
# Or
python3 tests/test_state.py
python3 tests/test_github.py
python3 tests/test_poller.py

# Run one poll cycle locally
python3 main.py \
    --repo Silverminer007/chronos-root \
    --state-file /tmp/state.json \
    --log-file /tmp/poller.log
```

### Deployment (systemd)

See `/etc/systemd/system/agent-orchestrator.timer` and `.service` for systemd integration. Poller runs every 5 minutes automatically.

```bash
# View logs
tail -f /var/log/agent-orchestrator/poller.log

# Check status
systemctl status agent-orchestrator.timer
systemctl status agent-orchestrator.service

# Manual trigger
systemctl start agent-orchestrator.service
```

## GitHub Integration

All interactions use the `gh` CLI:

```bash
# List ready-for-agent issues
gh issue list --repo owner/repo --label ready-for-agent --json number,title,body

# Add label
gh issue edit 123 --repo owner/repo --add-label in-progress

# Remove label
gh issue edit 123 --repo owner/repo --remove-label ready-for-agent

# Post comment
gh issue comment 123 --repo owner/repo --body "Agent started work"
```

**Requirement**: `gh` CLI must be installed and authenticated with repo access.

## Error Handling

| Scenario | Behavior |
|----------|----------|
| No ready-for-agent issues | Poller logs "Found 0 issues" and exits cleanly |
| GitHub API error | Error logged, poll cycle skips that issue |
| State file corruption | Load fails, state reverts to default (empty) |
| Missing gh CLI | GitHub API calls fail with clear error messages |

## Next Steps

After this scaffold is merged, the following tickets build on it:

- **#62** Agent spawning infrastructure (worktree mgmt)
- **#63** Knowledge base schema
- **#64** Spec validator agent
- **#65-67** TDD, code-review, fixer agents
- **#68-70** Concurrency, state recovery, CI polling
- **#71** Systemd timer and deployment
