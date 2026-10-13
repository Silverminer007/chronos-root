---
title: ADR-2026-10-05-012: Appointment Groups: Add group endpoint
date: 2026-10-05
status: Accepted
supercedes: (none)
superceded_by: (none)
---

## Context

Issue #122 requires implementing the `POST /api/v2/appointments/{id}/groups/{groupId}` endpoint in the Rust backend to achieve API parity with the Java backend. This endpoint allows appointment creators to add groups to appointments, automatically adding current group members as appointment participants.

The implementation decision points are:
1. Authorization model (who can add groups)
2. Validation rules (group existence, soft-delete filtering, duplicate checks)
3. Role and status assignment for group participants
4. Event publishing (AppointmentGroupAddedEvent)
5. Database schema (appointment_groups table)
6. Future member auto-addition scope (out of scope for Rust)

## Decision

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
- **Group existence**: Checks group exists and is **not soft-deleted** (deleted_at IS NULL)
- **Deleted group filtering**: Returns `NotFound` if `groups.deleted_at` is set (soft-delete respected)
- **Duplicate prevention**: Checks if group already participates in appointment; rejects with `ValidationError("This group is already a participant of this appointment")`

These checks mirror Java `AppointmentParticipationService.addGroupToAppointment()` logic.

### 3. Role Assignment

The `role` field (passed in request body) determines the participation role for all group members added to the appointment.

- Request format: `POST /api/v2/appointments/{id}/groups/{groupId}` with JSON body: `{ "role": "ATTENDANT" }`
- Valid roles: `ATTENDANT`, `ORGANIZER`, `RESPONSIBLE` (enum `UserRole`)
- All current group members receive the specified role when added (one role per group, not per member)
- Matches Java `AddGroupParticipantDto.user_role` at line 186

### 4. Participation Status for Added Members

Group members added to the appointment receive **PENDING** participation status.

- Status reflects that the member has not yet RSVP'd (yes/no)
- Members can later RSVP via `POST /api/v2/appointments/{id}/participation`
- Status is independent of role

### 5. Event Publishing: AppointmentGroupAddedEvent

After successful database commit, fires **AppointmentGroupAddedEvent**.

Event payload includes:
```json
{
  "appointment_id": "uuid-string",
  "group_id": "uuid-string",
  "actor_id": "uuid-string",
  "timestamp": 1234567890
}
```

Event is persisted to `events` table via `PostgresEventBus` for audit trail and future listener subscription.

- **Timing**: After repository transaction succeeds (transactional safety)
- **Failure handling**: Event publish failure is logged but does not fail the HTTP response (fire-and-forget pattern)

### 6. Database Schema: appointment_groups Table

Migration `009_create_appointment_groups.sql` creates:

```sql
CREATE TABLE IF NOT EXISTS appointment_groups (
    id UUID PRIMARY KEY,
    appointment_id UUID NOT NULL,
    group_id UUID NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'ATTENDANT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT fk_appointment_groups_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(id) ON DELETE CASCADE,
    CONSTRAINT fk_appointment_groups_group FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE,
    CONSTRAINT unique_appointment_group UNIQUE(appointment_id, group_id)
);
```

- **Primary key**: UUID `id` (unique record identifier)
- **Soft deletes**: `deleted_at` column for future `DELETE` endpoint (not yet implemented)
- **Role storage**: `role` VARCHAR column stores the participation role for the group
- **Indexes**: On `appointment_id`, `group_id`, and `deleted_at` for query performance

### 7. Future Member Auto-Addition: Out of Scope

The Java backend automatically adds new group members to appointments when they join the group (via event listener on `UserAddedToGroupEvent`). This feature is **out of scope for Rust**.

- Simplifies implementation (no event listener infrastructure needed for this case)
- Can be added later without breaking the endpoint API contract
- Documented as a known difference from Java backend behavior

## Consequences

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

- **Simplicity vs. completeness**: Skipping auto-addition of future group members reduces complexity but deviates from Java behavior
- **One role per group**: Assigns a single role to all group members in the group-appointment relationship, rather than per-member roles

## Implementation

### Handler Signature

```rust
pub async fn add_group_to_appointment(
    State(state): State<Arc<AppState>>,
    Path((appointment_id, group_id)): Path<(Uuid, Uuid)>,
    principal: PrincipalContext,
    Json(request): Json<AddGroupToAppointmentRequest>,
) -> Result<impl IntoResponse, AppointmentError>
```

**Path**: `/api/v2/appointments/:id/groups/:groupId`  
**Method**: `POST`  
**Authorization**: Extracted from JWT via `PrincipalContext.user_id()`  
**Request body**: `{ "role": "ATTENDANT" }`  
**Response**: `200 OK` (empty body)

### Service Layer Validation Pattern

