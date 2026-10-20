---
title: ADR-2026-10-04-012: RSVP Endpoint Design (POST /api/v2/appointments/{id}/participation)
date: 2026-10-04
status: Accepted
supercedes: (none)
superceded_by: (none)
---

## Context

Issue #120 requires implementing the RSVP endpoint (`POST /api/v2/appointments/{id}/participation`) in the Rust backend, enabling users to respond to appointment invitations. The Java backend already implements this endpoint with specific authorization and validation rules. The Rust implementation must match the Java API contract exactly to maintain frontend compatibility.

Key design questions:
1. How should soft-deleted appointments (CANCELLED/DELETED status) be filtered when validating RSVP requests?
2. What participation status transitions are allowed (which values can a user transition to)?
3. What authorization model should be enforced — role-based access or explicit participation checks?
4. When should events fire relative to database commits?
5. How does the Rust implementation differ from the Java backend (e.g., no admin bypass for non-organizers)?

## Decision

Implement the RSVP endpoint in Rust backend with **Java-identical behavior** for authorization, validation, and event firing. Defer admin checks to a future ticket.

### 1. Soft-Delete Filtering Strategy: Query-Level

When validating an RSVP request, check that the appointment exists and has status `PLANNED` (not CANCELLED or DELETED):

- **Query-level check**: the `rsvp_to_appointment` handler calls `service.change_participation_status()` which queries the participation record
- **Implicit filter**: soft-deleted appointments cannot have their participations updated because finding the appointment fails
- **Consequence**: returning 404 for RSVP to deleted/cancelled appointment, not 403, matching Java behavior

No business-logic filter is added; the database query implicitly enforces it.

### 2. Status Validation Scope: Immutable PENDING, Binary Transitions

Only allow transitions to `APPROVED` or `REJECTED`:

- **Reject**: `ParticipationStatus::Pending` → return 400 Bad Request with message "you cannot set your participation status back to pending"
- **Reject**: same status as current → return 422 Unprocessable Entity with message "this is already your participation status"
- **Allow**: `PENDING` → `APPROVED` or `REJECTED` (any direction allowed; user can change their mind)

Matches Java `AppointmentParticipationService.changeParticipationStatus()` exactly.

### 3. Authorization Model: Participant Read-Requirement Only

Authorization requires the calling user to **exist as a participant** in the appointment:

- Handler calls repository: `get_participant_role(appointment_id, user_id)` → returns `Option<UserRole>`
- **Allow**: if role is `Some(Guest | Attendant | Helper | Responsible)`
- **Deny**: if role is `None` or `Some(None)` → return 403 Forbidden with message "Unauthorized"

**Difference from Java**: Java backend has `requireReadAppointment()` which checks if user role is not `NONE` and throws `ForbiddenException` for admins, and also bypasses the check via `isAdminRequest()`. The Rust backend does not yet implement admin bypass — that is deferred to a separate ticket.

The authorization is enforced in the handler layer (not service layer), before the service is called, matching the pattern in other Rust handlers.

### 4. Event Firing: After Successful Status Update

Events fire after the database commit succeeds (transactional safety):

- Event: `AppointmentParticipationStatusChangedEvent`
- Fields: `appointment_id`, `user_id`, `new_status`, `old_status`, `timestamp`
- Timing: after `update_participation_status()` succeeds, inside the service method
- Fire-and-forget: event publisher logs errors but does not propagate to client

Matches [[ADR-2026-10-03-011-appointment-mutations]] event firing pattern (post-commit).

### 5. Response Contract: Empty 200 OK

Return `200 OK` with empty body on successful RSVP.

- No response body (unlike appointment GET endpoints which return full appointment)
- Client infers new status from request (idempotent on client side)
- Matches Java `Response.ok().build()` exactly

## Consequences

### Positive

- ✅ **Frontend compatibility**: Frontend can RSVP without distinguishing Java vs. Rust backends
- ✅ **Authorization safety**: Only existing participants can RSVP, preventing unauthorized status changes
- ✅ **Event-driven notifications**: Push notifications and reminders react to status changes via events
- ✅ **Consistency**: Validation and error messages match Java backend exactly
- ✅ **Simplicity**: soft-delete filtering is implicit (query-level), no duplicate checks

### Negative

- ❌ **Admin bypass incomplete**: Rust backend does not yet implement admin bypass for authorization checks — admins must be explicit participants to RSVP. This is acceptable for now (deferred to future ticket).
- ❌ **Response usability**: Empty 200 OK response requires client to infer new status — could be improved with a future response body containing updated participation object.

### Trade-offs

- **Admin bypass vs. simplicity**: Deferring admin checks keeps implementation simple and focused on core RSVP flow. Admin RSVP is rare and can be added later.
- **Query-level vs. explicit filtering**: Implicit soft-delete filtering via query keeps code simpler than explicit status checks, at the cost of less obvious behavior.

