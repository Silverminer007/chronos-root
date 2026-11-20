---
id: FA-003
title: Zu- und Absage mit Mindestteilnehmerzahl
status: entwurf
typ: anforderung
quellen: [backend/src/main/java/de/chronos_live/chronos_date_api/application/AppointmentParticipationService.java, backend/src/main/java/de/chronos_live/chronos_date_api/application/AppointmentParticipationValidationService.java]
verknuepft: [F-003, F-005]
---

# FA-003: Zu- und Absage mit Mindestteilnehmerzahl

*Als Teilnehmer möchte ich zu- oder absagen, damit der Terminstatus die Mindestteilnehmerzahl widerspiegelt.*

## Akzeptanzkriterien
- Jede Person mit Teilnahmerolle (nicht `NONE`) setzt ihren Status auf `APPROVED` oder `REJECTED`; zwischen beiden darf sie beliebig wechseln. Zurück auf `PENDING` ist ebenso unzulässig wie die Wiederholung des aktuellen Status (jeweils Fehler); ohne Teilnahme `403`.
- Der eigene Teilnahmestatus ist abrufbar.
- Jede Änderung erzeugt die Systemnachricht „<Name> hat zugesagt“ bzw. „<Name> hat abgesagt“ und benachrichtigt die übrigen Teilnehmer gemäß [M-007](../../05-modelle/M-007-benachrichtigungsmatrix.md).
- Die Statusprüfung folgt [M-003](../../05-modelle/M-003-statusautomat-des-termins.md). Beispiel: Mindestzahl 3, vier Teilnehmer, eine Absage → Termin bleibt `PLANNED`; eine zweite Absage → `NOT_ENOUGH_ATTENDEES` und Warnung an alle Teilnehmer; danach eine Zusage statt Absage → zurück auf `PLANNED`.
- Nach Beginn des Termins sowie bei `CANCELLED` und `DELETED` ändert eine Antwort den Terminstatus nicht mehr.
