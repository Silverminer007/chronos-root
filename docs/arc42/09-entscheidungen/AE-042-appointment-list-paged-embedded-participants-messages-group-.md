---
id: AE-042
title: "Appointment list paged, embedded participants/messages/group_participants (Java parity)"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2026-10-20
quellen:
  - kb/adr/ADR-2026-10-20-026-appointment-list-and-embedded-details.md
verknuepft: []
---

# AE-042: Appointment list paged, embedded participants/messages/group_participants (Java parity)

Quelle: `kb/adr/ADR-2026-10-20-026-appointment-list-and-embedded-details.md` (Status im ADR: accepted, Datum: 2026-10-20). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

The parity harness showed that the appointment responses of Rust differ from Java (`AppointmentsResource`, `AppointmentMapper`, `PagedResponse`, `AppointmentService#enrichAppointmentDtos`): `GET /api/v2/appointments` was a bare array with `limit/offset/sort_by/sort_dir`, and the appointment objects ended at `minimal_attendees`.

## Entscheidung

- **List shape:** `{"data": [...], "meta": {"page", "size", "total"}}`. `PagedResponse`/`PageMeta` moved from `admin::models` to `appointments::models` (Java: `dto.PagedResponse` is shared by both resources).
- **Query parameters, exactly Java:** `page` (default 0), `size` (default 10), `start` (default now), `end` (default now + 1000 years), `search`, and the flags `participants`, `messages`, `groups`. `limit`, `offset`, `sort_by`, `sort_dir` are gone, and with them the service-side sorting and `appointment_sorting_test` (it only tested raw SQL ordering). The frontend (`frontend/app`, `frontend/server`) does not use them. …

## Konsequenzen

- Tests: `rust-backend/tests/appointment_list_test.rs`; `appointment_mutations_test.rs` now expects a cancelled appointment to stay in the list.
- `AppointmentService::with_details` is the single place that builds the embedded collections; list, `GET /{id}`, `PATCH` and the admin list use it.
- Open (not in scope): timestamp format `...+00:00` vs `...Z` (same instant, informational in the ticket); if a later parity run shows that Java omits the embeds in a case treated as "always" here, only `Embed` at the call site has to change.
