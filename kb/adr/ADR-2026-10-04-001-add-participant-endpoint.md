---
title: ADR-2026-10-04-001: Add participant endpoint (POST /api/v2/appointments/{id}/participants/{userId})
date: 2026-10-04
status: Accepted
supercedes: (none)
superceded_by: (none)
---

## Context

Issue #116 requires implementing the `POST /api/v2/appointments/{id}/participants/{userId}` endpoint in the Rust backend to match the Java backend's API contract. This endpoint allows authorized users to manually add participants to appointments.

The Java implementation includes a friendship validation check (line 67-68 of `AuthorizationService`) that prevents users from adding non-friends. However, the specification explicitly states this check is "out-of-scope" for API parity, as it is business logic beyond the core endpoint contract.

Three key design decisions must be documented:
1. Authorization requirements (who can add participants)
2. Event structure (what event is fired when a participant is added)
3. Friendship validation (whether to replicate the Java check or skip it)

## Decision

Implement the add-participant endpoint with **Java-identical API contract** (endpoint path, request/response format, authorization layer) but **skip the friendship validation** as specified in the ticket.

### 1. Authorization: RESPONSIBLE Role Only

Only users with the **RESPONSIBLE** role in the appointment can add new participants.

- **Check location**: Service layer (not handler), matching Java `AuthorizationService` pattern
- **Error response**: 400 Bad Request with message "Only RESPONSIBLE participants can add participants"
- **Applies to**: `POST /api/v2/appointments/{id}/participants/{userId}`

**Why**: RESPONSIBLE is the organizer role; only organizers should be able to invite participants. This mirrors the Java backend and ensures consistent permission model across all endpoints.

### 2. Event Structure: AppointmentParticipationAddedEvent

Fire `AppointmentParticipationAddedEvent` after successfully adding a participant.

- **Event fields**:
  - `appointment_id`: UUID of the appointment
  - `target_user_id`: OIDC string ID of the newly added participant
  - `acting_user_id`: OIDC string ID of the user who performed the action
  - `timestamp`: Unix timestamp (seconds since epoch)

- **Timing**: Fire after database commit succeeds (transactional safety)
- **Listener**: Event listeners (e.g., push notification service) observe this event

**Why**: Matches the Java `AppointmentParticipationAddedEvent` structure; enables side-effects (push notifications, audit logs) without coupling the handler to those systems.

### 3. Friendship Validation: Skip (Out-of-Scope)

**Do NOT** check whether the acting user and target user are friends.

- **Rationale**: The friendship check is business logic beyond the core endpoint contract. API parity means matching:
  - Endpoint signature: `POST /api/v2/appointments/{id}/participants/{userId}` ✅
  - Request body: `{ "role": "ATTENDANT" }` ✅
  - Authorization layer: RESPONSIBLE role check ✅
  - Event firing: `AppointmentParticipationAddedEvent` ✅
  
  The friendship check is a policy decision, not part of the endpoint contract.

- **Java backend behavior**: Java includes the check (line 67-68), but the specification clarifies this is optional and out of scope for the Rust implementation.

## Consequences

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

## References

- **Ticket**: Issue #116: [Rust Gap] Appointment Participants: Add participant
- **Specification clarification** (2026-10-04): "Friendship check: Skip the friendship validation...skip the friendship check as an out-of-scope detail."
- **Related**: [[issue-91-appointment-mutations]] (authorization pattern, event firing timing)
