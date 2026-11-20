---
id: FA-010
title: Rolle eines Teilnehmers ändern
status: entwurf
typ: anforderung
quellen: [backend/src/main/java/de/chronos_live/chronos_date_api/application/AppointmentParticipationService.java, frontend/app/components/ParticipantsStatusCard.vue]
verknuepft: [F-004]
---

# FA-010: Rolle eines Teilnehmers ändern

*Als Verantwortliche/r möchte ich die Rolle eines Teilnehmers ändern, damit Rechte zur Aufgabe passen.*

## Akzeptanzkriterien
- Nur Personen mit Rolle `RESPONSIBLE` ändern Rollen; andere erhalten `403`.
- Wählbar sind `NONE`, `GUEST`, `ATTENDANT`, `HELPER`, `RESPONSIBLE` ([M-006](../../05-modelle/M-006-rollenmatrix.md)); eine ungültige Rolle ergibt `400`.
- Die Zielperson muss Teilnehmer des Termins sein und darf die gewählte Rolle noch nicht haben (jeweils Fehler).
- Die neue Rolle wirkt sofort auf Rechte und Benachrichtigungsempfang. `NONE` entzieht den Zugriff auf den Termin.
- Die Rollenänderung erzeugt weder Nachricht noch Benachrichtigung an die betroffene Person ([M-007](../../05-modelle/M-007-benachrichtigungsmatrix.md)).
- Die Oberfläche bietet „Rolle ändern“ und „Entfernen“ in der Teilnehmerliste nur `RESPONSIBLE` und nicht für andere `RESPONSIBLE`.
