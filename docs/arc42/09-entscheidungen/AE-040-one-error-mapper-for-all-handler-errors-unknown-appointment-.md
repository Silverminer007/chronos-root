---
id: AE-040
title: "One error mapper for all handler errors; unknown appointment is a 403"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2026-10-20
quellen:
  - kb/adr/ADR-2026-10-20-025-central-error-mapping.md
verknuepft: []
---

# AE-040: One error mapper for all handler errors; unknown appointment is a 403

Quelle: `kb/adr/ADR-2026-10-20-025-central-error-mapping.md` (Status im ADR: accepted, Datum: 2026-10-20). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

Java answers every error of a resource with the `ErrorResponse` JSON (`timestamp, status, error, errorCode, message, path[, fieldErrors]`).
Rust had a second mapper, `AppointmentError`, that answered with `text/plain`, and axum's own rejections (`Json`, `Path`, unmatched route) were plain text or empty.
Java also authorizes before it looks an appointment up (`AuthorizationService`), so an unknown appointment is a `403`, not a `404`.

## Entscheidung

- **`AppError` is the only mapper for handler errors** (scope of the extractor rejections: see "Not changed"). `AppointmentError` stays as the handlers' error type but converts into `AppError` (`From<AppointmentError> for AppError`) and answers through it. `fill_error_path` sets the real `path`. The `Unauthorized` variant is gone; every denial is `Forbidden(<Java message>)`.
- **Authorization messages are constants** (`NO_ROLE_MESSAGE`, `NOT_ATTENDANT_MESSAGE`, `NOT_RESPONSIBLE_MESSAGE` in `appointments/services`), used by services and handlers alike.
- **Unknown appointment = 403** for `GET`, `PATCH`, `DELETE`, `cancel` (role check before the lookup, message of the respective Java `require*`), and for add group / add participant / change role / remove participant (`missing_is_forbidden` in the handlers) …

## Konsequenzen

- Tests: `rust-backend/tests/error_mapping_test.rs`; `groups_integration_test.rs` now expects JSON for the appointment error.
- Clients see the same status (403) for a missing appointment as with the Java backend.
