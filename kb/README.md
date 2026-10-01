# Chronos Knowledge Base

A comprehensive, immutable knowledge base for understanding and extending the Chronos system. Built in arc42 style with Architecture Decision Records (ADRs), constraints, and architectural documentation.

## Philosophy

- **Atomic**: Each entry is self-contained and focused on a single concept
- **Immutable**: Entries are never modified after creation; only new entries can supersede them
- **Versioned**: Entries are numbered to establish order and enable referencing
- **Traceable**: Every entry documents its creation date and decision context

## Structure

### [ADRs (Architecture Decision Records)](adr/)

Major architectural decisions—*why* we chose a technology, pattern, or approach. Each ADR is immutable and captures the context, decision, and consequences at a point in time.

**Format**: AYYY-MM-DD-title.md (where Y is the year, MM month, DD day)
- ADR-2025-09-28-001: Use Nuxt 3 + Vue 3 for frontend
- ADR-2025-09-28-002: Use Quarkus for REST backend
- etc.

### [Constraints](constraints/)

Business, technical, and organizational constraints that shape architecture. These are immutable givens—they don't change; if they do, new constraint entries supersede them.

**Format**: CYYY-MM-DD-title.md

Examples:
- All UI text must be in German
- Must support OIDC authentication (Keycloak)
- Must support Web Push notifications
- Database migrations are forward-only

### [Architecture](architecture/)

Atomic architectural concepts and system design. Each entry documents one subsystem or cross-cutting concern.

**Format**: AYYY-MM-DD-title.md

Examples:
- Frontend authentication flow
- Service Worker token refresh mechanism
- Layered backend structure
- Database schema management
- Push notification pipeline

### [Patterns](patterns/)

Reusable design patterns, conventions, and anti-patterns specific to this codebase.

**Format**: PYYY-MM-DD-title.md

Examples:
- Principal context pattern
- CDI event decoupling
- DTO mapping pattern
- Store pattern (Pinia)
- Authorization checks in services

### [Glossary](glossary/)

Domain terminology and acronyms.

**Format**: Single glossary.md file, alphabetically sorted

## How to Navigate

### By Question Type

**"Why did we choose X?"** → See [ADRs](adr/)

**"What constraints do we operate under?"** → See [Constraints](constraints/)

**"How does the auth flow work?"** → See [Architecture](architecture/), look for "auth"

**"What's the pattern for handling side effects?"** → See [Patterns](patterns/), look for "CDI events"

**"What does OIDC mean?"** → See [Glossary](glossary/)

### By System

**Frontend**:
- ADR-2025-09-28-001: Use Nuxt 3 + Vue 3
- Architecture: Frontend authentication flow
- Architecture: Service Worker token refresh
- Patterns: Store pattern

**Backend**:
- ADR-2025-09-28-002: Use Quarkus
- ADR-2025-10-01-011: Soft-delete pattern for appointments
- Architecture: Layered backend structure
- Patterns: CDI events, DTO mapping, authorization
- Constraints: Forward-only migrations

**Infrastructure**:
- ADR-2025-09-28-003: Keycloak for OIDC
- ADR-2025-09-28-004: Web Push for notifications
- Constraints: VAPID requirement for Web Push

## Entry Format

### ADR Format
```markdown
---
title: ADR-2025-09-28-001: Use Nuxt 3 + Vue 3 for frontend
date: 2025-09-28
status: Accepted  # Accepted, Proposed, Deprecated
supercedes: (none)
superceded_by: (future ADR number if applicable)
---

## Context
Why did this decision come up? What forces were at play?

## Decision
What did we decide to do?

## Consequences
What will be the outcomes of this decision?
- Positive: ...
- Negative: ...
- Trade-offs: ...

## Alternatives Considered
What other options did we evaluate?

## References
- Link to related code
- Link to related ADRs
```

### Constraint Format
```markdown
---
title: C2025-09-28-001: All UI text in German
date: 2025-09-28
status: Active  # Active, Superseded
superceded_by: (future entry if applicable)
---

## Description
What is this constraint?

## Rationale
Why does this constraint exist?

## Implications
How does this shape our architecture/code?

## References
- Related ADRs
- Code examples
```

### Architecture Format
```markdown
---
title: ARCH-2025-09-28-001: Frontend Authentication Flow
date: 2025-09-28
component: Frontend
relates_to:
  - ADR-2025-09-28-001: Use Nuxt 3 + Vue 3
  - C2025-09-28-003: OIDC authentication required
---

## Overview
High-level summary of this architectural component.

## System Context
How does this fit into the broader system?

## Building Blocks
What are the key components/classes/files involved?

## Runtime Behavior
How does this work at runtime? (Include sequence diagrams if helpful)

## Data Flow
How does data move through this subsystem?

## Design Decisions
Why did we structure it this way?

## Implications
What does this architecture enable/prevent?

## Related Entries
- Links to related architecture docs
- Links to related patterns
- Links to related ADRs
```

