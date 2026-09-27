# Code-Review Agent Integration Test Specification

## Feature: TDD Loop with Code-Review Gate

### Seams Under Test

1. **Code-Review Spawning** — After a TDD cycle, spawn a code-review subagent on the diff
2. **Findings Collection** — Receive structured findings from code-review agent
3. **Loop Decision** — If findings exist, continue loop; if clean, break to PR phase
4. **Findings Reporting** — Report findings to user before next iteration

### Test Cases

#### Test 1: Spawn code-review agent after TDD cycle
- **Setup**: Complete one TDD cycle (test passes)
- **Action**: Call code-review subagent spawn
- **Expected**: Subagent is created with diff context
- **Pass Condition**: Subagent creation succeeds, receives accurate diff

#### Test 2: Collect clean findings (no issues)
- **Setup**: Code-review agent returns empty findings
- **Action**: Check findings result
- **Expected**: Loop breaks, proceed to PR creation
- **Pass Condition**: PR creation phase is triggered

#### Test 3: Collect non-empty findings
- **Setup**: Code-review agent returns findings list
- **Action**: Process findings
- **Expected**: Findings are reported, loop continues
- **Pass Condition**: Next TDD cycle is initiated with findings reported

#### Test 4: Structured findings format
- **Setup**: Code-review agent returns findings
- **Action**: Parse findings structure
- **Expected**: Findings have file, line, category, summary, details
- **Pass Condition**: All finding fields are present and accessible

### Implementation Priority

1. **Red**: Write test that fails because code-review spawning is not implemented
2. **Green**: Implement minimal code-review spawning in ticket-implementer agent
3. **Refactor**: Code review (structured findings, error handling)
4. **Repeat** for findings collection and loop decision logic
