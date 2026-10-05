---
title: ADR-2026-10-03-011: Appointment mutations (create/edit/delete/move)
date: 2026-10-03
status: Accepted
supercedes: (none)
superceded_by: (none)
---

## Context

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

## Decision

Implement appointment mutations in Rust backend with **Java-identical API contract** to enable frontend to switch backends without code changes.

### 1. Field Naming: Java-Identical

Use `name`, `venue`, `description`, `start`, `end`, `status`, `minimal_attendees` in all request/response DTOs, matching the Java `AppointmentDto` exactly.

- Requests: field names match Java
- Responses: field names match Java
- Database columns may differ (migrations add new columns, don't edit existing ones)
- Applies to both mutation endpoints (PATCH, POST, DELETE) and read-only endpoints (GET) from #29

### 2. DateTime Format: ISO-8601 Strings

Request and response timestamps use ISO-8601 string format (`"2026-09-15T14:00:00Z"`), not structured DateTime objects.

- Parse: `chrono::DateTime<Utc>::from_str("2026-09-15T14:00:00Z")`
- Serialize: `chrono::serde::ts_seconds_option` or equivalent ISO-8601 formatter
- Matches Java Jackson serialization

### 3. Nested Response Arrays: All Three Like Java

`AppointmentResponse` includes:
- `participants`: array of `UserParticipantDto` (id, role, status)
- `messages`: array of `MessageDto` (id, text, created_at, user)
- `group_participants`: array of `GroupDto` (id, name, members)

Omit null fields via `#[serde(skip_serializing_if = "Option::is_none")]` matching Java `@JsonInclude(NON_NULL)`.

### 4. Status Field: PLANNED/CANCELLED/DELETED

Add `status` column with three values:
- `PLANNED`: default on creation
- `CANCELLED`: set by `POST /api/v2/appointments/{id}/cancel` (soft delete)
- `DELETED`: set by `DELETE /api/v2/appointments/{id}` (soft delete)

No rows are ever removed from the database. Query filters exclude CANCELLED and DELETED from list endpoints and read endpoints.

### 5. Creator Participation: Auto-Join via Event

Creating an appointment automatically makes the creator a RESPONSIBLE participant.

- Event: `AppointmentCreatedEvent` fires after database commit
- Handler: `AppointmentParticipationService.onAppointmentCreated` observes event
- Action: inserts participation row with `role = RESPONSIBLE`
- Timing: after repository transaction succeeds (transactional safety)

### 6. Validation Rules: Minimal Server-Side Checks

Enforce only core constraints:
- **Required**: non-blank `name`
- **Required**: valid ISO-8601 `start` and `end` (parseable strings)
- **Required**: `end >= start` (no backwards time ranges)
- **Optional**: `description`, `venue`, `minimal_attendees`
- **Reject**: negative `minimal_attendees` (returns 400 Bad Request)
- **Allow**: null `minimal_attendees`

**Not enforced**: title length, participant limits, past dates, duration limits, overlapping appointments. Defer business rules to application layer.

### 7. Authorization: Role-Based Access Control

Service layer (not JAX-RS layer) enforces authorization before mutations.

- **PATCH** (`/api/v2/appointments/{id}`): requires ATTENDANT role or higher (any participant can edit)
- **DELETE** (`/api/v2/appointments/{id}`): requires RESPONSIBLE role (creator only)
- **POST /cancel** (`/api/v2/appointments/{id}/cancel`): requires RESPONSIBLE role (creator only)

Matches Java `AuthorizationService` pattern.

### 8. Event Firing: After Database Commit

All events fire **after** the database commit succeeds (transactional safety):

- `AppointmentCreatedEvent` — every create
- `AppointmentEditedEvent` — every PATCH
- `AppointmentMovedEvent` — additionally fires when `start` or `end` changed (in same PATCH)
- `AppointmentDeletedEvent` — every DELETE
- `AppointmentCancelledEvent` — every POST /cancel
- `AppointmentGroupAddedEvent` — every POST /appointments/{id}/groups/{groupId}

Multiple events from a single request (e.g., both EditedEvent and MovedEvent on PATCH) fire in the same transaction, after repository succeeds.

## Consequences

### Positive

- ✅ **Frontend compatibility**: Frontend can switch from Java to Rust backend without code changes
- ✅ **API consistency**: Identical contracts across Java and Rust backends reduce bugs
- ✅ **Event-driven side-effects**: Push notifications and reminders decouple via events, not synchronous calls
- ✅ **Audit trail**: Soft deletes (CANCELLED/DELETED status) preserve history for compliance
- ✅ **Transactional safety**: Events only fire after commit, preventing orphaned side-effects
- ✅ **Authorization separation**: Service layer checks prevent bypassing via malformed requests

### Negative

- ❌ **Database schema evolution**: Field renames (`title` → `name`) require careful migration timing
- ❌ **Query complexity**: Filters must exclude soft-deleted rows (null checks on status column)
- ❌ **Nested response complexity**: Fetching participants/messages/group_participants requires joins or additional queries
- ❌ **Event handler ordering**: Order of event listeners affects side-effect execution (e.g., creator participation before push notifications)

### Trade-offs

- **Simplicity vs. compatibility**: Field rename adds migration burden but enables drop-in replacement
- **Query performance vs. data completeness**: Including nested arrays in response requires extra joins; could defer to separate endpoints later
- **Event ordering vs. decoupling**: Post-commit events decouple side-effects but require careful listener registration order

## Implementation

### Endpoints Summary

| Method | Path | Authorization | Events |
|--------|------|---|---|
| POST | `/api/v2/appointments` | Public (creator becomes RESPONSIBLE) | `AppointmentCreatedEvent` |
| PATCH | `/api/v2/appointments/{id}` | ATTENDANT+ | `AppointmentEditedEvent` + `AppointmentMovedEvent` (if start/end changed) |
| DELETE | `/api/v2/appointments/{id}` | RESPONSIBLE | `AppointmentDeletedEvent` |
| POST | `/api/v2/appointments/{id}/cancel` | RESPONSIBLE | `AppointmentCancelledEvent` |
| POST | `/api/v2/appointments/{id}/groups/{groupId}` | RESPONSIBLE | `AppointmentGroupAddedEvent` |

### Validation Layer

```rust
// service layer — AppointmentService::validate()
fn validate(&self, req: &CreateAppointmentRequest) -> Result<(), ValidationError> {
  if req.name.trim().is_empty() {
    return Err(ValidationError::MissingField("name"));
  }
  
  let start = DateTime::parse_from_rfc3339(&req.start)
    .map_err(|_| ValidationError::InvalidDateTime("start"))?;
  let end = DateTime::parse_from_rfc3339(&req.end)
    .map_err(|_| ValidationError::InvalidDateTime("end"))?;
  
  if end < start {
    return Err(ValidationError::InvalidDateRange("end < start"));
  }
  
  if let Some(minimal) = req.minimal_attendees {
    if minimal < 0 {
      return Err(ValidationError::NegativeValue("minimal_attendees"));
    }
  }
  
  Ok(())
}
```

### Event Firing Pattern

```rust
// Create appointment in transaction
let appointment = repository.persist(appointment_dto)?;

// Fire event AFTER commit
event_bus.publish(AppointmentCreatedEvent {
  appointment_id: appointment.id,
  creator_id: principal.user_id,
  timestamp: Utc::now(),
});

Ok(appointment)
```

### DTO Field Mapping

```rust
#[derive(Serialize, Deserialize)]
pub struct AppointmentResponse {
  pub id: String,
  pub name: String,
  pub description: Option<String>,
  pub venue: Option<String>,
  pub start: String, // ISO-8601
  pub end: String,   // ISO-8601
  pub status: AppointmentStatus,
  pub minimal_attendees: Option<i32>,
  
  #[serde(skip_serializing_if = "Option::is_none")]
  pub participants: Option<Vec<UserParticipantDto>>,
  
  #[serde(skip_serializing_if = "Option::is_none")]
  pub messages: Option<Vec<MessageDto>>,
  
  #[serde(skip_serializing_if = "Option::is_none")]
  pub group_participants: Option<Vec<GroupDto>>,
}
```

### Authorization Pattern

```rust
// In PATCH handler
let appointment = repository.find_by_id(id)?;

// Check authorization before mutation
authorization_service.check_attendant(&principal, &appointment)
  .map_err(|_| UnauthorizedException)?;

// Mutate
let updated = appointment.update_from(patch_request);
repository.update(updated)?;

// Fire events
event_bus.publish(AppointmentEditedEvent { ... });
```

### Event Types Table

| Event Name | Fired by | Event Fields |
|---|---|---|
| `AppointmentCreatedEvent` | POST `/api/v2/appointments` | `appointment_id`, `creator_id`, `timestamp` |
| `AppointmentEditedEvent` | PATCH `/api/v2/appointments/{id}` | `appointment_id`, `timestamp` |
| `AppointmentMovedEvent` | PATCH `/api/v2/appointments/{id}` (when start/end changes) | `appointment_id`, `old_start`, `old_end`, `timestamp` |
| `AppointmentDeletedEvent` | DELETE `/api/v2/appointments/{id}` | `appointment_id`, `timestamp` |
| `AppointmentCancelledEvent` | POST `/api/v2/appointments/{id}/cancel` | `appointment_id`, `timestamp` |
| `AppointmentGroupAddedEvent` | POST `/api/v2/appointments/{id}/groups/{groupId}` | `appointment_id`, `group_id`, `actor_id`, `timestamp` |

## References

- Issue #91: [Appointment mutations: Create/Edit/Delete/Move](https://github.com/Silverminer007/chronos-root/issues/91)
- Issue #30: [Original Java implementation](https://github.com/Silverminer007/chronos-root/issues/30)
- Ticket #122: [Rust Gap - Appointment Groups: Add group](https://github.com/Silverminer007/chronos-root/issues/122)
- Ticket clarifications (2026-10-03): Field naming, DateTime format, nested arrays, status, creator participation, validation, authorization, event firing
- [ADR-2025-09-28-007: Layered backend (service/domain/infrastructure pattern)](ADR-2025-09-28-007-layered-backend.md)
- [ADR-2025-09-28-009: Panache ORM (Hibernate, soft deletes via status column)](ADR-2025-09-28-009-panache.md)

## Related Entries

- [ADR-2026-10-03-010: Event bus trait split](#) — fire-and-forget events after commit
- Frontend code: `frontend/app/stores/appointments.ts` (calls PATCH/DELETE endpoints)
- Database migration: `rust-backend/migrations/006_add_appointment_status_and_minimal_attendees.sql`
