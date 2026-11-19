---
id: AE-017
title: "RSVP Endpoint Design (POST /api/v2/appointments/{id}/participation)"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2026-10-04
quellen:
  - kb/adr/ADR-2026-10-04-012-rsvp-endpoint.md
verknuepft: []
---

# AE-017: RSVP Endpoint Design (POST /api/v2/appointments/{id}/participation)

Quelle: `kb/adr/ADR-2026-10-04-012-rsvp-endpoint.md` (Status im ADR: accepted, Datum: 2026-10-04). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

Issue #120 requires implementing the RSVP endpoint (`POST /api/v2/appointments/{id}/participation`) in the Rust backend, enabling users to respond to appointment invitations. The Java backend already implements this endpoint with specific authorization and validation rules. The Rust implementation must match the Java API contract exactly to maintain frontend compatibility.

Key design questions:
1. How should soft-deleted appointments (CANCELLED/DELETED status) be filtered when validating RSVP requests?
2. What participation status transitions are allowed (which values can a user transition to)?
3. What authorization model should be enforced — role-based access or explicit participation checks?
4. When should events fire relative to database commits?
5. How does the Rust implementation differ from the Java backend (e.g., no admin bypass for non-organizers)?

## Entscheidung

Implement the RSVP endpoint in Rust backend with **Java-identical behavior** for authorization, validation, and event firing. Defer admin checks to a future ticket.

### 1. Soft-Delete Filtering Strategy: Query-Level

When validating an RSVP request, check that the appointment exists and has status `PLANNED` (not CANCELLED or DELETED):

- **Query-level check**: the `rsvp_to_appointment` handler calls `service.change_participation_status()` which queries the participation record
- **Implicit filter**: soft-deleted appointments cannot have their participations updated because finding the appointment fails
- **Consequence**: returning 404 for RSVP to deleted/cancelled appointment, not 403, matching Java behavior

No business-logic filter is added; the database query implicitly enforces it.

### 2. Status Validation Scope: Immutable PENDING, Binary Transitions …

## Konsequenzen

### Positive

- ✅ **Frontend compatibility**: Frontend can RSVP without distinguishing Java vs. Rust backends
- ✅ **Authorization safety**: Only existing participants can RSVP, preventing unauthorized status changes
- ✅ **Event-driven notifications**: Push notifications and reminders react to status changes via events
- ✅ **Consistency**: Validation and error messages match Java backend exactly
- ✅ **Simplicity**: soft-delete filtering is implicit (query-level), no duplicate checks

### Negative

- ❌ **Admin bypass incomplete**: Rust backend does not yet implement admin bypass for authorization checks — admins must be explicit participants to RSVP. This is acceptable for now (deferred to future ticket).
- ❌ **Response usability**: Empty 200 OK response requires client to infer new status — could be improved with a future response body containing updated participation object. …
