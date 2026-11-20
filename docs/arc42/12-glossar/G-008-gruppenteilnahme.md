---
id: G-008
title: "Gruppenteilnahme"
status: entwurf
typ: begriff
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/domain/AppointmentGroupParticipation.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/AppointmentParticipationService.java
verknuepft: [G-003, G-006]
---

# G-008: Gruppenteilnahme

**Code-Name:** `AppointmentGroupParticipation` (Tabelle `appointment_group_participations`, API-Feld `group_participants`); `ParticipantGroup` (Frontend)

Einladung einer ganzen [Gruppe](G-006-gruppe.md) zu einem Termin mit einer Rolle für alle Mitglieder. Jedes Mitglied erhält daraus eine eigene [Teilnahme](G-003-teilnehmer.md) (`groupParticipationId` = ID der Gruppe, [AE-055](../09-entscheidungen/AE-055-gruppeneinladung-als-abgeleitete-einzelteilnahmen.md)); später beitretende oder ausscheidende Mitglieder werden nachgeführt.
