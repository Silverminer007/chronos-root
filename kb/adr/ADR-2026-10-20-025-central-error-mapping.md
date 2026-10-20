# ADR-2026-10-20-025: One error mapper for all handler errors; unknown appointment is a 403

- Status: accepted
- Ticket: #157 (found by the parity run of #137). Supersedes point 7 of [ADR-2026-10-20-023](ADR-2026-10-20-023-admin-endpoints.md) ("appointment error bodies are not touched") and the statement in [ADR-2026-10-20-019](ADR-2026-10-20-019-groups-friendships-endpoints.md) (`path` paragraph) that appointment endpoints answer with plain-text `AppointmentError`. The test that pinned this, `test_appointment_error_responses_keep_their_plain_text_body`, was replaced by `test_appointment_error_responses_are_json_with_the_request_path` (`groups_integration_test.rs`).

## Context

Java answers every error of a resource with the `ErrorResponse` JSON (`timestamp, status, error, errorCode, message, path[, fieldErrors]`).
Rust had a second mapper, `AppointmentError`, that answered with `text/plain`, and axum's own rejections (`Json`, `Path`, unmatched route) were plain text or empty.
Java also authorizes before it looks an appointment up (`AuthorizationService`), so an unknown appointment is a `403`, not a `404`.

## Decision

- **`AppError` is the only mapper.** `AppointmentError` stays as the handlers' error type but converts into `AppError` (`From<AppointmentError> for AppError`) and answers through it. `fill_error_path` sets the real `path`. The `Unauthorized` variant is gone; every denial is `Forbidden(<Java message>)`.
- **Authorization messages are constants** (`NO_ROLE_MESSAGE`, `NOT_ATTENDANT_MESSAGE`, `NOT_RESPONSIBLE_MESSAGE` in `appointments/services`), used by services and handlers alike.
- **Unknown appointment = 403** for `GET`, `PATCH`, `DELETE`, `cancel` (role check before the lookup, message of the respective Java `require*`), and for add group / add participant / change role / remove participant (`missing_is_forbidden` in the handlers). The admin endpoints share the services but skip authorization in Java, so they keep `404 RESOURCE_NOT_FOUND`; therefore the mapping is done in the user handlers, not in the services.
- **Extractor rejections** go through `AppError` as well: `AppJson<T>` (unreadable body -> `400 BAD_REQUEST`, "Ungültiger Request-Body") and `AppPath<T>` (path parameter not convertible -> `AppError::RouteNotFound`, as for Jakarta REST). The push handlers return `AppError::unexpected()` (Java's generic 500 JSON) instead of bare `StatusCode`s.
- **Unmatched route:** `route_not_found` is the fallback of the protected router, i.e. after authentication (no token: 401 as before), then `404` JSON without `errorCode` (Java `NotFoundExceptionMapper`).

## Not changed (deliberately)

- Empty `401`/`403` from the security layer (missing token, `@RolesAllowed`): Quarkus answers them without body in Java too.
- Admin `Response.status(BAD_REQUEST).build()` answers stay empty (`AdminError::Status`).
- `405` (e.g. `DELETE /api/v2/user`) stays empty as documented in [ADR-2026-10-20-017](ADR-2026-10-20-017-user-settings-keycloak-acl.md); an unsupported method on a known path is not mapped.
- `Query<T>` rejections and a wrong `Content-Type` (Java: 415) still use axum's answer; no Java behaviour was verified for them.

## Consequences

- Tests: `rust-backend/tests/error_mapping_test.rs`; `groups_integration_test.rs` now expects JSON for the appointment error.
- Clients see the same status (403) for a missing appointment as with the Java backend.