### Pattern Format
```markdown
---
title: PATTERN-2025-09-28-001: CDI Event Decoupling
date: 2025-09-28
language: Java
applies_to:
  - Backend service layer
relates_to:
  - ADR-2025-09-28-002: Use Quarkus
---

## Intent
What problem does this pattern solve?

## Motivation
Why is this pattern valuable in this codebase?

## Structure
What are the key components?

## Implementation
How do you apply this pattern?

## Example
Code example from the codebase.

## Trade-offs
Benefits and drawbacks of this pattern.

## Related Patterns
Links to related patterns.
```

## Querying the KB

All entries include frontmatter (YAML) for easy indexing:
```bash
# Find all entries related to authentication
grep -r "auth" kb/ --include="*.md" | grep -i "title\|relates_to\|component"

# Find all deprecated ADRs
grep -r "status: Deprecated" kb/adr/

# Find all constraints that affect backend
grep -r "relates_to.*backend" kb/constraints/
```

## Adding a New Entry

1. Determine the entry type (ADR, Constraint, Architecture, Pattern)
2. Use today's date and increment the sequence number
3. Write the entry in the appropriate format
4. Update this README if adding a new category
5. Never edit existing entries—create a new one if something needs to change
6. If superseding an entry, update the old entry's `superceded_by` field (this is allowed for administrative tracking only)

## Entry Index

### Architecture Decision Records
- [ADR-2025-09-28-001: Use Nuxt 3 + Vue 3 for frontend](adr/ADR-2025-09-28-001-nuxt-vue.md)
- [ADR-2025-09-28-002: Use Quarkus 3 for REST backend](adr/ADR-2025-09-28-002-quarkus.md)
- [ADR-2025-09-28-003: Keycloak OIDC for authentication](adr/ADR-2025-09-28-003-keycloak.md)
- [ADR-2025-09-28-004: Web Push for notifications](adr/ADR-2025-09-28-004-web-push.md)
- [ADR-2025-09-28-005: Service Worker for token refresh](adr/ADR-2025-09-28-005-service-worker.md)
- [ADR-2025-09-28-006: Pinia for frontend state management](adr/ADR-2025-09-28-006-pinia.md)
- [ADR-2025-09-28-007: Layered architecture for backend](adr/ADR-2025-09-28-007-layered-backend.md)
- [ADR-2025-09-28-008: PostgreSQL with Flyway for persistence](adr/ADR-2025-09-28-008-postgres-flyway.md)
- [ADR-2025-09-28-009: Panache for ORM](adr/ADR-2025-09-28-009-panache.md)
- [ADR-2025-09-28-010: Server routes as auth proxy](adr/ADR-2025-09-28-010-server-routes-proxy.md)
- [ADR-2025-10-01-011: Soft-delete pattern for appointment lifecycle](adr/ADR-2025-10-01-011-soft-delete.md)

### Constraints
- [C2025-09-28-001: All UI text in German](constraints/C2025-09-28-001-german-ui.md)
- [C2025-09-28-002: OIDC authentication required](constraints/C2025-09-28-002-oidc-required.md)
- [C2025-09-28-003: Web Push VAPID requirement](constraints/C2025-09-28-003-vapid.md)
- [C2025-09-28-004: Database migrations are forward-only](constraints/C2025-09-28-004-forward-migrations.md)
- [C2025-09-28-005: No direct backend calls from frontend](constraints/C2025-09-28-005-no-direct-backend-calls.md)
- [C2025-09-28-006: SSR disabled (SPA only)](constraints/C2025-09-28-006-spa-only.md)

### Architecture
- [ARCH-2025-09-28-001: Frontend authentication flow](architecture/ARCH-2025-09-28-001-auth-flow.md)
- [ARCH-2025-09-28-002: Service Worker token refresh](architecture/ARCH-2025-09-28-002-service-worker.md)
- [ARCH-2025-09-28-003: Layered backend structure](architecture/ARCH-2025-09-28-003-layered-backend.md)
- [ARCH-2025-09-28-004: Database schema management](architecture/ARCH-2025-09-28-004-schema-management.md)
- [ARCH-2025-09-28-005: Push notification pipeline](architecture/ARCH-2025-09-28-005-notifications.md)
- [ARCH-2025-09-28-006: Authorization and security](architecture/ARCH-2025-09-28-006-authorization.md)

### Patterns
- [PATTERN-2025-09-28-001: Principal context pattern](patterns/PATTERN-2025-09-28-001-principal-context.md)
- [PATTERN-2025-09-28-002: CDI event decoupling](patterns/PATTERN-2025-09-28-002-cdi-events.md)
- [PATTERN-2025-09-28-003: DTO mapping pattern](patterns/PATTERN-2025-09-28-003-dto-mapping.md)
- [PATTERN-2025-09-28-004: Pinia store pattern](patterns/PATTERN-2025-09-28-004-store-pattern.md)
- [PATTERN-2025-09-28-005: Authorization checks in services](patterns/PATTERN-2025-09-28-005-authorization.md)
- [PATTERN-2025-09-28-006: Testdata fixtures](patterns/PATTERN-2025-09-28-006-testdata.md)

### Glossary
- [Glossary](glossary/glossary.md)

---

**Last updated**: 2025-10-01
