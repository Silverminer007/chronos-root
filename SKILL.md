---
name: fixer-agent
description: Automatically remediates code review findings and re-attempts failed implementations with test verification
---

# Fixer Agent

The Fixer agent automatically remediates code review findings reported by the Code Review agent. It parses findings, applies fixes in priority order, verifies them with tests, and commits the results.

## Workflow Overview

1. **Parse Findings** — Extract code review findings from agent comment on PR
2. **Prioritize** — Sort findings by severity (CRITICAL → MAJOR → MINOR)
3. **Apply Fixes** — Modify code to resolve each finding
4. **Verify** — Run tests after each fix to ensure no regression
5. **Commit** — Create a single commit with all fixes
6. **Report** — Post summary comment on PR
7. **Cleanup** — Label PR on failure, exit with appropriate status code

## Input/Output

### Input
- **Ticket number** — GitHub ticket ID
- **Code review findings** — Extracted from Code Review agent comment
- **Source directory** — Path to the codebase

### Output
- **Single commit** with all fixes applied
- **GitHub comment** explaining what was fixed
- **Exit code** for poller (0 = success, non-zero = failure)
- **PR label** (`agent-failed`) if all fixes failed

## Finding Format

Findings are parsed from Code Review agent comments using this format:

```
[SEVERITY] FILE:LINE — Summary
Optional suggestion on next line (indented)
```

Example:
```
[CRITICAL] src/auth.py:15 — SQL injection vulnerability
[MAJOR] src/utils.py:88 — Missing null check
[MINOR] src/config.py:32 — Unused import
        Suggestion: Use isinstance() for type checking
```

**Severity levels:**
- `CRITICAL` — Security vulnerability or data loss risk
- `MAJOR` — Logic error or significant issue
- `MINOR` — Code quality or style issue

## Fix Application Process

For each finding (in priority order):

1. **Read file** at the specified line number
2. **Understand context** using Knowledge Base patterns if needed
3. **Apply fix** using a minimal change that resolves the issue
4. **Run tests** to verify no regression
5. **Handle results:**
   - If tests pass: move to next finding
   - If tests fail: revert fix and retry with different approach
   - After 3 failed attempts: skip finding and continue

## Single Commit Pattern

- DO NOT commit after each fix
- Apply all fixes first
- Commit once with standard format:
  ```
  fix: address code-review findings for #TICKET_ID
  
  Fixed:
  - issue 1
  - issue 2
  
  Co-Authored-By: Claude {model} <noreply@anthropic.com>
  ```

## CI Integration

After committing and pushing:
- Fixer agent exits immediately (does NOT wait for CI)
- Poller monitors CI status and handles retries
- If CI fails, poller may trigger another fix attempt

## Knowledge Base Integration

The Fixer agent:
- Reads KB patterns to understand fix strategies
- Documents workarounds used in fixes
- May suggest new KB patterns for future similar issues
- Updates KB as part of fix PR if needed

## Failure Handling

If fixes cannot be applied:

1. **Label PR** with `agent-failed`
2. **Post comment** with failure details
3. **Exit with failure code**
4. **Human intervention** required

Example failure comment:
```
❌ Could not apply any fixes.

**Unfixable issues:**
- `src/main.py:42` — Missing error handling

Please review manually or improve the KB patterns.
```

## Success Scenarios

### All Fixes Applied
```
✅ All findings fixed!

**Fixed issues:**
- `src/auth.py:15` — SQL injection vulnerability
- `src/utils.py:88` — Missing null check
```

### Partial Fixes
```
⚠️ Partial fixes applied.

**Fixed:**
- `src/auth.py:15` — SQL injection vulnerability

**Could not fix:**
- `src/config.py:32` — Unused import
```

## Acceptance Criteria

- [x] Agent correctly parses code review findings from comment
- [x] Agent applies fixes in priority order (CRITICAL → MAJOR → MINOR)
- [x] Agent runs tests after each fix to verify no regression
- [x] Agent creates single commit with all fixes
- [x] Agent posts clear summary of what was fixed
- [x] Agent handles fix failures gracefully (retry, then fail)
- [x] KB is updated with relevant patterns/constraints
- [x] Works in isolated worktree

## Architecture

```
fixer-agent/
├── src/
│   ├── finding_parser.py      — Parse findings from comments
│   ├── fix_applicator.py      — Apply and verify fixes
│   ├── fixer_orchestrator.py  — Coordinate fixing workflow
│   └── kb_interface.py        — Knowledge Base access
├── tests/
│   ├── test_finding_parser.py
│   ├── test_fix_applicator.py
│   └── test_fixer_orchestrator.py
└── main.py                     — Entry point
```

## Dependencies

- **#62** (Worktree infrastructure) — Isolated workspace management
- **#63** (KB schema) — Knowledge Base structure
- **#66** (Code review agent) — Findings to remediate

## Testing

All components tested with TDD (Test-Driven Development):
- Finding parser: 6 tests
- Fix applicator: 6 tests
- Fixer orchestrator: 7 tests

Run tests:
```bash
cd fixer-agent
python3 -m pytest tests/ -v
```

## When to Use This Skill

Use the Fixer agent when:
- Code Review agent has identified findings
- You want automatic remediation of code issues
- You need fast feedback with test verification
- You want a clean commit history (single commit for all fixes)
- You're in a CI/CD pipeline needing autonomous fixes
