# Fixer Agent

Autonomous code remediation agent that fixes code review findings with test verification.

## Overview

The Fixer agent automatically remediates findings from the Code Review agent:
- **Parses findings** from PR comments with severity levels
- **Applies fixes** at specified line numbers with rollback on test failure
- **Verifies** each fix with automated tests
- **Commits** all fixes in a single clean commit
- **Reports** results via GitHub comment

## Quick Start

### Run Tests
```bash
cd fixer-agent
python3 -m pytest tests/ -v
```

### Components

1. **Finding Parser** (`src/finding_parser.py`)
   - Extracts code review findings from comments
   - Parses severity, file, line, summary, and suggestions
   - Sorts findings by priority

2. **Fix Applicator** (`src/fix_applicator.py`)
   - Applies fixes to source files
   - Handles line-level modifications
   - Supports test-based rollback
   - Implements retry logic

3. **Fixer Orchestrator** (`src/fixer_orchestrator.py`)
   - Coordinates the complete fixing workflow
   - Prioritizes findings by severity
   - Manages commits and GitHub interactions
   - Handles partial success scenarios

## Test Coverage

All components tested with TDD:
- **Finding Parser**: 6 tests (parsing, suggestions, severity ordering)
- **Fix Applicator**: 6 tests (apply, rollback, retry, error handling)
- **Fixer Orchestrator**: 7 tests (workflow, priorities, commits, comments)

Total: **19 passing tests**

## Architecture

```
┌─────────────────────────────────────┐
│  GitHub PR with Code Review Comment │
│  [CRITICAL] file:line — issue       │
└──────────────┬──────────────────────┘
               │
        ┌──────▼──────────┐
        │ Finding Parser  │
        │ (extract, sort) │
        └──────┬──────────┘
               │
        ┌──────▼──────────────────────┐
        │ Fixer Orchestrator           │
        │ (coordinate workflow)        │
        └──┬─────────────┬─────────────┘
           │             │
      ┌────▼───┐   ┌────▼──────────┐
      │  Fix   │   │  Test Runner  │
      │Applicator  │  (verify fix)  │
      └────┬───┘   └────┬──────────┘
           │             │
      ┌────▼─────────────▼────┐
      │ Git + GitHub Handler  │
      │ (commit & comment)    │
      └──────────────────────┘
```

## Dependencies

- **#62**: Worktree infrastructure (isolated workspace)
- **#63**: Knowledge Base schema  
- **#66**: Code Review agent (findings source)

## License

Part of the Chronos project
