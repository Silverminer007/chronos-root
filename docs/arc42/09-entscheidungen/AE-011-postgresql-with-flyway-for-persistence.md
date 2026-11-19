---
id: AE-011
title: "PostgreSQL with Flyway for persistence"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2025-09-28
quellen:
  - kb/adr/ADR-2025-09-28-008-postgres-flyway.md
verknuepft: []
---

# AE-011: PostgreSQL with Flyway for persistence

Quelle: `kb/adr/ADR-2025-09-28-008-postgres-flyway.md` (Status im ADR: accepted, Datum: 2025-09-28). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

Chronos needed a relational database for appointment data with:
- ACID guarantees (correctness matters for scheduling)
- Complex queries (joins across appointments, users, participations)
- Schema evolution (new features require schema changes)
- Testability (integration tests need real DB)

Evaluated options:
1. **PostgreSQL + Flyway** — open-source, reliable, battle-tested migrations
2. **MySQL + Liquibase** — solid but less powerful than PostgreSQL
3. **MongoDB** — schemaless, but wrong for this use case (ACID less guaranteed, relational queries are painful)
4. **SQLite** — simple, but not suitable for production multi-user app

## Entscheidung

Use **PostgreSQL** as the relational database with **Flyway** for schema versioning and migrations.

### Key Choices
- **PostgreSQL**: Mature, reliable, powerful (JSON, full-text search, JSONB)
- **Flyway**: Simple, version-based migrations, easy to understand
- **Hibernate Panache**: ORM bridges Java objects and PostgreSQL
- **Testcontainers**: Tests use containerized PostgreSQL (not in-memory H2)

## Konsequenzen

### Positive
- ✅ **ACID guarantees**: Transactions ensure data consistency
- ✅ **Relational model**: Natural fit for appointments, users, participations
- ✅ **Complex queries**: JOINs, subqueries, aggregations all supported
- ✅ **Flyway migrations**: Simple, version-controlled schema changes
- ✅ **Open-source**: No licensing costs, deployable anywhere
- ✅ **Testability**: Testcontainers can spin up real PostgreSQL for tests
- ✅ **Mature ecosystem**: Excellent documentation, large community
- ✅ **Observability**: Built-in logging, query performance analysis

### Negative
- ❌ **Operational complexity**: Database administration required
- ❌ **Migration risk**: Schema changes must be tested carefully
- ❌ **Performance**: Very large tables (100M+ rows) require tuning
- ❌ **Schema lock**: Altering columns can lock table (mitigated by careful migrations)

### Trade-offs
- **Flexibility vs …
