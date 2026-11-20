---
id: G-006
title: "Gruppe"
status: entwurf
typ: begriff
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/domain/Group.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/domain/GroupMember.java
verknuepft: [G-008, G-009]
---

# G-006: Gruppe

**Code-Name:** `Group` (Tabelle `groups`, Felder `groupName`, `ownerOidcId`); Mitglied: `GroupMember` (Tabelle `group_member`, Zuordnung Benutzer zu Gruppe)

Benannte Menge von Benutzern mit einem Besitzer, die gesammelt zu Terminen eingeladen werden kann ([Gruppenteilnahme](G-008-gruppenteilnahme.md)). Nur der Besitzer ändert oder löscht sie und entfernt Mitglieder; Mitglieder hinzufügen darf, wer selbst Mitglied und mit dem Neuen [befreundet](G-009-freundschaft.md) ist. Beim Hinzufügen oder Entfernen eines Mitglieds folgen dessen Teilnahmen an Terminen der Gruppe.
