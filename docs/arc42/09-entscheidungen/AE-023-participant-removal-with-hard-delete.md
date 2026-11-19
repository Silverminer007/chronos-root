---
id: AE-023
title: "Participant removal with hard delete"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2026-10-14
quellen:
  - kb/adr/ADR-2026-10-14-017-participant-removal.md
verknuepft: []
---

# AE-023: Participant removal with hard delete

Quelle: `kb/adr/ADR-2026-10-14-017-participant-removal.md` (Status im ADR: accepted, Datum: 2026-10-14). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

Issue #118 requires implementing the `DELETE /api/v2/appointments/{id}/participants/{userId}` endpoint in the Rust backend for full API parity with the Java backend. This endpoint allows appointment organizers to remove participants from appointments. Six key decisions needed specification:

1. Hard delete vs. soft delete strategy
2. Event definition and payload structure
3. Authorization scope (creator-only vs. role-based)
4. Response format (empty body vs. deleted object)
5. Where to define the `AppointmentParticipationRemovedEvent`
6. API documentation approach

## Entscheidung

Implement participant removal as a hard-delete endpoint with creator-only authorization, firing a structured event for decoupled side-effects (push notifications, audit logging).

### 1. Delete Strategy: Hard Delete (DELETE FROM)

Perform a hard delete on the `appointment_participants` table, not soft delete.

**Rationale**:
- Matches Java backend behavior (Java uses hard delete)
- Simpler query logic: no need to filter soft-deleted participants
- Cleaner audit trail: participant removal is absolute, not a status flag
- Reduces data storage footprint over time

**Consequence**: A deleted participation record cannot be recovered from the database. If re-adding the same participant is needed, it requires a new INSERT via the add-participant endpoint.

### 2. Event Definition and Payload

Define `AppointmentParticipationRemovedEvent` in `events.rs` with three required fields: …

## Konsequenzen

### Positive

- ✅ **Full API parity**: Identical endpoint behavior between Java and Rust backends
- ✅ **Event-driven side-effects**: Push notifications and audit logging decouple via events
- ✅ **Simple authorization model**: Clear creator-only restriction prevents accidental privilege escalation
- ✅ **Clean database model**: Hard deletes avoid soft-delete filtering complexity
- ✅ **Testable**: Integration tests verify behavior against a real database

### Negative

- ❌ **Irreversible**: Hard-deleted participation records cannot be recovered
- ❌ **Limited authorization**: Cannot implement role-based removal without changing this decision
- ❌ **Event handler coordination**: Handlers observing the event must be deployed before endpoint goes live

### Trade-offs

- **Simplicity vs. Auditability**: Hard delete is simpler but offers less audit history than soft delete …
