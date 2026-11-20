---
id: LZ-011
title: Mindestteilnehmerzahl und Terminstatus
status: entwurf
typ: laufzeitszenario
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/AppointmentParticipationValidationService.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/AppointmentParticipationStatusConfirmationService.java
verknuepft: [FA-003, BB-012, LZ-004, LZ-006]
---

# Mindestteilnehmerzahl und Terminstatus

Der Terminstatus (`PLANNED` ↔ `NOT_ENOUGH_ATTENDEES`) wird auf drei Wegen neu bestimmt, alle in `AppointmentParticipationValidationService`; bei jedem Wechsel zu `NOT_ENOUGH_ATTENDEES` wird `AppointmentParticipationInvalidEvent` ausgelöst (Push `APPOINTMENT_PARTICIPATION_INVALID` mit Zahlen zu Zusagen, Absagen und Ausstehenden).

| Weg | Auslöser | Regel |
|---|---|---|
| Zu-/Absage | `StatusChanged` (async, [LZ-004](LZ-004-zu-absage.md)) | Nichts bei Start in der Vergangenheit, Status `CANCELLED`/`DELETED` oder einer Absage bei bereits `NOT_ENOUGH_ATTENDEES`. Bei `PLANNED`: Teilnehmer minus Absagen < `minimalAttendees` ⇒ `NOT_ENOUGH_ATTENDEES`. Sonst (`NOT_ENOUGH_ATTENDEES`): Teilnehmer minus Absagen ≥ `minimalAttendees` ⇒ zurück auf `PLANNED` |
| Terminänderung | `AppointmentEdited` (synchron) | Frist je Terminart: 8 Wochen (≥ 24 h), 1 Woche (Mo–Do), 2 Wochen (Fr–So). Liegt der Start innerhalb der Frist, zählen nur Zusagen (< `minimalAttendees` ⇒ `NOT_ENOUGH_ATTENDEES`), davor Teilnehmer minus Absagen. Nichts ohne `minimalAttendees`, bei Start in der Vergangenheit oder Status `CANCELLED`/`DELETED` |
| Zeitplan | `sendNonMinimalAttendeesAlerts` alle 15 Minuten (nur Leader) | Termine mit Status `PLANNED`, deren Start in 8 Wochen (Dauer ≥ 24 h), 1 Woche (Mo–Do) bzw. 2 Wochen (Fr–So) im nächsten 14-Minuten-Fenster liegt: Zusagen < `minimalAttendees` ⇒ `NOT_ENOUGH_ATTENDEES` |

Daneben löst `triggerAppointmentParticipationStatusConfirmation` 30 Tage vor langen Terminen und 7 Tage vor Terminen Fr–So `…StatusConfirmation` aus; der einzige Beobachter schreibt ins Debug-Log. Ein Push `PARTICIPATION_STATUS_RECHECK` ist vorgesehen, aber nicht angebunden (Event nirgends ausgelöst, [BB-008](../05-bausteine/BB-008-events-und-ports.md)).

Das Feld `minimalAttendees` ist optional (`Integer`, beim Anlegen darf es fehlen). Der Beobachter für Zu-/Absagen und `checkForEnoughAttendees` (Zeitplan) vergleichen es ohne Null-Prüfung; bei einem Termin ohne Mindestzahl wirft das eine `NullPointerException` (im Beobachter bleibt der Status unverändert, im Zeitplan bricht der Lauf für die übrigen Termine ab). Nur der Weg „Terminänderung“ prüft auf `null`.
