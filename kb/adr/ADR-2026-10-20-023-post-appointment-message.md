---
title: ADR-2026-10-20-023: Post appointment message (Java parity)
date: 2026-10-20
status: Accepted
supercedes: (none)
superceded_by: (none)
---

## Context

Issue #125 requires `POST /api/v2/appointments/{id}/messages` in the Rust backend. Java serves it in `MessageResource#sendMessage` and `MessageService#sendMessage`. The ticket answers of 2026-10-13 (including the #95 prerequisite) settle: `i64` ids (#95), Java snake_case field names, no body validation, and push is out of scope except that the event must be fired (the listener is #99). They also state that Java behaviour supersedes the 2026-10-04 answers where they differ. The hint of 2026-10-20 (from #99) asks to record any event publisher that is still missing (see decision 7).

## Decision

1. **Status `200`, not `201`.** Java answers `Response.ok(...)`. The 2026-10-04 answer asked for `201` (taken from a Java integration test), but it is superseded by the Java-parity rule and the contract harness (ADR-2026-10-20-021) diffs status codes.
2. **No migration, no soft delete.** The `message` table is in `001_baseline.sql`; messages are hard-deleted only.
3. **Authorization = Java `requireSendMessage`:** same rule as the `GET` (ADR-2026-10-20-022): a participation row with a role other than `NONE`, any status. Everyone else, including callers of an unknown appointment, gets `403`. The Java admin bypass is not ported (no admin concept in Rust yet).
4. **Request:** `{"body": "..."}`. Other `MessageDto` fields are ignored, a missing or `null` body is stored as `NULL`. No blank or length check, because Java has none (a body over 255 characters fails in the database and answers `500`, as in Java).
5. **Response:** the Java `MessageDto` `{id, sender_id, sender_name, appointment_id, body, timestamp}`. `sender_name` is the caller's name from the token claims (`UserIdentity::from(&PrincipalContext)`, same rule as Java `UserIdentity#getName`), not a `user_profiles` lookup. When the token has no name claims (`UserIdentity::name()` with all claims `None`), `sender_name` falls back to `Gelöschter Benutzer`. The `GET` response takes the name from `user_profiles`, so POST and `GET` can differ for such a caller (e.g. POST `Gelöschter Benutzer`, `GET` the profile name). This is accepted as Java parity. `id` and `timestamp` come from the inserted row (`RETURNING`), so the response equals what `GET` returns.
6. **Timestamp** is the database `now()` (Java: `Instant.now()`), serialized like in the `GET`.
7. **Event:** `MessageSentEvent` with payload `{"message_id": <id>}` is fired after the insert, which is auto-committed. A failing publisher is logged and does not fail the request, as for the other Rust publishers. The push listener (#99) already handles this event type; the publisher row in ADR-2026-10-20-019 is now `done`. No other event publisher is missing for this ticket.
8. `message_dto` maps a stored row for both endpoints, so the response shape exists once.

## Tests

`rust-backend/tests/appointment_messages_test.rs`: response shape and stored row, `MessageSentEvent` payload, `PENDING` participant allowed, non-participant `403` (nothing stored, no event), unknown appointment `403`, blank body accepted, posted message listed by `GET`. Unit tests cover the request DTO and the principal-to-identity mapping. The contract harness scenario already has the `send message` step.
