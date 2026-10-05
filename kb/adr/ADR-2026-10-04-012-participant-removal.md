---
title: ADR-2026-10-04-012: Participant removal with hard delete
date: 2026-10-04
status: Accepted
supercedes: (none)
superceded_by: (none)
relates_to:
  - ADR-2026-10-03-011: Appointment mutations (create/edit/delete/move)
  - ADR-2025-09-28-007: Layered backend architecture
  - ADR-2025-09-28-009: Panache ORM
---

## Context

Issue #118 requires implementing the `DELETE /api/v2/appointments/{id}/participants/{userId}` endpoint in the Rust backend for full API parity with the Java backend. This endpoint allows appointment organizers to remove participants from appointments. Six key decisions needed specification:

1. Hard delete vs. soft delete strategy
2. Event definition and payload structure
3. Authorization scope (creator-only vs. role-based)
4. Response format (empty body vs. deleted object)
5. Where to define the `AppointmentParticipationRemovedEvent`
6. API documentation approach

## Decision

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

Define `AppointmentParticipationRemovedEvent` in `events.rs` with three required fields:

```rust
pub struct AppointmentParticipationRemovedEvent {
    pub appointment_id: Uuid,
    pub target_user_id: Uuid,  // The participant being removed
    pub acting_user_id: Uuid,  // The organizer performing removal
    pub timestamp: i64,        // Unix timestamp
}
```

**Rationale**:
- Follows the same pattern as other participation events (Added, StatusChanged, RoleChanged)
- Includes both participants (target and acting) for audit logging
- Timestamp enables chronological event replay
- Enables side-effect handlers (push notifications, audit log, reminder cleanup)

**Consequence**: Event handlers observing `AppointmentParticipationRemovedEvent` must be deployed before the endpoint goes live to avoid orphaned events.

### 3. Authorization: Creator-Only

Only the appointment **creator** (the user who created the appointment) can remove participants.

**Rationale**:
- Matches Java backend authorization pattern
- Prevents any participant from removing others, even if RESPONSIBLE role is added later
- Follows principle of least privilege: organizer has exclusive removal power
- Simplifies role-based security model during initial implementation

**Consequence**: This is stricter than role-based removal (e.g., allowing RESPONSIBLE participants to remove others). If broader removal permissions are needed later, a new ADR should specify role-based authorization and the service layer check must be updated.

### 4. Response Format: Empty Body

Return `200 OK` with an empty response body on success, matching Java backend behavior.

**Rationale**:
- Consistency with Java backend API contract
- RESTful convention: DELETE operations often return no body on success
- Simplifies response serialization logic

**Consequence**: Clients cannot rely on the response body to verify which participant was removed; they must track it from the request path parameter.

### 5. Event Definition Location

Define `AppointmentParticipationRemovedEvent` in `events.rs` alongside other appointment events, not in a separate module.

**Rationale**:
- Centralized event namespace improves discoverability
- Consistent with other event definitions
- Single file to edit when adding new event handlers

**Consequence**: `events.rs` becomes the master registry for all appointment-related events.

### 6. API Documentation: Rustdoc Comments

Document the endpoint in Rustdoc comments on the handler function, covering:
- Endpoint path and HTTP method
- Authorization requirements
- Request parameters (path variables)
- Response format
- Possible error responses
- Event details

**Rationale**:
- Rustdoc is co-located with implementation code
- Extractable to HTML via `cargo doc`
- Visible to developers reading the handler function
- Sufficient for this codebase; no separate API docs file required yet

**Example**:
```rust
/// Delete a participant from an appointment.
///
/// # Endpoint
/// `DELETE /api/v2/appointments/{id}/participants/{userId}`
///
/// # Authorization
/// Only the appointment creator (organizer) can remove participants from an appointment.
///
/// # Request Parameters
/// - `id` (path): UUID of the appointment
/// - `userId` (path): UUID of the user (participant) to be removed
///
/// # Response
/// Returns 200 OK with an empty body on success. Fires an `AppointmentParticipationRemovedEvent`
/// containing the appointment ID, target user ID, acting user ID, and timestamp.
///
/// # Errors
/// - 404 Not Found: Appointment or participant not found
/// - 403 Forbidden: User is not the appointment creator
/// - 500 Internal Server Error: Database error
```

