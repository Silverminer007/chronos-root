# Code Review Agent Specification

## Overview

Implements a standalone Code Review Agent that reviews PRs and posts findings as GitHub comments.
This agent works in a poller-based orchestration workflow:

```
Poller → TDD Agent (creates PR) → Code Review Agent → [Fix if needed] → Poller
```

## Input/Output

**Input**: 
- Ticket number (e.g., `66`)
- Reads PR from GitHub API

**Output**:
- Posts GitHub comment with findings (or approval)
- Exits cleanly (poller determines next action)

## Key Features

### 1. PR Discovery
- Take ticket number as input
- Search GitHub for PR linked to this ticket
- Handle case where PR doesn't exist yet

### 2. Code Review
- Use `/code-review` skill to review changes
- Skill returns Standards and Spec findings
- Parse findings into structured format

### 3. Finding Format (parseable by Fixer)
```
Severity: CRITICAL|MAJOR|MINOR
File: src/auth.ts
Line: 42
Category: correctness|efficiency|style|security|test-coverage
Issue: Brief description
Suggestion: How to fix (if applicable)
```

### 4. GitHub Comment

**If no findings:**
```
✓ Code Review Passed

- Code Quality: ✓ (Standards compliance)
- Spec Compliance: ✓ (Acceptance criteria)  
- Test Coverage: ✓

Ready for review.
```

**If findings exist:**
```
⚠ Code Review Found Issues

[CRITICAL] src/auth.ts:42 (correctness)
- Missing null check before array access
→ Validate input before using array methods

[MAJOR] src/types.ts:15 (test-coverage)
- New type lacks test coverage
→ Add tests for new type definitions

Fix agent will attempt to resolve these issues.
```

### 5. Knowledge Base Integration
- Agent reads architectural patterns from KB
- Reports violations of documented patterns
- May identify new patterns worth documenting

### 6. Severity Levels
- **CRITICAL**: Code won't work / security risk
- **MAJOR**: Violates standards / incomplete implementation
- **MINOR**: Code smell / minor improvement

## Architecture

### Components

1. **CodeReviewAgent class**
   - `__init__(ticket_id, org, repo)`
   - `find_pr()` → gets PR number for ticket
   - `review_pr()` → runs `/code-review` skill
   - `parse_findings()` → converts to Finding objects
   - `post_comment()` → posts to GitHub
   - `run()` → orchestrates full workflow

2. **Finding dataclass**
   - `file`, `line`, `category`
   - `severity` (CRITICAL|MAJOR|MINOR)
   - `issue_summary`
   - `suggestion`

3. **Comments module**
   - `format_approval_comment()` → if clean
   - `format_findings_comment()` → if issues
   - `parse_findings_from_comment()` → read by Fixer

## Testing

Tests should validate:
1. PR discovery from ticket number
2. Finding parsing from code-review skill output
3. Severity classification
4. GitHub comment formatting
5. Knowledge Base pattern detection
6. Clean exit with status code

## Integration Points

- **Depends on**: `/code-review` skill, GitHub API, KB schema
- **Integrates with**: Poller (input), Fixer agent (reads findings), Knowledge Base
- **Status reporting**: Exit code, GitHub comment
