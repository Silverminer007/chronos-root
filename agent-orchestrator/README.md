# Agent Orchestrator

Autonomous agent-driven ticket implementation orchestrator. Polls GitHub for `ready-for-agent` labeled issues, creates isolated worktrees, spawns agents, and coordinates their work.

## Architecture

### Components

| Component | Purpose |
|-----------|---------|
| `poller.py` | Main polling loop; discovers tickets, spawns agents |
| `worktree_manager.py` | Git worktree creation and lifecycle management |
| `agent_spawner.py` | Spawns agent processes (TDD, code-review, spec-validator, fixer) |
| `state.py` | State file (JSON) management with atomic writes |
| `github_api.py` | GitHub API wrapper using `gh` CLI |
| `main.py` | Entry point; runs one poll cycle |
| `tests/` | Unit and integration tests for all modules |

### Data Flow

```
systemd timer (every 5m)
    ↓
main.py --repo owner/repo --repo-path /path/to/repo --state-file ... --log-file ... --worktree-base ...
    ↓
Poller.run_once()
    ├─ Load state.json
    ├─ Discover ready-for-agent issues via GitHub
    ├─ For each new ticket:
    │   ├─ Create isolated git worktree
    │   ├─ Spawn agent process (TDD, code-review, etc.)
    │   ├─ Apply in-progress label
    │   └─ Track agent in state.json
    ├─ Update state.json with active agents
    └─ Log to /var/log/agent-orchestrator/poller.log
```

## State File Format (state.json)

```json
{
  "last_poll": "2026-09-27T12:00:00Z",
  "active_agents": [
    {
      "ticket_id": 62,
      "agent_type": "tdd",
      "worktree_path": "/var/lib/agent-orchestrator/worktrees/worktree-62",
      "started_at": "2026-09-27T11:55:00Z",
      "status": "running",
      "pid": 12345,
      "ci_poll_rounds": 0,
      "pr_number": null
    },
    {
      "ticket_id": 63,
      "agent_type": "code-review",
      "worktree_path": "/var/lib/agent-orchestrator/worktrees/worktree-63",
      "started_at": "2026-09-27T11:50:00Z",
      "status": "waiting_for_ci",
      "pid": 12346,
      "ci_poll_rounds": 2,
      "pr_number": 123
    }
  ],
  "completed_tickets": [
    {
      "ticket_id": 60,
      "pr_number": 120,
      "status": "ready_for_review",
      "completed_at": "2026-09-27T10:30:00Z"
    }
  ]
}
```

### Fields

- **last_poll** (ISO 8601): Timestamp of last poll cycle
- **active_agents** (list): Agents currently running
  - `ticket_id`: GitHub issue number
  - `agent_type`: tdd, code-review, spec-validator, fixer
  - `worktree_path`: Isolated git worktree location
  - `started_at`: When agent was spawned (ISO 8601)
  - `status`: running, waiting_for_ci, completed, failed
  - `pid`: Process ID of agent
  - `ci_poll_rounds`: Number of CI checks completed
  - `pr_number`: GitHub PR number (if applicable)
- **completed_tickets** (list): Successfully completed tickets

## Worktree and Agent Spawning

### WorktreeManager

Manages the lifecycle of git worktrees for agent isolation:

```python
from worktree_manager import WorktreeManager

manager = WorktreeManager(
    base_path="/var/lib/agent-orchestrator/worktrees",
    repo_path="/path/to/chronos-root"
)

# Create a worktree for ticket #62
worktree_path = manager.create_worktree(
    ticket_id=62,
    branch_name="feature/62-agent-spawning"
)

# Remove worktree after agent completes
manager.remove_worktree(worktree_path)
```

### AgentSpawner

Spawns agent processes in isolated worktrees:

```python
from agent_spawner import AgentSpawner, AgentType

spawner = AgentSpawner(
    repo_path="/path/to/chronos-root",
    claude_code_path="/usr/local/bin/claude"
)

# Spawn a TDD agent
pid = spawner.spawn_agent(
    ticket_id=62,
    agent_type=AgentType.TDD,
    worktree_path="/var/lib/agent-orchestrator/worktrees/worktree-62",
    branch_name="feature/62-agent-spawning"
)

# Check if agent is running
is_running = spawner.is_agent_running(pid)

# Terminate agent if needed
spawner.terminate_agent(pid)
```

## Usage

### Development

```bash
# Install dependencies (minimal; gh CLI must exist)
python3 -m pip install -r requirements.txt

# Run tests
python3 -m pytest tests/ -v

# Run one poll cycle locally
python3 main.py \
    --repo Silverminer007/chronos-root \
    --repo-path /home/vagrant/git/chronos-root \
    --state-file /tmp/state.json \
    --log-file /tmp/poller.log \
    --worktree-base /tmp/worktrees
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

## Agent Types

The orchestrator supports spawning different types of agents:

| Agent Type | Purpose | Spawns via |
|---|---|---|
| **TDD** | Test-driven development agent | `/tdd --ticket=N --branch=name` |
| **CODE_REVIEW** | Code review agent | `/code-review --ticket=N --branch=name` |
| **SPEC_VALIDATOR** | Spec validation agent | `/spec-validate --ticket=N --branch=name` |
| **FIXER** | Issue fixer agent | `/fixer --ticket=N --branch=name` |

## Error Handling

| Scenario | Behavior |
|----------|----------|
| No ready-for-agent issues | Poller logs "Found 0 issues" and exits cleanly |
| Worktree creation fails | Error logged, agent spawn skipped for ticket |
| Agent spawn fails | Error logged, no agent tracked in state |
| GitHub API error | Error logged, poll cycle skips that issue |
| State file corruption | Load fails, state reverts to default (empty) |
| Missing gh CLI | GitHub API calls fail with clear error messages |
| Process spawn timeout | RuntimeError raised, logged, and caught |

## Testing

The test suite includes:

- **Unit tests** for WorktreeManager (create, remove, existence checks)
- **Unit tests** for AgentSpawner (spawn, status, terminate)
- **Integration tests** for Poller (discovery, spawning, state tracking)
- **Mock-based tests** ensuring isolated, deterministic test runs

Run tests:

```bash
python3 -m pytest tests/ -v
python3 -m pytest tests/test_worktree_manager.py -v
python3 -m pytest tests/test_agent_spawner.py -v
python3 -m pytest tests/test_poller_integration.py -v
```

## Next Steps

After this implementation is merged, the following tickets build on it:

- **#63** Knowledge base schema
- **#64** Spec validator agent
- **#65-67** TDD, code-review, fixer agents (full implementations)
- **#68-70** Concurrency, state recovery, CI polling
- **#71** Systemd timer and deployment
