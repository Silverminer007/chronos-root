# ADR-2026-10-20-029: Final parity run fixes (names, move message, PATCH messages, bare settings 400) and explorative allowlist

- Status: accepted
- Ticket: #169 (final proof of the Rust port; map #21)
- Amends: [ADR-2026-10-20-026](ADR-2026-10-20-026-appointment-list-and-embedded-details.md) (names are resolved only where Java resolves them)

## Context

Ticket #169 (final proof of the Rust port). The rerun after #156-#161 on fresh Java and Rust images found Rust deviations that the earlier allowlist had hidden or that the earlier runs could not see (arrays were empty). Java is the reference (map #21), so each is fixed in Rust or left red; none is allowlisted. The Java source is not changed (#162).

## Decisions

1. **Names are resolved only where Java resolves them.** Java calls `AppointmentService#enrichAppointmentDtos` in `GET /appointments` and `GET /appointments/{id}`; `PATCH /appointments/{id}` and `GET /admin/appointments` return the bare mapper output: participants without `name`/`profile_picture_url`/`via_group_name`, messages with `sender_name: null`, group members with `first_name`/`last_name: null`. `Embed` gets a `names` flag (`Embed::ALL` resolves, `Embed::UNNAMED` does not); `MessageDto.sender_name` is `Option<String>` (`null` when unresolved, also when the sender is unknown). This amends ADR-2026-10-20-026, which embedded names everywhere.
2. **Moving an appointment writes a system message** (Java `MessageService#onAppointmentMoved`): `<name> hat diesen Termin um N Stunden verschoben` / `vor verlegt` (start moved later / earlier), else `hat das Ende dieses Termins um N Stunden verschoben` / `vor verlegt` (end decides, also for 0). Hours are whole hours of the real delta. **Java defect (to #162):** the observer reloads the appointment in its own transaction before the new times are stored, so Java always computes 0 hours and writes `... Ende dieses Termins um 0 Stunden vor verlegt`. Rust writes the intended text; message bodies are not compared in success bodies (ADR-2026-10-20-021), so this is not allowlisted and does not show in the run.
3. **The PATCH response embeds the messages as they were before the update** (Java reads the appointment first; the move message is written in its own transaction). The handler reads the messages before `update_appointment` and puts them into the response; participants and groups are read after.
4. **Malformed settings JSON is a bare `400`** (Quarkus maps Jackson's `JsonParseException` itself, no body). #161 had answered with a JSON error body; `ApiError::unparsable_body` sends no body. Wrong types and empty bodies stay `500` like Java.
5. **Harness:** a Rust 404/405 against a Java 5xx is a `status_mismatch` (Java defect), not `missing_route` - Java did not give a deliberate answer for a route Rust lacks. `missing_route` stays non-allowlistable.
6. **The explorative pass uses `contract-tests/allowlist.json`** (`explore.py` applies the same allowlist and prints `ALLOWED`). Explore-only entries (11 Java 500 cases, all `java-defect (#162)`) live in the same file, one source of truth; the harness lists them as `NOTE unused allowlist entry`.
7. **Scenario data for empty arrays:** the contract scenario adds the second group member, group, message before the `list/get/patch appointment with embedded data` steps, a reverse friend request before `incoming requests with data`, and a push subscription around `admin push test` (log no longer empty).

## Consequences

- Tests: `tests/appointment_list_test.rs` (names unresolved in PATCH/admin list), `tests/system_messages_parity_test.rs` (move message, PATCH messages from before the move), `tests/user_endpoints_test.rs` (bare 400), `contract-tests/tests/test_compare.py` (Java 5xx).
- The error bodies of the appointments module (`VALIDATION_ERROR` code, `fieldErrors`, Java message texts, not-found text with id) were first left red; review of MR !173 asked for the fix instead of a follow-up ticket, so they are fixed in the same ticket (see ARCH-2026-10-20-002, section "Appointment error bodies"). No Rust deviation is open and no follow-up ticket exists.
