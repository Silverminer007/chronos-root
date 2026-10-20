# ADR-2026-10-20-022: Reminder engine, scheduler and leader election in the Rust backend

- Status: accepted
- Ticket: #100 (map #21: Rust re-implementation, Java is the reference)

## Context

Java sends reminders from `AppointmentReminderScheduler` (cron `0 */15 * * * ?`, leader only) ->
`AppointmentReminderService` -> `ReminderRuleEngine` -> `ReminderEventPort` (CDI event) -> `WebPushService`.
The leader is chosen with a Kubernetes lease (`LeaderElectionService`, `scheduler.leader-election.enabled`,
env `LEADER_ELECTION_ENABLED`, default `true`). The Rust skeleton had invented rules (Mon-Fri weekday,
`Utc::now()` inside a rule, every past trigger fired), a scheduler that read `find_all()` and a fake
leader election that always won.

## Decisions

1. **Rules and engine are copied 1:1** (`reminders/rules.rs`, `reminders/services`):
   - Rule order `Long`, `ShortWeekday`, `ShortWeekend`, `AppointmentReminder`; per appointment the **first
     applicable rule** counts and **at most one reminder is fired per run**.
   - Duration = whole hours (truncated); `Long` applies from 24 h. "Weekday" is **Mon-Thu**, "weekend"
     **Fri-Sun** (UTC), as in Java's `AppointmentTimeUtil` (the old Rust code used Mon-Fri).
   - Triggers: Long -16/-8/-4/-2 weeks, then daily from -2 weeks; Weekday -7/-4/-2/-1 days; Weekend
     -4/-2/-1 weeks, then daily from -1 week (relative to the start, not to "now" as in the old Rust code);
     AppointmentReminder -30 min.
   - A trigger is due if `now - trigger >= 0` and `(now - trigger)` is at most 15 whole minutes.
2. **Java quirks are kept, not fixed.** `AppointmentReminderRule` applies to everything but is last in the
   list, so it never wins; and `ReminderEventCDIAdapter#sendReminder` is empty. Hence only
   `AppointmentParticipationStatusPendingReminderEvent {appointment_id}` is ever fired (recipients and text:
   push listener, ADR-2026-10-20-019). `BusReminderEventPort::send_reminder` is a no-op for the same reason;
   `AppointmentReminderEvent` is not fired by the scheduler.
3. **Input of a run** is `find_non_cancelled_between(now, now + 20*7 days)`: `start_time` strictly between,
   status neither CANCELLED nor DELETED (NOT_ENOUGH_ATTENDEES is included), like Java's
   `findNonCancelledBetween`.
4. **Schedule**: a Tokio loop wakes at every quarter hour (minute 0/15/30/45, second 0) like the cron; the
   tick is derived from the injected clock. The scheduler runs inside the backend process (`main.rs`).
5. **Injectable clock** (`reminders/clock.rs`): `Clock` trait with `SystemClock` and `FakeClock`
   (`set`/`advance`). Engine and scheduler get the time only from it; `run_once()` is the testable unit.
6. **Leader election with a PostgreSQL session-level advisory lock** (`reminders/leader.rs`, key
   `SCHEDULER_LOCK_KEY`) instead of Java's Kubernetes lease: the Rust backend has no cluster API access and
   every replica shares the database anyway. The lock lives on one **detached** connection (never returned
   to the pool); `is_leader()` pings it and re-acquires with `pg_try_advisory_lock` when it is gone, so a
   crashed or disconnected leader frees the lock by itself and a follower takes over at its next tick (<=15 min,
   no run is missed because followers try at every tick). `resign()` closes the connection for an immediate
   hand-over. `LEADER_ELECTION_ENABLED` (default `true`; `true/1/yes/y/on` enable, anything else disables)
   switches it off: the instance is then always leader and needs no database lock.
   Consequence: the Rust Helm values need no Role for leases (`backend-role.yaml` is Java only).
7. **Errors**: a failing delivery for one appointment is logged and the other appointments are still
   evaluated (Java would abort the run on an exception). A failing run is logged and retried at the next tick.
8. Old unused types (`ReminderTriggerTimes`, `AppointmentReminderEvent` model with `scheduled_time`) were
   removed; the fired payload is only `{ "appointment_id": <id> }` because the push handler (and Java's
   record) carry nothing else.

## Tests

- Unit (fake clock, no DB): every rule's applicability and trigger times, due window incl. the
  15:59 boundary, first-rule-wins, one reminder per run, event payload, tick calculation, env parsing.
- Integration (`tests/reminder_scheduler_test.rs`, PostgreSQL): two election instances on one database
  (exactly one leader, hand-over on resign, takeover after the leader's session is terminated), two
  schedulers racing (exactly one runs), disabled election (both run), end to end through the real
  `PostgresEventBus` and push listener (an appointment produces the expected notification, clock advance
  walks through trigger times, cancelled/started/too-far appointments are skipped).
