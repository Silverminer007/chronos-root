---
id: AE-018
title: "Appointment Groups: Add group endpoint"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2026-10-05
quellen:
  - kb/adr/ADR-2026-10-05-012-appointment-groups-add-group.md
verknuepft: []
---

# AE-018: Appointment Groups: Add group endpoint

Quelle: `kb/adr/ADR-2026-10-05-012-appointment-groups-add-group.md` (Status im ADR: accepted, Datum: 2026-10-05). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

Issue #122 requires implementing the `POST /api/v2/appointments/{id}/groups/{groupId}` endpoint in the Rust backend to achieve API parity with the Java backend. This endpoint allows appointment creators to add groups to appointments, automatically adding current group members as appointment participants.

The implementation decision points are:
1. Authorization model (who can add groups)
2. Validation rules (group existence, soft-delete filtering, duplicate checks)
3. Role and status assignment for group participants
4. Event publishing (AppointmentGroupAddedEvent)
5. Database schema (appointment_groups table)
6. Future member auto-addition scope (out of scope for Rust)

## Entscheidung

Implement `POST /api/v2/appointments/{id}/groups/{groupId}` in Rust backend with **full authorization enforcement and event-driven side-effects**, matching Java backend behavior exactly for API parity.

### 1. Authorization: Creator-Only Access

Only the **appointment creator** can add groups to an appointment.

- Service layer validates `appointment.creator_id == actor_id` before mutation
- Non-creators receive `ValidationError("Only the appointment creator can add groups")`
- Authorization check happens before any database writes (fail-safe)

Matches Java `AppointmentParticipationResource.addGroup()` at line 182-189.

### 2. Validation Rules

**Pre-mutation validation** (all checks before database write):

- **Appointment existence**: Fetches appointment by ID; returns `NotFound` if missing
- **Group existence**: Checks group exists and is **not soft-deleted** (deleted_at IS NULL) …

## Konsequenzen

### Positive

- ✅ **API parity with Java**: Frontend can switch backends without code changes
- ✅ **Authorization enforcement**: Service layer prevents unauthorized mutations
- ✅ **Soft-delete compliance**: Deleted groups cannot be added to appointments
- ✅ **Duplicate prevention**: Cannot add the same group twice
- ✅ **Event-driven audit trail**: All mutations fire events persisted to events table
- ✅ **Transaction safety**: Events only fire after database commit succeeds

### Negative

- ❌ **No automatic new member addition**: Group members added after group creation are not automatically added to appointment
- ❌ **Query complexity**: Must filter soft-deleted groups when validating (added deleted_at check)

### Trade-offs

- **Simplicity vs. completeness**: Skipping auto-addition of future group members reduces complexity but deviates from Java behavior …