```rust
async fn validate_group_addition(
    &self,
    actor_id: Uuid,
    appointment_id: Uuid,
    group_id: Uuid,
) -> Result<(), ServiceError> {
    // Check if appointment exists
    let appointment = self
        .repo
        .find_by_id(appointment_id)
        .await?
        .ok_or(ServiceError::NotFound)?;

    // Authorization: only creator can add groups
    if appointment.creator_id != actor_id {
        return Err(ServiceError::ValidationError(
            "Only the appointment creator can add groups".to_string(),
        ));
    }

    // Check if group exists and is not deleted
    let group_exists = self
        .repo
        .group_exists(group_id)
        .await?;

    if !group_exists {
        return Err(ServiceError::NotFound);
    }

    // Check if group is already a participant
    let already_participant = self
        .repo
        .group_participation_exists(appointment_id, group_id)
        .await?;

    if already_participant {
        return Err(ServiceError::ValidationError(
            "This group is already a participant of this appointment".to_string(),
        ));
    }

    Ok(())
}
```

### Service Layer Mutation and Event Pattern

```rust
pub async fn add_group_to_appointment(
    &self,
    actor_id: Uuid,
    appointment_id: Uuid,
    group_id: Uuid,
    role: UserRole,
) -> Result<(), ServiceError> {
    // Validate authorization and preconditions
    self.validate_group_addition(actor_id, appointment_id, group_id)
        .await?;

    // Mutate: add group to appointment
    self.repo
        .add_group(appointment_id, group_id, role)
        .await
        .map_err(|e| ServiceError::DatabaseError(e.to_string()))?;

    // Publish event after successful database commit
    if let Some(ref publisher) = self.event_publisher {
        let event_json = serde_json::json!({
            "appointment_id": appointment_id.to_string(),
            "group_id": group_id.to_string(),
            "actor_id": actor_id.to_string(),
            "timestamp": chrono::Utc::now().timestamp(),
        });
        let event_bus_event =
            crate::event_bus::Event::new("AppointmentGroupAddedEvent", event_json);
        if let Err(e) = publisher.fire(event_bus_event).await {
            eprintln!("Failed to fire AppointmentGroupAddedEvent: {:?}", e);
        }
    }

    Ok(())
}
```

### Request/Response DTOs

```rust
#[derive(Deserialize)]
pub struct AddGroupToAppointmentRequest {
    pub role: String, // "ATTENDANT", "ORGANIZER", "RESPONSIBLE"
}

// Response: 200 OK with empty body
```

### Error Responses

| Scenario | HTTP Status | Error Message |
|----------|---|---|
| Appointment not found | 404 | `NotFound` |
| Group not found or deleted | 404 | `NotFound` |
| Authorization failure (non-creator) | 400 | `ValidationError("Only the appointment creator can add groups")` |
| Group already participant | 400 | `ValidationError("This group is already a participant of this appointment")` |
| Database error | 500 | `DatabaseError("...")`  |

## Testing

Integration tests in `rust-backend/tests/appointment_add_group_test.rs` cover:

1. **Success**: Creator can add a group; participation record created; event fired
2. **Authorization**: Non-creator cannot add group; no mutation occurs
3. **Soft-delete validation**: Deleted groups are rejected (NotFound)
4. **Duplicate prevention**: Cannot add the same group twice

Run with:
```bash
cd rust-backend
cargo test appointment_add_group_tests --test appointment_add_group_test -- --nocapture
```

## References

- **Issue #122**: [Rust Gap] Appointment Groups: Add group endpoint
- **Issue #91**: Appointment mutations (Create/Edit/Delete/Move)
- **Java reference**: `AppointmentParticipationResource.java` lines 161-189; `AppointmentParticipationService.addGroupToAppointment()`
- **Related ADRs**:
  - [[ADR-2026-10-03-011: Appointment mutations (create/edit/delete/move)]] — event-driven side-effects, transactional safety
  - [[ADR-2025-09-28-007: Layered backend (service/domain/infrastructure pattern)]]
  - [[ADR-2025-09-28-009: Panache ORM (Hibernate, soft deletes via status column)]]
- **Database migration**: `rust-backend/migrations/009_create_appointment_groups.sql`
- **Implementation**:
  - Handler: `rust-backend/src/appointments/handlers/mod.rs`
  - Service: `rust-backend/src/appointments/services/mod.rs`
  - Repository: `rust-backend/src/appointments/repository.rs`
  - Tests: `rust-backend/tests/appointment_add_group_test.rs`

## Related Entries

- `DELETE /api/v2/appointments/{id}/groups/{groupId}` endpoint (planned, would use same schema with soft-deletes)
- Group member auto-addition on group join (Java-only feature, not implemented in Rust)
