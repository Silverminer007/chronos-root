# Agent Orchestrator: Spec Validator

The Spec Validator agent validates GitHub ticket specifications for completeness before implementation begins.

## Purpose

Ensures all tickets have sufficient detail to move forward with implementation by checking:
- **Acceptance Criteria**: Are they defined and testable?
- **Scope**: Is the scope clearly bounded?
- **Dependencies**: Are external dependencies documented?
- **Ambiguities**: Are there vague terms or missing details?
- **Testability**: Can acceptance criteria be verified?

## How It Works

The validator reads a GitHub issue, analyzes its specification, and either:
1. **Silent exit** — spec is adequate, poller proceeds to implementation
2. **Post comment** — spec is incomplete, posts questions to the issue and labels it `agent-needs-input`

Human must reply and re-apply `ready-for-agent` label for the poller to retry.

## Usage

### CLI

```bash
python3 spec_validator.py <issue_number>
```

Example:
```bash
python3 spec_validator.py 65
```

### In Code

```python
from spec_validator import SpecValidator

validator = SpecValidator()
exit_code = validator.validate_and_respond(65)
```

## Validation Logic

### Acceptance Criteria
- **Pass**: Has `## Acceptance Criteria`, `## Definition of Done`, or `## Requirements` section, OR has checklist items (- [ ])
- **Fail**: No structured acceptance criteria found

### Scope
- **Pass**: 
  - Has explicit `## Scope` section, OR
  - Contains scope keywords (scope, boundaries, out of scope, etc.), OR
  - Title indicates a bug fix (contains "fix", "bug", "resolve", etc.), OR
  - Title is descriptive (4+ words)
- **Fail**: Vague or unbounded work (body < 100 chars and no scope indicators)

### Dependencies
- **Checked only if**: Spec is complex (body > 300 chars) and no dependency section found
- **Required**: Explicit `## Dependencies` section or dependency keywords

### Ambiguous Terminology
- **Detected**: Vague words like "better", "faster", "improve", "widget", "stuff", "nice", etc. without metrics
- **Example**: "Make it faster" ❌ vs "Reduce latency from 500ms to <100ms" ✅

## Knowledge Base Integration

The validator can load a knowledge base (JSON) to understand domain-specific terminology and patterns:

```python
validator = SpecValidator(kb_path='knowledge-base.json')
```

If no KB is found, uses sensible defaults for the Chronos project.

## Testing

```bash
python3 -m pytest test_spec_validator.py -v
```

All 19 tests pass, covering:
- Basic validation logic
- GitHub integration (fetch, comment, label)
- Knowledge base loading
- CLI interface
- Edge cases

## GitHub Comment Format

When a spec is incomplete, the validator posts:

```markdown
## Ticket underspecified

The following questions should be answered before implementation:

1. Are there specific acceptance criteria that define 'done'? ...
2. What is the scope of this work? ...
3. [etc]

---
Please reply in the ticket comments, then re-apply the `ready-for-agent` label to retry.

*This check was performed by spec-validator agent.*
```

## Integration with Poller

The orchestrator poller spawns the validator in an isolated worktree:

```
worktree-spec-{ticket_id}/
└── spec_validator.py
```

Workflow:
1. Poller finds issue with `ready-for-agent` label
2. Spawns `spec_validator.py <issue_number>`
3. If validator posts comment → add `agent-needs-input` label
4. Human replies and re-applies `ready-for-agent`
5. Poller retries spec validation

## Files

- `spec_validator.py` — Main validator implementation
- `test_spec_validator.py` — 19 comprehensive tests
- `knowledge-base.json` (optional) — Domain patterns and terminology

## Extending the Validator

### Adding Custom Validation Rules

```python
class MyValidator(SpecValidator):
    def validate(self, spec):
        result = super().validate(spec)
        
        # Add custom check
        if some_custom_check(spec):
            result.questions.append("Custom question?")
            result.is_valid = False
        
        return result
```

### Using Domain-Specific Knowledge Base

```json
{
  "domain": "chronos",
  "patterns": {
    "acceptance_criteria": ["Acceptance Criteria", "Definition of Done"],
    "scope": ["Scope", "Boundaries"],
    "dependencies": ["Dependencies", "Related Issues"]
  },
  "required_sections": ["## Problem", "## Acceptance Criteria"],
  "keywords": {
    "technical": ["microservice", "RSVP", "pagination"],
    "vague": ["better", "nice"]
  }
}
```

## Architecture

- **ValidationResult** — Data class holding validation outcome and questions
- **SpecValidator** — Core validation engine
  - `validate(spec)` — Synchronous validation returning ValidationResult
  - `validate_and_respond(issue_number)` — End-to-end: fetch → validate → respond
  - `fetch_issue(number)` — GitHub API integration
  - `post_comment(number, result)` — Post questions to issue
  - `add_label(number, label)` — Add label to issue

## Error Handling

- Missing `gh` CLI → SystemExit(1) with stderr message
- Invalid issue number → SystemExit(1)
- GitHub API errors → SystemExit(1) with detailed error

## Future Enhancements

- [ ] Integration with Knowledge Base (#63) for domain-aware validation
- [ ] ML-based clarity scoring
- [ ] Automatic suggestions for ambiguous specs
- [ ] Custom validation rules per organization/team
- [ ] Metrics on spec quality over time
