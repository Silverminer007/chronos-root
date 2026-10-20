---
title: ADR-2026-10-20-022: List appointment messages (Java parity)
date: 2026-10-20
status: Accepted
supercedes: (none)
superceded_by: (none)
---

## Context

Issue #124 requires `GET /api/v2/appointments/{id}/messages` in the Rust backend. Java serves it in `MessageResource#getMessagesForEvent` (`MessageService#getMessages`). Ticket answers: no soft-delete for messages (Java hard-deletes), and `POST` is out of scope. The third question round (UUID vs `i64`, `sender_oidcid` type, name enrichment) is answered by the existing schema: ADR-2026-10-12-014 keeps the Java schema, so `message.appointment_id` is `BIGINT`, `sender_oidcid` is `VARCHAR(255)` and names come from `user_profiles` (ADR-2026-10-20-019).

## Decision

1. **No migration.** The Java-compatible `message` table already exists in `001_baseline.sql`.
2. **No soft-delete filter.** Messages have no deletion marker. Messages of cancelled or deleted appointments stay readable for participants, as in Java.
3. **Authorization = Java `requireReadAppointment`:** a participation row with a role other than `NONE` is required (any status, including `PENDING`). Everybody else, including callers of an unknown appointment, gets `403` (Java derives role `NONE` there). The Java admin bypass is not ported: the Rust backend has no admin concept yet.
4. **Order:** newest first (`timestamp DESC`, `id DESC` as tie-breaker for equal timestamps; Java sorts by timestamp only).
5. **Body** is the Java `MessageDto` array: `id`, `sender_id`, `sender_name`, `appointment_id`, `body`, `timestamp`. Nullable columns serialize as `null` like Java. `timestamp` uses chrono `SecondsFormat::AutoSi` (same fraction rule as `Instant.toString()`).
6. **Sender names** via `IdentityRepository::find_by_ids` (one query). Senders without a profile get `Gelöschter Benutzer`, the same as Java's deleted-user identity.
7. The unused placeholder types `Message`, `MessageDto`, `MessageResponse` in `appointments/models` (not matching Java) were replaced by a `Message` row type and a Java-shaped `MessageDto`.

## Events

A read emits no event. Nothing from #99 is needed here. The `MessageSentEvent` publisher belongs to the future `POST` endpoint.

## Tests

`rust-backend/tests/appointment_messages_test.rs`: shape and ordering, scoping to the appointment, empty list, `PENDING` participant, non-participant `403`, unknown appointment `403`, sender without profile. The contract harness scenario already contains the `list messages` steps.
