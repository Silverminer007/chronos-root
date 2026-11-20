---
id: FA-002
title: Personen und Gruppen einladen
status: entwurf
typ: anforderung
quellen: [backend/src/main/java/de/chronos_live/chronos_date_api/application/AppointmentParticipationService.java, backend/src/main/java/de/chronos_live/chronos_date_api/application/AuthorizationService.java]
verknuepft: [F-004, F-007]
---

# FA-002: Personen und Gruppen einladen

*Als Verantwortliche/r möchte ich Personen und Gruppen einladen und Einladungen zurücknehmen, damit die richtigen Leute teilnehmen.*

## Akzeptanzkriterien
- Einladen und Entfernen darf nur, wer am Termin die Rolle `RESPONSIBLE` hat; andere erhalten `403`.
- Eine Person kann nur eingeladen werden, wenn sie mit der einladenden Person befreundet ist (angenommene Freundschaft); sonst `403`. Die Rolle der neuen Teilnahme wird beim Einladen gewählt, der Teilnahmestatus ist `PENDING`.
- Eine bereits eingeladene Person oder Gruppe kann nicht erneut eingeladen werden (Validierungsfehler).
- Eine Gruppe kann nur einladen, wer selbst Mitglied dieser Gruppe ist. Jedes Mitglied ohne bestehende Teilnahme erhält eine eigene Teilnahme mit der gewählten Rolle und Status `PENDING`; bestehende Teilnahmen bleiben unverändert.
- Wird eine Person nach der Einladung Mitglied der Gruppe, erhält sie automatisch eine Teilnahme an allen Terminen der Gruppe mit deren Rolle; verlässt oder verliert sie die Mitgliedschaft, entfällt die über die Gruppe entstandene Teilnahme.
- Die Teilnehmerliste kennzeichnet Teilnahmen, die über eine Gruppe entstanden sind (Gruppenname).
- Eine Person entfernen löscht deren Teilnahme; eine Gruppe entfernen löscht alle über die Gruppe entstandenen Teilnahmen. Entfernen einer nicht vorhandenen Teilnahme ist ein Validierungsfehler.
- Das Einladen löst die Benachrichtigung „neuer Teilnehmer“ aus ([M-007](../../05-modelle/M-007-benachrichtigungsmatrix.md)). Mit den Standardeinstellungen erfährt eine neu eingeladene Person mit Rolle unterhalb `RESPONSIBLE` nichts davon ([RA-012](../../12-risiken-annahmen/RA-012-standardeinstellungen-unterdruecken-einladungsbenachrichtigung.md)).
