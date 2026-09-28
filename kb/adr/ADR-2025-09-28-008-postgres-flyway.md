---
title: ADR-2025-09-28-008: PostgreSQL with Flyway for persistence
date: 2025-09-28
status: Accepted
supercedes: (none)
superceded_by: (none)
---

## Context

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

## Decision

Use **PostgreSQL** as the relational database with **Flyway** for schema versioning and migrations.

### Key Choices
- **PostgreSQL**: Mature, reliable, powerful (JSON, full-text search, JSONB)
- **Flyway**: Simple, version-based migrations, easy to understand
- **Hibernate Panache**: ORM bridges Java objects and PostgreSQL
- **Testcontainers**: Tests use containerized PostgreSQL (not in-memory H2)

## Consequences

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
- **Flexibility vs. safety**: ACID transactions are safe but add complexity
- **Power vs. simplicity**: PostgreSQL has many features, but we use only basics
- **Schema-first vs. schema-less**: Schema-first (PostgreSQL) catches errors early

## Alternative Considered

### MongoDB
```
Pros: Schemaless (easy to evolve), simple to get started
Cons: Weak ACID guarantees, relational queries are painful, eventual consistency issues
Decision: Rejected — ACID correctness is critical for scheduling app
```

## Flyway Migrations

### Format

Migrations live in `backend/src/main/resources/db/migration/` and follow the naming convention:

```
V<major>.<minor>.<patch>__<description>.sql
```

Examples:
```
V1.0.0__Initial_schema.sql
V1.1.0__Add_appointment_description.sql
V1.1.1__Add_participation_status_index.sql
V1.2.0__Create_reminder_table.sql
```

### Rules

1. **Never modify a migration after it's committed**
   - Once a migration is deployed to production, it's immutable
   - If there's a mistake, write a new migration that fixes it

2. **Forward-only changes**
   - Never `DROP TABLE` or `DELETE FROM` in production migrations
   - If you need to remove data, create a new migration

3. **Test locally first**
   - Run `./mvnw quarkus:dev`, check the schema, roll back, modify migration, re-run
   - Ensure migration works on both fresh install and existing databases

4. **Use transactions**
   - Wrap migration in `BEGIN; ... COMMIT;` (Flyway does this automatically)

5. **Document the why**
   ```sql
   -- V1.1.0__Add_description.sql
   -- Adding description field to appointments (feature: appointment details)
   ALTER TABLE appointment ADD COLUMN description TEXT;
   CREATE INDEX idx_appointment_title ON appointment(title); -- Support search feature
   ```

### Example Migrations

```sql
-- V1.0.0__Initial_schema.sql
CREATE TABLE "user" (
  id BIGSERIAL PRIMARY KEY,
  oidc_subject VARCHAR(255) NOT NULL UNIQUE,
  name VARCHAR(255) NOT NULL,
  email VARCHAR(255) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE appointment (
  id BIGSERIAL PRIMARY KEY,
  organizer_id BIGINT NOT NULL REFERENCES "user"(id),
  title VARCHAR(255) NOT NULL,
  start_time TIMESTAMP NOT NULL,
  end_time TIMESTAMP NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE participation (
  id BIGSERIAL PRIMARY KEY,
  appointment_id BIGINT NOT NULL REFERENCES appointment(id),
  user_id BIGINT NOT NULL REFERENCES "user"(id),
  status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(appointment_id, user_id)
);

CREATE INDEX idx_appointment_organizer ON appointment(organizer_id);
CREATE INDEX idx_participation_user ON participation(user_id);
```

## Testing with Flyway

### In Tests
Tests run with Flyway, then load testdata fixtures:

```bash
./mvnw test
```

Testdata fixtures live in `backend/src/test/resources/db/testdata/` and are loaded by the test setup:

```java
@QuarkusTest
public class AppointmentServiceTest {
  @Test
  void testFetchAppointments() {
    // Flyway has already run migrations and loaded testdata
    // Database is ready to test against
    
    var result = appointmentService.fetchAll();
    assertEquals(3, result.size());
  }
}
```

### Testdata Format

Simple SQL inserts:

```sql
-- testdata/appointments.sql
INSERT INTO "user" (id, oidc_subject, name, email) VALUES
  (1, 'user-001', 'Alice', 'alice@example.com'),
  (2, 'user-002', 'Bob', 'bob@example.com');

INSERT INTO appointment (id, organizer_id, title, start_time, end_time) VALUES
  (1, 1, 'Team Meeting', '2025-09-28 14:00:00', '2025-09-28 15:00:00'),
  (2, 1, 'Lunch', '2025-09-28 12:00:00', '2025-09-28 13:00:00');
```

## Configuration

```properties
# application.properties
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/chronos
quarkus.datasource.username=chronos
quarkus.datasource.password=chronos
quarkus.flyway.migrate-at-start=true
```

## References

- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [Flyway Documentation](https://flywaydb.org/documentation/)
- [Quarkus Flyway Guide](https://quarkus.io/guides/flyway)
- [C2025-09-28-004: Database migrations are forward-only](../constraints/C2025-09-28-004-forward-migrations.md)
- [ARCH-2025-09-28-004: Database schema management](../architecture/ARCH-2025-09-28-004-schema-management.md)
- Backend code: `backend/src/main/resources/db/migration/`

## Related Entries

- [ADR-2025-09-28-002: Quarkus 3](ADR-2025-09-28-002-quarkus.md)
- [ADR-2025-09-28-009: Panache for ORM](ADR-2025-09-28-009-panache.md)
