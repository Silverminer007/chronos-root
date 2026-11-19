---
id: AE-031
title: "List appointment messages (Java parity)"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2026-10-20
quellen:
  - kb/adr/ADR-2026-10-20-022-list-appointment-messages.md
verknuepft: []
---

# AE-031: List appointment messages (Java parity)

Quelle: `kb/adr/ADR-2026-10-20-022-list-appointment-messages.md` (Status im ADR: accepted, Datum: 2026-10-20). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

Issue #124 requires `GET /api/v2/appointments/{id}/messages` in the Rust backend. Java serves it in `MessageResource#getMessagesForEvent` (`MessageService#getMessages`). Ticket answers: no soft-delete for messages (Java hard-deletes), and `POST` is out of scope. The third question round (UUID vs `i64`, `sender_oidcid` type, name enrichment) is answered by the existing schema: ADR-2026-10-12-014 keeps the Java schema, so `message.appointment_id` is `BIGINT`, `sender_oidcid` is `VARCHAR(255)` and names come from `user_profiles` (ADR-2026-10-20-019).

## Entscheidung

1. **No migration.** The Java-compatible `message` table already exists in `001_baseline.sql`.
2. **No soft-delete filter.** Messages have no deletion marker. Messages of cancelled or deleted appointments stay readable for participants, as in Java.
3. **Authorization = Java `requireReadAppointment`:** a participation row with a role other than `NONE` is required (any status, including `PENDING`). Everybody else, including callers of an unknown appointment, gets `403` (Java derives role `NONE` there). The Java admin bypass is not ported: the Rust backend has no admin concept yet.
4. **Order:** newest first (`timestamp DESC`, `id DESC` as tie-breaker for equal timestamps; Java sorts by timestamp only).
5. **Body** is the Java `MessageDto` array: `id`, `sender_id`, `sender_name`, `appointment_id`, `body`, `timestamp`. Nullable columns serialize as `null` like Java …

## Konsequenzen

(?) im ADR nicht ausgewiesen.
