# Architecture Decision Records (ADRs)

## Issue #118: Implement DELETE /api/v2/appointments/{id}/participants/{userId}

**Status**: Implemented  
**Date**: 2026-10-04  
**Scope**: Rust Backend API Parity

### Decision

Implemented the `DELETE /api/v2/appointments/{id}/participants/{userId}` endpoint to allow appointment organizers to remove participants from appointments.

### Implementation Details

#### 1. Event Definition (events.rs)
Added `AppointmentParticipationRemovedEvent` struct following the same pattern as other appointment events:
- `appointment_id`: UUID of the appointment
- `target_user_id`: UUID of the participant being removed
- `acting_user_id`: UUID of the user performing the removal (the organizer)
- `timestamp`: Unix timestamp of the event

This event is fired after successful participant removal and can be observed by side-effect handlers (e.g., push notifications).

#### 2. Repository Layer (repository.rs)
Added `remove_participant()` method that:
- Performs a hard delete (DELETE FROM) on the `appointment_participants` table
- Returns `RepositoryError::NotFound` if the participant record doesn't exist
- Follows the same pattern as the existing `add_participant()` method

Rationale for hard delete:
- Matches Java backend behavior (Java backend uses hard delete)
- Simpler audit trail (participant removal is absolute)
- Reduces data storage for inactive participants
- No need for soft-delete filtering in queries

#### 3. Service Layer (services/mod.rs)
Added `remove_participant()` method that:
- Validates appointment exists
- Enforces authorization: only the appointment creator can remove participants
- Calls repository to hard delete the participant
- Fires `AppointmentParticipationRemovedEvent` with proper event payload
- Returns error if unauthorized or participant not found

Authorization Decision:
- Only the appointment **creator** (organizer) can remove participants
- This matches Java backend authorization pattern
- Other participants cannot remove each other, even if they have RESPONSIBLE role

#### 4. Handler Layer (handlers/mod.rs)
Added `remove_participant()` HTTP handler that:
- Accepts path parameters: `appointment_id` and `participant_id` (both UUIDs)
- Extracts the acting user from `PrincipalContext`
- Delegates to service layer
- Returns 200 OK with empty body on success
- Returns appropriate HTTP error codes on failure (400, 403, 404, 500)
- Includes comprehensive Rustdoc comments describing the endpoint

#### 5. Router Configuration (app.rs)
Registered the route as:
```rust
DELETE /api/v2/appointments/:id/participants/:userId
```

### Error Handling

The endpoint returns appropriate HTTP status codes:
- **200 OK** — Participant successfully removed
- **400 Bad Request** — Participant not found or invalid request format
- **403 Forbidden** — User is not the appointment creator
- **404 Not Found** — Appointment not found
- **500 Internal Server Error** — Database error

### Testing

Created comprehensive integration tests in `tests/remove_participant_test.rs`:

1. **Positive Case**: Participant can be successfully removed
2. **Event Firing**: AppointmentParticipationRemovedEvent is fired with correct payload
3. **Authorization Failure**: Non-creator cannot remove participants
4. **Appointment Not Found**: Proper error when appointment doesn't exist
5. **Participant Not Found**: Proper error when participant doesn't exist

### API Parity

This implementation achieves full API parity with the Java backend:
- Endpoint format: ✓ Matches Java backend
- Request/response: ✓ 200 OK with empty body
- Authorization: ✓ Creator-only removal
- Event firing: ✓ AppointmentParticipationRemovedEvent
- Delete strategy: ✓ Hard delete matching Java

### Documentation

Added Rustdoc comments to the handler function documenting:
- Endpoint path and HTTP method
- Authorization requirements
- Request parameters (path variables)
- Response format
- Possible error responses
- Event details

### Future Enhancements

Potential improvements for future tickets:
- Add role-based authorization (e.g., allow RESPONSIBLE participants to remove others)
- Add audit logging for participant removals
- Add bulk participant removal endpoint
- Add invitation revocation (before participant accepts)

