# ADR-2026-10-20-026: Appointment list paged, embedded participants/messages/group_participants (Java parity)

- Status: accepted
- Ticket: #156 (found by the parity run of #137; map #21: Java is the reference, Java defects go to #162)
- Amended by: [ADR-2026-10-20-029](ADR-2026-10-20-029-final-parity-run-fixes.md) (decision 1: names are resolved only in `GET /appointments` and `GET /appointments/{id}`, not in `PATCH` and the admin list; `sender_name` is `null` when unresolved)

## Context

The parity harness showed that the appointment responses of Rust differ from Java (`AppointmentsResource`, `AppointmentMapper`, `PagedResponse`, `AppointmentService#enrichAppointmentDtos`): `GET /api/v2/appointments` was a bare array with `limit/offset/sort_by/sort_dir`, and the appointment objects ended at `minimal_attendees`.

## Decision

- **List shape:** `{"data": [...], "meta": {"page", "size", "total"}}`. `PagedResponse`/`PageMeta` moved from `admin::models` to `appointments::models` (Java: `dto.PagedResponse` is shared by both resources).
- **Query parameters, exactly Java:** `page` (default 0), `size` (default 10), `start` (default now), `end` (default now + 1000 years), `search`, and the flags `participants`, `messages`, `groups`. `limit`, `offset`, `sort_by`, `sort_dir` are gone, and with them the service-side sorting and `appointment_sorting_test` (it only tested raw SQL ordering). The frontend (`frontend/app`, `frontend/server`) does not use them.
- **Filter and order (Java `AppointmentRepository#search`):** the caller participates (any role), `end_time > start`, `start_time < end`, status is not DELETED, optional case-insensitive substring match of `search` in name, description and venue (`%` and `_` in `search` are not escaped, like Java). Ordered by `start_time` (then `id` for stable pages). A CANCELLED appointment **stays in the list** (Java hides DELETED only; the former Rust list also hid CANCELLED).
- **Invalid values:** a `page`/`size` that is no int is `404` (Jakarta REST, as in the admin list). A negative `page`, `size < 1` (Panache `Page.of` throws in Java: 500) and an unparsable `start`/`end` (`Instant.parse`: 500) answer **`400`** through the central error mapping ([ADR-2026-10-20-025](ADR-2026-10-20-025-central-error-mapping.md)), per the ticket answer and #162. These are Java defects and belong in the harness allowlist as `java-defect` once the parity harness has such a probe; the default harness steps do not send them. The admin list keeps its Java-exact 500 ([ADR-2026-10-20-023](ADR-2026-10-20-023-admin-endpoints.md)).
- **Embedding.** In Java the mapper copies the lazily loaded entity collections, so the list, `PATCH` and the admin list serialize `participants`, `messages` and `group_participants` even without a flag; the flags `participants/messages/groups` only select `JOIN FETCH`. The harness confirms it (these three steps differ only by the missing fields). Rust therefore **always embeds all three in list, `PATCH` and admin list** and ignores the three list flags (accepted, no effect). `GET /{id}` embeds only what the flags `participants`, `messages`, `group_participants` ask for (the harness step `get appointment` without flags is already identical). `POST` create (and admin create) does not embed, as in Java (mapper without enrichment).
- **Content (Java `enrich`):** `participants` = `user_id`, `name`, `profile_picture_url`, `role`, `status`, `via_group_id`, `via_group_name` (nulls omitted, `@JsonInclude(NON_NULL)`); `messages` = `id`, `sender_id`, `sender_name`, `appointment_id`, `body`, `timestamp` (nulls kept); `group_participants` = `id`, `name`, `members[]` (`id`, `first_name`, `last_name`). Names come from `user_profiles` (batch lookup; a user without a profile is "Gelöschter Benutzer" / null names like the existing participant list). Java's collections are unordered sets; Rust orders participants and groups by id, messages oldest first.
- Everything is fetched in batch per request (one query per collection for the whole page), not per appointment.

## Consequences

- Tests: `rust-backend/tests/appointment_list_test.rs`; `appointment_mutations_test.rs` now expects a cancelled appointment to stay in the list.
- `AppointmentService::with_details` is the single place that builds the embedded collections; list, `GET /{id}`, `PATCH` and the admin list use it.
- Open (not in scope): timestamp format `...+00:00` vs `...Z` (same instant, informational in the ticket); if a later parity run shows that Java omits the embeds in a case treated as "always" here, only `Embed` at the call site has to change.
