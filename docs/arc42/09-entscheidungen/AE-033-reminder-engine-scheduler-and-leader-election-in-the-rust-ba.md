---
id: AE-033
title: "Reminder engine, scheduler and leader election in the Rust backend"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2026-10-20
quellen:
  - kb/adr/ADR-2026-10-20-022-reminder-engine-scheduler-leader-election.md
verknuepft: []
---

# AE-033: Reminder engine, scheduler and leader election in the Rust backend

Quelle: `kb/adr/ADR-2026-10-20-022-reminder-engine-scheduler-leader-election.md` (Status im ADR: accepted, Datum: 2026-10-20). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

Java sends reminders from `AppointmentReminderScheduler` (cron `0 */15 * * * ?`, leader only) ->
`AppointmentReminderService` -> `ReminderRuleEngine` -> `ReminderEventPort` (CDI event) -> `WebPushService`.
The leader is chosen with a Kubernetes lease (`LeaderElectionService`, `scheduler.leader-election.enabled`,
env `LEADER_ELECTION_ENABLED`, default `true`). The Rust skeleton had invented rules (Mon-Fri weekday,
`Utc::now()` inside a rule, every past trigger fired), a scheduler that read `find_all()` and a fake
leader election that always won.

## Entscheidung

1. **Rules and engine are copied 1:1** (`reminders/rules.rs`, `reminders/services`):
   - Rule order `Long`, `ShortWeekday`, `ShortWeekend`, `AppointmentReminder`; per appointment the **first
     applicable rule** counts and **at most one reminder is fired per run**.
   - Duration = whole hours (truncated); `Long` applies from 24 h. "Weekday" is **Mon-Thu**, "weekend"
     **Fri-Sun** (UTC), as in Java's `AppointmentTimeUtil` (the old Rust code used Mon-Fri).
   - Triggers: Long -16/-8/-4/-2 weeks, then daily from -2 weeks; Weekday -7/-4/-2/-1 days; Weekend
     -4/-2/-1 weeks, then daily from -1 week (relative to the start, not to "now" as in the old Rust code);
     AppointmentReminder -30 min.
   - A trigger is due if `now - trigger >= 0` and `(now - trigger)` is at most 15 whole minutes.
2 …

## Konsequenzen

- **Positive**: no cluster API access, no Role for leases and no extra component; a crashed or disconnected leader
  frees the lock by itself (the session ends), and the Java behaviour "exactly one scheduler" is kept with a
  single shared resource, the database.
- **Negative: one pool connection is lost.** The leader holds the lock on a connection taken out of the sqlx pool
  with `detach()`. The effective pool capacity drops by one on every replica that holds the lock, and the pool
  metrics (size/idle/in use) no longer count that connection. Size `max_connections` with one extra connection
  for the leader.
- **Negative: followers churn connections.** A follower opens a fresh connection on every tick (every 15
  minutes), tries `pg_try_advisory_lock` and closes it again. The cost is negligible at this rate, but it is a
  short-lived extra connection per follower and tick. …
