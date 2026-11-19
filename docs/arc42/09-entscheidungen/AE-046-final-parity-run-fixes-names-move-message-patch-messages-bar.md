---
id: AE-046
title: "Final parity run fixes (names, move message, PATCH messages, bare settings 400) and explorative allowlist"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2026-10-20
quellen:
  - kb/adr/ADR-2026-10-20-029-final-parity-run-fixes.md
verknuepft: []
---

# AE-046: Final parity run fixes (names, move message, PATCH messages, bare settings 400) and explorative allowlist

Quelle: `kb/adr/ADR-2026-10-20-029-final-parity-run-fixes.md` (Status im ADR: accepted, Datum: 2026-10-20). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

Ticket #169 (final proof of the Rust port). The rerun after #156-#161 on fresh Java and Rust images found Rust deviations that the earlier allowlist had hidden or that the earlier runs could not see (arrays were empty). Java is the reference (map #21), so each is fixed in Rust or left red; none is allowlisted. The Java source is not changed (#162).

## Entscheidung

1. **Names are resolved only where Java resolves them.** Java calls `AppointmentService#enrichAppointmentDtos` in `GET /appointments` and `GET /appointments/{id}`; `PATCH /appointments/{id}` and `GET /admin/appointments` return the bare mapper output: participants without `name`/`profile_picture_url`/`via_group_name`, messages with `sender_name: null`, group members with `first_name`/`last_name: null`. `Embed` gets a `names` flag (`Embed::ALL` resolves, `Embed::UNNAMED` does not); `MessageDto.sender_name` is `Option<String>` (`null` when unresolved, also when the sender is unknown). This amends ADR-2026-10-20-026, which embedded names everywhere.
2 …

## Konsequenzen

- Tests: `tests/appointment_list_test.rs` (names unresolved in PATCH/admin list), `tests/system_messages_parity_test.rs` (move message, PATCH messages from before the move), `tests/user_endpoints_test.rs` (bare 400), `contract-tests/tests/test_compare.py` (Java 5xx).
- The error bodies of the appointments module (`VALIDATION_ERROR` code, `fieldErrors`, Java message texts, not-found text with id) were first left red; review of MR !173 asked for the fix instead of a follow-up ticket, so they are fixed in the same ticket (see ARCH-2026-10-20-002, section "Appointment error bodies"). No Rust deviation is open and no follow-up ticket exists.
