# TDD Implementation: Issue #08 - Appointment Mutations

## Overview

Implemented comprehensive integration tests for appointment CRUD operations (Create, Read, Update, Delete) following Test-Driven Development principles.

## Test Seams (Public Interfaces Tested)

### 1. REST Resource Layer (HTTP Endpoints)
- **POST /api/v2/appointments** — Create new appointment
- **PATCH /api/v2/appointments/{id}** — Update appointment fields
- **DELETE /api/v2/appointments/{id}** — Soft delete (status = DELETED)
- **POST /api/v2/appointments/{id}/cancel** — Cancel appointment (status = CANCELLED)
- **GET /api/v2/appointments/{id}** — Retrieve with optional nested data

### 2. Authorization Layer
- Role-based access control via `AppointmentParticipationQueryService`
- Tested via HTTP status codes (403 Forbidden for unauthorized)
- Creator + participants have roles: RESPONSIBLE, ATTENDANT, GUEST
- Update requires ATTENDANT or higher
- Delete/Cancel require RESPONSIBLE

### 3. Validation Layer
- Input validation (blank names, invalid date ranges, negative integers)
- Tested via HTTP 400 Bad Request responses
- Transactional constraint validation

### 4. Response Format
- JSON structure matching Java backend spec
- Required fields: id, name, status, start, end, participants, messages
- Nested data structures for participants and messages

## Test Coverage

### Acceptance Criteria Coverage (from issue #08)

| Criterion | Tests | Status |
|-----------|-------|--------|
| POST /create | `testCreateAppointment_Success`, `testCreateAppointment_BlankName_Returns400`, `testCreateAppointment_InvalidDateRange_Returns400`, `testCreateAppointment_NegativeMinimalAttendees_Returns400` | ✅ |
| PATCH /update | `testUpdateAppointment_Success`, `testUpdateAppointment_BlankName_Returns400`, `testUpdateAppointment_EndBeforeStart_Returns400`, `testUpdateAppointment_NegativeMinimalAttendees_Returns400`, `testUpdateAppointment_OnlyNameUpdated`, `testUpdateAppointment_OnlyStartTimeUpdated`, `testUpdateAppointment_OnlyEndTimeUpdated`, `testUpdateAppointment_OnlyMinimalAttendeesUpdated`, `testUpdateAppointment_AllFieldsUpdated` | ✅ |
| DELETE /soft-delete | `testDeleteAppointment_Success`, `testDeleteAppointment_SetsStatusDeleted`, `testDeleteAppointment_UnauthorizedUser_Returns403` | ✅ |
| POST /cancel | `testCancelAppointment_Success`, `testCancelAppointment_SetsStatusCancelled` | ✅ |
| Authorization | `testUpdateAppointment_UnauthorizedUser_Returns403`, `testDeleteAppointment_UnauthorizedUser_Returns403` | ✅ |
| Validation | 4 tests covering invalid inputs | ✅ |
| Response Format | `testCreateAppointment_ResponseFormatMatchesSpec`, `testGetAppointment_ResponseFormatMatchesSpec` | ✅ |
| Error Handling | 3 tests for 404 Not Found scenarios | ✅ |

### Test Statistics
- **Total tests added**: 26+
- **Test methods**: ~31 (includes read and search tests)
- **Code coverage**: Covers happy path, validation errors, authorization, and edge cases

## Design Decisions

### 1. Soft Delete Strategy
- Both DELETE and CANCEL use soft deletes (status-based)
- Rows persist in database with status = DELETED or CANCELLED
- Allows historical tracking and recovery
- Separate endpoints for each workflow
- See [ADR-2025-10-01-011: Soft-delete pattern](../kb/adr/ADR-2025-10-01-011-soft-delete.md) for detailed design rationale

### 2. Creator Tracking
- Added `creatorOidcId` field to Appointment entity
- Set by service during creation
- Stored in database for authorization queries
- Indexed for performance (`idx_appointment_creator_oidcid`)

### 3. Authorization Model
- Role-based access control (RESPONSIBLE, ATTENDANT, GUEST)
- Stored in `AppointmentParticipation` table
- Queried via `AppointmentParticipationQueryService.getUserRole()`
- Admin bypass via `PrincipalContext.isAdminRequest()`

### 4. Test Helpers
- `createTestAppointment(creatorOidcId)` — Factory for test data
- `addParticipantToAppointment(...)` — Add roles and participation status
- Reduces duplication and improves maintainability

## Running the Tests

```bash
cd backend

# Run all appointment integration tests
./mvnw test -Dtest=AppointmentIntegrationTest

# Run specific test
./mvnw test -Dtest=AppointmentIntegrationTest#testCreateAppointment_Success

# Full test suite with checks
./mvnw verify
```

## Database Migrations

### V2.2.0: Add Appointment Creator
- Adds `creator_oidc_id VARCHAR(255)` column to appointment table
- Creates index for authorization queries
- Run with: `./mvnw quarkus:dev` (auto-runs migrations)

## Notes for Future Work

### 1. Event Firing Verification
- Service layer already fires events (AppointmentCreatedEvent, AppointmentEditedEvent, AppointmentMovedEvent, AppointmentCancelledEvent, AppointmentDeletedEvent)
- Current integration tests don't verify event firing (would require mocking CDI events at HTTP level)
- Could enhance with event bus spy/listener tests if needed

### 2. Concurrent Edit Handling
- Implementation uses last-write-wins (no optimistic locking)
- No @Version field on Appointment entity
- Simultaneous edits overwrite each other
- Acceptable per spec (comment #4)

### 3. Minimal Attendees Validation
- Only enforces >= 0 constraint
- No max participant limit enforced
- Appointments can exist with 0 actual participants
- Tested as per spec

### 4. Participant List Mutations
- Participants are NOT modified via PATCH /appointments/{id}
- Separate endpoints for adding/removing participants
- Participant list is read-only in update operation
- Tested indirectly through response format tests

## Related Issues

- **#29**: 07: Read-only appointment endpoints (blocked by)
- **#27**: 05: Event bus abstraction & PostgreSQL LISTEN/NOTIFY (blocked by)
- **#08**: Appointment mutations (this issue)

## Test Execution Notes

Tests require:
- PostgreSQL database (via Docker Testcontainers)
- Quarkus test framework with `@QuarkusTest`
- Mock JWT for authentication (`mockJwtForUser`)
- RestAssured for HTTP assertions

All tests inherit from `BaseIntegrationTest` which provides:
- Test user creation (`TEST_USER_OIDC`, `TEST_USER_OIDC_2`, `ADMIN_USER_OIDC`)
- JWT mocking helpers
- RestAssured configuration

## Verification Checklist

- [x] Tests cover all HTTP methods (POST, PATCH, DELETE)
- [x] Authorization enforcement verified (403)
- [x] Validation errors verified (400)
- [x] Not found scenarios verified (404)
- [x] Response format matches Java spec
- [x] Soft delete persists rows (status-based)
- [x] Delete vs Cancel distinction maintained
- [x] All mutable fields tested (name, description, venue, start, end, minimal_attendees)
- [x] Partial updates tested
- [x] Database migrations prepared
- [x] Creator tracking implemented
- [x] Helper methods for test setup