## Implementation Details

### Handler Layer (`handlers/mod.rs`)

The `remove_participant` handler:
- Accepts path parameters: `appointment_id` and `participant_id` (both UUIDs)
- Extracts the acting user from `PrincipalContext`
- Validates appointment exists via repository fetch
- Enforces authorization check: `appointment.creator_id == acting_user_id`
- Delegates to service layer
- Returns `200 OK` with empty body on success
- Returns appropriate HTTP error codes on failure

### Service Layer (`services/mod.rs`)

The `remove_participant` service method:
- Validates appointment exists
- Enforces creator-only authorization
- Calls repository to hard delete the participant
- Fires `AppointmentParticipationRemovedEvent` with proper event payload
- Returns error if unauthorized or participant not found

### Repository Layer (`repository.rs`)

The `remove_participant` repository method:
- Performs hard DELETE on `appointment_participants` table
- Returns `RepositoryError::NotFound` if participant record doesn't exist
- Follows the same transactional pattern as `add_participant()`

### Router Configuration (`app.rs`)

Register the route as:
```rust
DELETE /api/v2/appointments/:id/participants/:userId
```

## Error Handling

The endpoint returns appropriate HTTP status codes:

| Status | Scenario |
|--------|----------|
| 200 OK | Participant successfully removed |
| 400 Bad Request | Invalid request format or participant not found |
| 403 Forbidden | User is not the appointment creator |
| 404 Not Found | Appointment not found |
| 500 Internal Server Error | Database error |

## Testing Strategy

Comprehensive integration tests verify:

1. **Positive Case**: Participant can be successfully removed
2. **Event Firing**: `AppointmentParticipationRemovedEvent` is fired with correct payload
3. **Authorization Failure**: Non-creator cannot remove participants
4. **Appointment Not Found**: Proper error when appointment doesn't exist
5. **Participant Not Found**: Proper error when participant doesn't exist

Tests run against a real test database (Testcontainers PostgreSQL) to ensure hard-delete behavior is correct.

## API Parity Checklist

✅ Endpoint format matches Java backend  
✅ Request/response format matches (200 OK with empty body)  
✅ Authorization matches (creator-only)  
✅ Event firing implemented and tested  
✅ Delete strategy matches (hard delete)  
✅ Endpoint documented via Rustdoc  

## Consequences

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

- **Simplicity vs. Auditability**: Hard delete is simpler but offers less audit history than soft delete. If full audit trail becomes a requirement, a new ADR should specify soft delete with a `deleted_at` timestamp.
- **Creator-only vs. Role-based**: Restricting to creator is simpler but less flexible. Role-based removal could be added in a future ADR if business requirements change.

## Future Enhancements

Potential improvements for future tickets:

- Add role-based authorization (e.g., allow RESPONSIBLE participants to remove others)
- Add audit logging table for participant removal events
- Add bulk participant removal endpoint
- Add invitation revocation (before participant accepts)
- Implement soft delete with `deleted_at` timestamp if compliance requires full audit trail

## References

- Issue #118: [Appointment Participants: Remove participant](https://github.com/Silverminer007/chronos-root/issues/118)
- Ticket specification: Answers provided 2026-10-04 clarifying hard delete, event structure, creator-only authorization
- [ADR-2026-10-03-011: Appointment mutations (create/edit/delete/move)](ADR-2026-10-03-011-appointment-mutations.md)
- [ADR-2025-09-28-007: Layered backend architecture (service/domain/infrastructure pattern)](ADR-2025-09-28-007-layered-backend.md)
- [ADR-2025-09-28-009: Panache ORM (Hibernate, hard/soft deletes)](ADR-2025-09-28-009-panache.md)
- Implementation: `rust-backend/src/appointments/handlers/mod.rs::remove_participant`
- Tests: `rust-backend/tests/remove_participant_test.rs`

## Related Entries

- ADR-2026-10-03-011: Appointment mutations
- PATTERN-2025-09-28-005: Authorization checks in services
- ARCH-2025-09-28-003: Layered backend structure
