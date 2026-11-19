---
id: AE-035
title: "Post appointment message (Java parity)"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2026-10-20
quellen:
  - kb/adr/ADR-2026-10-20-023-post-appointment-message.md
verknuepft: []
---

# AE-035: Post appointment message (Java parity)

Quelle: `kb/adr/ADR-2026-10-20-023-post-appointment-message.md` (Status im ADR: accepted, Datum: 2026-10-20). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

Issue #125 requires `POST /api/v2/appointments/{id}/messages` in the Rust backend. Java serves it in `MessageResource#sendMessage` and `MessageService#sendMessage`. The ticket answers of 2026-10-13 (including the #95 prerequisite) settle: `i64` ids (#95), Java snake_case field names, no body validation, and push is out of scope except that the event must be fired (the listener is #99). They also state that Java behaviour supersedes the 2026-10-04 answers where they differ. The hint of 2026-10-20 (from #99) asks to record any event publisher that is still missing (see decision 7).

## Entscheidung

1. **Status `200`, not `201`.** Java answers `Response.ok(...)`. The 2026-10-04 answer asked for `201` (taken from a Java integration test), but it is superseded by the Java-parity rule and the contract harness (ADR-2026-10-20-021) diffs status codes.
2. **No migration, no soft delete.** The `message` table is in `001_baseline.sql`; messages are hard-deleted only.
3. **Authorization = Java `requireSendMessage`:** same rule as the `GET` (ADR-2026-10-20-022): a participation row with a role other than `NONE`, any status. Everyone else, including callers of an unknown appointment, gets `403`. The Java admin bypass is not ported (no admin concept in Rust yet).
4. **Request:** `{"body": "..."}`. Other `MessageDto` fields are ignored, a missing or `null` body is stored as `NULL` …

## Konsequenzen

(?) im ADR nicht ausgewiesen.
