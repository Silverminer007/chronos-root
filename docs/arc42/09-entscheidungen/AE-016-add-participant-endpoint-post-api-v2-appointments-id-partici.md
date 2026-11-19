---
id: AE-016
title: "Add participant endpoint (POST /api/v2/appointments/{id}/participants/{userId})"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2026-10-04
quellen:
  - kb/adr/ADR-2026-10-04-001-add-participant-endpoint.md
verknuepft: []
---

# AE-016: Add participant endpoint (POST /api/v2/appointments/{id}/participants/{userId})

Quelle: `kb/adr/ADR-2026-10-04-001-add-participant-endpoint.md` (Status im ADR: accepted, Datum: 2026-10-04). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

Issue #116 requires implementing the `POST /api/v2/appointments/{id}/participants/{userId}` endpoint in the Rust backend to match the Java backend's API contract. This endpoint allows authorized users to manually add participants to appointments.

The Java implementation includes a friendship validation check (line 67-68 of `AuthorizationService`) that prevents users from adding non-friends. However, the specification explicitly states this check is "out-of-scope" for API parity, as it is business logic beyond the core endpoint contract.

Three key design decisions must be documented:
1. Authorization requirements (who can add participants)
2. Event structure (what event is fired when a participant is added)
3. Friendship validation (whether to replicate the Java check or skip it)

## Entscheidung

Implement the add-participant endpoint with **Java-identical API contract** (endpoint path, request/response format, authorization layer) but **skip the friendship validation** as specified in the ticket.

### 1. Authorization: RESPONSIBLE Role Only

Only users with the **RESPONSIBLE** role in the appointment can add new participants.

- **Check location**: Service layer (not handler), matching Java `AuthorizationService` pattern
- **Error response**: 400 Bad Request with message "Only RESPONSIBLE participants can add participants"
- **Applies to**: `POST /api/v2/appointments/{id}/participants/{userId}`

**Why**: RESPONSIBLE is the organizer role; only organizers should be able to invite participants. This mirrors the Java backend and ensures consistent permission model across all endpoints.

### 2. Event Structure: AppointmentParticipationAddedEvent …

## Konsequenzen

### Positive
- ✅ Endpoint matches Java API contract exactly (request/response format, authorization, events)
- ✅ Frontend can switch from Java to Rust backend without code changes
- ✅ Service layer handles authorization (decoupled from handlers), matching Java layered architecture
- ✅ Event-driven design enables extensibility (new listeners can be added without modifying the endpoint)

### Negative
- ❌ Friendship validation is not enforced (differs from Java business logic, though specification clarifies this is intentional)

### Trade-offs
- **API parity vs. feature parity**: Prioritize API parity (endpoint contract) over feature parity (optional business rules like friendship checks)
- **Flexibility**: Skipping the friendship check makes the endpoint more permissive, allowing clients to implement their own authorization logic
