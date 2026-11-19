---
id: BB-012
title: Java-Backend: Reminder-Engine und Scheduler (Ebene 2)
status: entwurf
typ: baustein-ebene-2
quellen:
  - backend/.../application/reminder
  - backend/.../application/scheduler
  - kb/adr/ADR-2026-10-20-022-reminder-engine-scheduler-leader-election.md
verknuepft: [LZ-006, FA-004]
---

# Java-Backend: Reminder-Engine und Scheduler (Ebene 2)

`AppointmentReminderScheduler` (Cron `0 */15 * * * ?`, nur Leader) → `AppointmentReminderService` → `ReminderRuleEngine` → Regeln `LongAppointmentRSVPRule`, `ShortWeekdayRSVPRule`, `ShortWeekendRSVPRule`, `AppointmentReminderRule` (30 min vorher). Pro Termin zählt die erste zutreffende Regel, je Lauf höchstens eine Erinnerung. `LeaderElectionService` nutzt ein Kubernetes-Lease (`LEADER_ELECTION_ENABLED`, Standard `true`).
