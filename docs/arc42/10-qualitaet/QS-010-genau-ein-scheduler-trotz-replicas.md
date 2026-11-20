---
id: QS-010
title: "Genau ein Scheduler trotz Replicas"
status: entwurf
typ: qualitaetsszenario
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/LeaderElectionService.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/scheduler/AppointmentReminderScheduler.java
  - backend/src/test/java/de/chronos_live/chronos_date_api/application/LeaderElectionServiceTest.java
verknuepft: [QA-008, AE-052]
---

# QS-010: Genau ein Scheduler trotz Replicas

| | |
|---|---|
| **Reiz** | Mehrere Backend-Pods laufen (Autoscaling 1 bis 4 in Prod); der Leader-Pod fällt aus oder verliert die Lease. |
| **Antwort** | Nur der Pod mit der Lease `chronos-scheduler` führt die 15-Minuten-Jobs aus; ein anderer Pod übernimmt die Lease. |
| **Messung** | Nie mehr als ein Leader gleichzeitig; Übernahme spätestens nach der Lease-Dauer von 15 s (plus Retry-Intervall 2 s). Erinnerungen erscheinen je Termin und Zeitpunkt höchstens einmal. |
| **Nachweis** | `LeaderElectionServiceTest` deckt nur den abgeschalteten Modus ab; Übernahme und Einmaligkeit sind nicht automatisch geprüft **(?)**. |
