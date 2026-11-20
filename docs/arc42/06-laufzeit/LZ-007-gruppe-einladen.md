---
id: LZ-007
title: Gruppe erstellen und zu Termin einladen
status: entwurf
typ: laufzeitszenario
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/presentation/GroupsResource.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/GroupService.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/AppointmentParticipationService.java
verknuepft: [FA-002, FA-006, KZ-002, KZ-003]
---

# Gruppe erstellen und zu Termin einladen

1. `POST /api/v2/groups` (Name nötig) legt die Gruppe mit dem Handelnden als Besitzer an; `GroupCreatedEvent` macht ihn nach dem Commit zum Mitglied. Umbenennen (`PATCH`) und Löschen (`DELETE`) darf nur der Besitzer.
2. Mitglied hinzufügen: `POST /groups/{groupId}/user/{userId}` verlangt, dass der Handelnde Mitglied und mit dem Ziel befreundet ist. `GroupMemberAddedEvent` bewirkt Push `NEW_GROUP_MEMBER`/`ADDED_TO_GROUP` und trägt das neue Mitglied als `PENDING`-Teilnehmer in alle Termine ein, zu denen die Gruppe eingeladen ist. Entfernen (`DELETE`) darf nur der Besitzer, nie sich selbst; `GroupMemberRemovedEvent` löscht die Teilnahmen, die über diese Gruppe entstanden sind.
3. Gruppe zum Termin: `POST /appointments/{id}/groups/{groupId}` mit `user_role` verlangt `RESPONSIBLE` und dass der Handelnde Mitglied der Gruppe ist. Es entsteht ein `AppointmentGroupParticipation` und für jedes Mitglied, das noch nicht Teilnehmer ist, eine `AppointmentParticipation` mit Status `PENDING` und `groupParticipationId`; danach `AppointmentGroupParticipationAddedEvent` (Push `NEW_PARTICIPANT`).
4. Entfernen der Gruppe vom Termin: `DELETE …/groups/{groupId}` (nur `RESPONSIBLE`) löscht die Gruppenteilnahme und deren Einzelteilnahmen.

Im Frontend lässt sich beim Zusammenstellen der Teilnehmer eine Gruppe neu anlegen (`CreateGroupDialog` im `AppointmentParticipantPicker`).
