---
name: Code Quality Gates
description: Automated code quality checks enforced on every PR (Checkstyle, SpotBugs, PMD)
category: constraints
last-updated: 2026-09-27
---

# Code Quality Gates

## Overview
The backend (`./mvnw verify`) enforces three code quality tools as mandatory gates. All PRs must pass before merging.

## Checkstyle

**Purpose**: Enforce consistent code style and formatting.

**Configuration**: `checkstyle.xml` (Google Java Style Guide)

**Common violations**:
- Indentation (4 spaces, no tabs)
- Line length > 100 characters
- Missing Javadoc on public methods
- Wildcard imports (`import java.util.*`)
- Whitespace around operators

**Run locally**:
```bash
./mvnw checkstyle:check
```

**Fix**:
```bash
./mvnw fmt:format
```

**Suppressions**: Rare suppressions allowed in `checkstyle-suppressions.xml` with justification.

## SpotBugs

**Purpose**: Detect common Java bugs and anti-patterns.

**Configuration**: `spotbugs-exclude.xml`

**Common issues**:
- Null pointer dereferences
- Unclosed resources
- Incorrect synchronization
- Unused fields/variables
- SQL injection (if applicable)

**Run locally**:
```bash
./mvnw spotbugs:check
```

**Examples of violations**:
```java
// ❌ Null dereference
String name = user.getName();
if (name != null) {
    System.out.println(name.toUpperCase());  // Could be null!
}

// ✅ Safe
String name = user.getName();
if (name != null && !name.isBlank()) {
    System.out.println(name.toUpperCase());
}

// ❌ Unclosed resource
InputStream is = new FileInputStream("file.txt");
String content = new String(is.readAllBytes());

// ✅ Safe
try (InputStream is = new FileInputStream("file.txt")) {
    String content = new String(is.readAllBytes());
}
```

## PMD

**Purpose**: Detect design flaws and improve code quality.

**Configuration**: `pmd-ruleset.xml`

**Common issues**:
- Cyclomatic complexity > 10
- God classes (too many methods)
- Empty catch blocks
- Duplicate code
- Too many parameters (> 7)

**Run locally**:
```bash
./mvnw pmd:check
```

**Examples**:
```java
// ❌ Too many parameters
public void createAppointment(String title, String desc, LocalDateTime start,
    LocalDateTime end, List<User> invitees, Location location, 
    boolean isPrivate, String category) { }

// ✅ Use a DTO
public void createAppointment(CreateAppointmentRequest request) { }

// ❌ High complexity
if (a) {
    if (b) { if (c) { if (d) { ... } } }  // Cyclomatic complexity 4+
}

// ✅ Lower complexity
if (isValidFor(a, b, c, d)) {
    // logic here
}
```

## CI Integration

All three tools run in CI on every PR:

```bash
./mvnw verify  # runs compile, test, checkstyle, spotbugs, pmd
```

**Failure stops the build.** No workarounds; fix the violations.

## Suppression Policy

Suppressions are allowed ONLY for:
1. False positives that cannot be fixed by code
2. Justified by code comment explaining why

**Example**:
```java
// SpotBugs: Ignore NP_NULL_ON_SOME_PATH (false positive; stream never null)
@SuppressFBWarnings("NP_NULL_ON_SOME_PATH")
public List<String> getNames() {
    return users.stream()
        .map(User::getName)
        .collect(Collectors.toList());
}
```

Never suppress without a comment.

## Rules

1. **All three must pass** before PR merge
2. **No exemptions**: No environment or branch exclusions
3. **Fix early**: Run `./mvnw verify` before pushing
4. **Suppress rarely**: Only for verified false positives
5. **No code duplication**: PMD checks this; refactor to reduce
