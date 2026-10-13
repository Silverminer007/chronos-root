---
title: ADR-2026-10-13-013: Participation status endpoint (Java parity)
date: 2026-10-13
status: Accepted
supercedes: (none)
superceded_by: (none)
---

## Context

Issue #121 requires `GET /api/v2/appointments/{id}/participation/status` in the Rust backend. The Java backend already serves it (`AppointmentParticipationResource#getParticipationStatus`, backed by `AppointmentParticipationQueryService#getUserStatus`). The acceptance criteria require the request/response format and authorization to match Java exactly.

The ticket thread contains earlier answers (2026-10-04) that proposed 404 for non-participants and for soft-deleted appointments, and a minimal body. Those answers conflict with the Java reference. The later answer (2026-10-13) resolves the conflict in favour of Java parity, and this ADR records that choice.

## Decision

Match the Java backend exactly:

### 1. Always 200 OK, PENDING by default

- A caller without a participation row receives `PENDING` (Java: `.orElse(ParticipationStatus.PENDING)`).
- The endpoint does not return 404 for unknown appointments or non-participants.

### 2. No existence or soft-delete check

- The query reads `appointment_participants` only. It does not join `appointments` and does not filter on `status`.
- **Deliberate exception** to the read-filter rule in [ADR-2026-10-03-011](ADR-2026-10-03-011-appointment-mutations.md) (which excludes CANCELLED and DELETED from read endpoints). This endpoint returns the caller's status for cancelled and deleted appointments too, because Java does.

### 3. Caller's own status only

- The path has no user parameter. The response always describes the authenticated principal.
- Any authenticated user may call it. There is no role check (Java calls no authorization service here).

### 4. Body: only `status`

```json
{"status": "PENDING"}
```

- `ParticipationStatusResponse { status: ParticipationStatus }` in `appointments/models`.
- Equivalent to Java's `AppointmentDto` with `@JsonInclude(NON_NULL)` and only `status` set. All other fields are omitted rather than serialised as null.
- Values are `PENDING`, `APPROVED`, `REJECTED` (`serde(rename_all = "UPPERCASE")`, the same as the stored values).

### 5. Storage mapping

- `appointment_participants.status` is `VARCHAR(50)`. `ParticipationStatus` derives `sqlx::Type` with `TEXT` and `UPPERCASE`, mirroring `UserRole`.
- The query selects `status::TEXT`, because the derived type is compatible only with `TEXT`. Without the cast sqlx rejects the `VARCHAR` column at runtime.

### Layering

- Handler `get_participation_status` (presentation) → `AppointmentService::get_participation_status` (applies the PENDING default) → `AppointmentRepository::find_participation_status` (`Option`, so the default lives in one place).

## Consequences

### Positive

- ✅ Frontend can switch between Java and Rust backends for this call without changes.
- ✅ The default lives in the service, so the repository stays a plain lookup.

### Negative

- ❌ A caller can still read their own status for a cancelled or deleted appointment (200 instead of 404). This departs from the ADR-011 read rule. It discloses nothing new: an unknown appointment also returns `PENDING`, so the two cases cannot be told apart.
- ❌ Because a non-participant also gets `PENDING`, a client cannot distinguish "not invited" from "invited, no response". Matches Java.

## Implementation

### Endpoints Summary

| Method | Path | Authorization | Events |
|--------|------|---|---|
| GET | `/api/v2/appointments/{id}/participation/status` | Any authenticated user (own status) | None |

### Tests

- `rust-backend/tests/appointment_participation_status_test.rs` covers APPROVED, REJECTED, PENDING, another user's status not being returned, non-participant (PENDING), unknown appointment (PENDING), and cancelled and deleted appointments (participant status returned).
- The tests build a router containing the handler and inject the principal directly, because the production auth middleware needs a live Keycloak JWKS. Route registration in `build_router` is covered by compilation and by the existing auth middleware, not by these tests.
