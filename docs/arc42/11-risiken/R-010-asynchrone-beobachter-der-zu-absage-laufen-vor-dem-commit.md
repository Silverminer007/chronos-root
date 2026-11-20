---
id: R-010
title: "Asynchrone Beobachter der Zu-/Absage laufen vor dem Commit"
status: entwurf
typ: risiko
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/AppointmentParticipationService.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/WebPushService.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/MessageService.java
verknuepft: [AE-048]
---

# R-010: Asynchrone Beobachter der Zu-/Absage laufen vor dem Commit

`changeParticipationStatus` löst `AppointmentParticipationStatusChangedEvent` per `fire` und `fireAsync` aus; alle Observer (Push, Systemnachricht, Mindestteilnehmer-Prüfung, Debug-Log) sind `@ObservesAsync`, nicht `AFTER_SUCCESS` ([AE-048](../09-entscheidungen/AE-048-domaenenereignisse-ueber-cdi-events.md)). Sie starten sofort, möglicherweise bevor die Transaktion der Zusage committed ist.

**Auswirkung:** Ein Observer kann den alten Status lesen (falsche Zusagezahl, falscher Terminstatus) oder bei Rollback trotzdem Push und Systemnachricht auslösen. Aufgetreten ist das nicht belegt **(?)**.

**Maßnahmen:** Ereignis nur mit `@Observes(during = AFTER_SUCCESS)` weitergeben oder die asynchrone Verarbeitung erst nach dem Commit starten.