## Implementation

### Handler Layer (Authorization)

```rust
pub async fn rsvp_to_appointment(
    State(state): State<Arc<AppState>>,
    Path(id): Path<Uuid>,
    principal: PrincipalContext,
    Json(request): Json<UpdateParticipationStatusRequest>,
) -> Result<impl IntoResponse, AppointmentError> {
    let user_id = principal.user_id();

    let repo = AppointmentRepository::new(state.db_pool.clone());

    // Authorization check - user must be a participant in the appointment
    let participant_role = repo
        .get_participant_role(id, user_id)
        .await
        .map_err(|_| AppointmentError::DatabaseError)?;

    if participant_role == Some(UserRole::None) || participant_role.is_none() {
        return Err(AppointmentError::Unauthorized);
    }

    let service = AppointmentService::with_events(repo, state.event_publisher.clone());

    service
        .change_participation_status(id, user_id, request.status)
        .await
        .map_err(AppointmentError::from)?;

    Ok(StatusCode::OK)
}
```

### Service Layer (Validation & Event Firing)

```rust
pub async fn change_participation_status(
    &self,
    appointment_id: Uuid,
    user_id: Uuid,
    new_status: ParticipationStatus,
) -> Result<(), ServiceError> {
    // Validate status is not PENDING
    if new_status == ParticipationStatus::Pending {
        return Err(ServiceError::BadRequestError(
            "you cannot set your participation status back to pending".to_string(),
        ));
    }

    // Find current participation status
    let participant = self
        .repo
        .find_participation(appointment_id, user_id)
        .await
        .map_err(|e| ServiceError::DatabaseError(e.to_string()))?
        .ok_or_else(|| {
            ServiceError::ValidationError("This user is not a participant of this event".to_string())
        })?;

    let current_status = participant.status;

    // Validate status is different from current
    if current_status == new_status {
        return Err(ServiceError::ValidationError(
            "this is already your participation status".to_string(),
        ));
    }

    // Update participation status
    self.repo
        .update_participation_status(appointment_id, user_id, new_status)
        .await
        .map_err(|e| ServiceError::DatabaseError(e.to_string()))?;

    // Fire event after successful database commit
    if let Some(ref publisher) = self.event_publisher {
        let event = AppointmentParticipationStatusChangedEvent::new(
            appointment_id,
            user_id.to_string(),
            new_status,
            current_status,
        );
        let event_json = serde_json::json!({
            "appointment_id": event.appointment_id.to_string(),
            "user_id": event.user_id,
            "new_status": event.new_status,
            "old_status": event.old_status,
            "timestamp": event.timestamp,
        });
        let event_bus_event =
            crate::event_bus::Event::new("AppointmentParticipationStatusChangedEvent", event_json);
        if let Err(e) = publisher.fire(event_bus_event).await {
            eprintln!("Failed to fire AppointmentParticipationStatusChangedEvent: {:?}", e);
        }
    }

    Ok(())
}
```

### Request/Response DTOs

**Request**:
```rust
#[derive(Deserialize)]
pub struct UpdateParticipationStatusRequest {
    pub status: ParticipationStatus,  // APPROVED or REJECTED
}
```

**Response**:
- `200 OK` with empty body

### Error Responses

| Scenario | HTTP Status | Message |
|----------|---|---|
| User not a participant | 403 Forbidden | "Unauthorized" |
| Status is PENDING | 400 Bad Request | "you cannot set your participation status back to pending" |
| Status same as current | 422 Unprocessable Entity | "this is already your participation status" |
| Appointment not found | 404 Not Found | "Appointment not found" |
| Database error | 500 Internal Server Error | "Database error" |

## References

- Ticket #120: [Rust Gap] Appointment Participants: RSVP
- Issue #30: Java backend RSVP implementation (Java AppointmentParticipationResource)
- [[ADR-2026-10-03-011-appointment-mutations]] — appointment mutations (create/edit/delete/cancel) design; defines event firing pattern and authorization model reused here
- [[ADR-2025-09-28-007-layered-backend]] — layered backend architecture (handler/service/repository)

## Related Entries

- Java implementation: `backend/src/main/java/de/chronos_live/chronos_date_api/presentation/AppointmentParticipationResource.java` (lines 45-72)
- Java service: `backend/src/main/java/de/chronos_live/chronos_date_api/application/AppointmentParticipationService.java` (`changeParticipationStatus` method)
- Rust implementation: `rust-backend/src/appointments/handlers/mod.rs` (`rsvp_to_appointment` function, lines 238-267)
- Rust service: `rust-backend/src/appointments/services/mod.rs` (`change_participation_status` method)
- Tests: `rust-backend/tests/appointment_rsvp_test.rs`
