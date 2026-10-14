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

- The query reads `appointment_participation` only. It does not join `appointments` and does not filter on `status`.
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

- `appointment_participation.status` is `VARCHAR(255)`. `ParticipationStatus` derives `sqlx::Type` with `type_name = "TEXT"` and `UPPERCASE`.
- `UserRole` uses `type_name = "VARCHAR"` and is read uncast. `ParticipationStatus` differs: its derived type is compatible only with the `TEXT` OID (sqlx's default `compatible` is `*ty == Self::type_info()`), so the query selects `status::TEXT`. Without the cast, sqlx rejects the `VARCHAR` column at runtime with a mismatched-type error.
- `AppointmentStatus` is not sqlx-derived. It is a hand-written `SMALLINT` mapping and reads through `COALESCE(status, 0)::smallint`, so it is unrelated to this cast.

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
- Most tests build a router containing the handler and inject the principal directly. They check the response per status, not the production route or middleware.
- Two tests go through `build_router`, so route registration and the auth middleware are exercised: an authenticated caller gets `200` with its status, and a request without a token gets `401`. They use a local mock JWKS endpoint and a test-only RS256 key (`rust-backend/tests/fixtures/rs256_test_key.pem`), so no live Keycloak is needed.
