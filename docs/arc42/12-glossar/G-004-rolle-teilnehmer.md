---
id: G-004
title: "Rolle (Teilnehmer)"
status: entwurf
typ: begriff
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/domain/UserRole.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/AuthorizationService.java
verknuepft: [G-003, AE-051]
---

# G-004: Rolle (Teilnehmer)

**Code-Name:** `UserRole` (Backend), `Role` (Frontend). Geordnet: `NONE < GUEST < ATTENDANT < HELPER < RESPONSIBLE`.

Aufgabe eines Teilnehmers an einem Termin; sie bestimmt seine Rechte ([AE-051](../09-entscheidungen/AE-051-berechtigung-ueber-teilnehmerrolle-am-termin.md)).
- `NONE`: keine Verbindung zum Termin (kein Zugriff), kein gespeicherter Wert.
- `GUEST`: Gast, `ATTENDANT`: reguläres Mitglied; ab `ATTENDANT` darf man den Termin ändern.
- `HELPER`: Helfer:in (Rechte wie `ATTENDANT`).
- `RESPONSIBLE`: Verantwortliche:r (Veranstalter:in); darf löschen, absagen, Teilnehmer, Gruppen und Rollen ändern.
