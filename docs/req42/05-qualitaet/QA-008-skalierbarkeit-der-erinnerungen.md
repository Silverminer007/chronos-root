---
id: QA-008
title: Skalierbarkeit der Erinnerungen
status: entwurf
typ: qualitaetsanforderung
quellen: [kb/adr/ADR-2026-10-20-022-reminder-engine-scheduler-leader-election.md]
verknuepft: []
---

# QA-008: Skalierbarkeit der Erinnerungen

**Qualitätsmerkmal:** Zuverlässigkeit

## Szenario
Bei mehreren Backend-Instanzen werden Erinnerungen genau einmal versendet (Leader-Election).

## Messbar durch
Backend-Tests zum Scheduler.
