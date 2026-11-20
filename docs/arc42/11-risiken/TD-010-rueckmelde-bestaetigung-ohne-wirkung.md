---
id: TD-010
title: "Rückmelde-Bestätigung ohne Wirkung"
status: entwurf
typ: technische-schuld
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/AppointmentParticipationStatusConfirmationService.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/DebugLoggingService.java
verknuepft: [AE-052, TD-008, G-010]
---

# TD-010: Rückmelde-Bestätigung ohne Wirkung

`AppointmentParticipationStatusConfirmationService` läuft alle 15 min unter Leader-Election und löst 30 Tage vor langen Terminen (ab 24 h) und 7 Tage vor Terminen mit Beginn Freitag bis Sonntag `AppointmentParticipationStatusConfirmationEvent` aus. Den Event beobachtet nur `DebugLoggingService` (Log-Ausgabe); `WebPushService` hat keinen Observer. Eine Bestätigungs-Nachricht an Teilnehmer entsteht nicht.

**Auswirkung:** Der Job verursacht Last (Datenbankabfragen alle 15 min) ohne fachlichen Nutzen.

**Maßnahme:** Observer mit Push/Systemnachricht ergänzen oder den Job entfernen; mit dem Product Owner klären, ob die Funktion gewollt ist **(?)**.
