---
id: LZ-006
title: Erinnerungen, Scheduler und Leader-Election
status: entwurf
typ: laufzeitszenario
quellen:
  - kb/adr/ADR-2026-10-20-022-reminder-engine-scheduler-leader-election.md
  - backend/.../application/scheduler
verknuepft: [BB-012, BB-017, FA-004]
---

# Erinnerungen, Scheduler und Leader-Election

Alle 15 Minuten (Minute 0/15/30/45) prüft **nur der Leader**:
1. Termine mit Start in den nächsten 20 Wochen, Status nicht `CANCELLED`/`DELETED`.
2. Pro Termin die erste zutreffende Regel (Long ≥ 24 h, Weekday Mo–Do, Weekend Fr–So, 30 min vorher).
3. Ein Auslöser ist fällig, wenn er höchstens 15 Minuten zurückliegt.
4. Gefeuert wird `AppointmentParticipationStatusPendingReminderEvent` → Push an Teilnehmer ohne Antwort.

Leader: Java per Kubernetes-Lease, Rust per PostgreSQL-Advisory-Lock (Übernahme spätestens beim nächsten Tick). `LEADER_ELECTION_ENABLED=false` macht jede Instanz zum Leader.
