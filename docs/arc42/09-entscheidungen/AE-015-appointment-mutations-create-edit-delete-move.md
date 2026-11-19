---
id: AE-015
title: "Appointment mutations (create/edit/delete/move)"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2026-10-03
quellen:
  - kb/adr/ADR-2026-10-03-011-appointment-mutations.md
verknuepft: []
---

# AE-015: Appointment mutations (create/edit/delete/move)

Quelle: `kb/adr/ADR-2026-10-03-011-appointment-mutations.md` (Status im ADR: accepted, Datum: 2026-10-03). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

Issue #91 requires implementing appointment mutations (POST/PATCH/DELETE endpoints) in the Rust backend, mirroring the Java backend's behavior. The Rust schema and DTOs differ in field naming and response structure from the Java backend, creating risk of divergent API contracts and breaking frontend compatibility.

Eight key decisions must be documented:
1. Field naming across request/response DTOs
2. DateTime format for serialization
3. Nested response arrays (participants, messages, group_participants)
4. Status field (PLANNED/CANCELLED/DELETED) for soft deletes
5. Creator auto-participation on appointment creation
6. Validation rules (minimal server-side checks)
7. Authorization (role-based access control)
8. Event firing timing relative to database commit

## Entscheidung

Implement appointment mutations in Rust backend with **Java-identical API contract** to enable frontend to switch backends without code changes.

### 1. Field Naming: Java-Identical

Use `name`, `venue`, `description`, `start`, `end`, `status`, `minimal_attendees` in all request/response DTOs, matching the Java `AppointmentDto` exactly.

- Requests: field names match Java
- Responses: field names match Java
- Database columns may differ (migrations add new columns, don't edit existing ones)
- Applies to both mutation endpoints (PATCH, POST, DELETE) and read-only endpoints (GET) from #29

### 2. DateTime Format: ISO-8601 Strings

Request and response timestamps use ISO-8601 string format (`"2026-09-15T14:00:00Z"`), not structured DateTime objects.

- Parse: `chrono::DateTime<Utc>::from_str("2026-09-15T14:00:00Z")` …

## Konsequenzen

### Positive

- ✅ **Frontend compatibility**: Frontend can switch from Java to Rust backend without code changes
- ✅ **API consistency**: Identical contracts across Java and Rust backends reduce bugs
- ✅ **Event-driven side-effects**: Push notifications and reminders decouple via events, not synchronous calls
- ✅ **Audit trail**: Soft deletes (CANCELLED/DELETED status) preserve history for compliance
- ✅ **Transactional safety**: Events only fire after commit, preventing orphaned side-effects
- ✅ **Authorization separation**: Service layer checks prevent bypassing via malformed requests

### Negative

- ❌ **Database schema evolution**: Field renames (`title` → `name`) require careful migration timing
- ❌ **Query complexity**: Filters must exclude soft-deleted rows (null checks on status column) …
