---
id: LZ-007
title: Gruppe erstellen und zu Termin einladen
status: entwurf
typ: laufzeitszenario
quellen:
  - backend/.../GroupsResource.java
  - kb/adr/ADR-2026-10-20-019-groups-friendships-endpoints.md
  - kb/adr/ADR-2026-10-05-012-appointment-groups-add-group.md
verknuepft: [FA-002, FA-006]
---

# Gruppe erstellen und zu Termin einladen

`POST /api/v2/groups` legt eine Gruppe mit Besitzer an; Mitglieder per `POST /groups/{groupId}/user/{userId}` (nur Freunde). `POST /appointments/{id}/groups/{groupId}` (nur `RESPONSIBLE`) erzeugt `AppointmentGroupParticipation` und für jedes Mitglied eine `AppointmentParticipation` mit `groupParticipationId`. Entfernen der Gruppe vom Termin: `DELETE …/groups/{groupId}`. Frontend-Ablauf „Gruppe beim Terminerstellen anlegen“: PR #344.
