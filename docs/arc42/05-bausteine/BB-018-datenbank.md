---
id: BB-018
title: Datenbank (Ebene 1)
status: entwurf
typ: baustein-ebene-1
quellen:
  - backend/src/main/resources/db/migration
  - backend/src/test/resources/db/testdata/V99.0.0__test_fixtures.sql
verknuepft: [KT-005, KZ-006, KZ-008, LS-007]
---

# Datenbank (Ebene 1)

PostgreSQL, Schema allein durch Flyway-Migrationen unter `backend/src/main/resources/db/migration` ([KZ-006](../08-konzepte/KZ-006-flyway-migrationen.md)); Entitäten: [KZ-008](../08-konzepte/KZ-008-domaenenmodell.md).

| Migration | Inhalt |
|---|---|
| `V1.0.0` | Initialschema des Altsystems (`users`, `groups`, `event`, `attendance`, `message`, `pushsubscription`, `settings` u. a.) |
| `V1.1.0` | Neues Terminsystem (`appointment`, `appointment_participation`, `appointment_group_participations`, `friendship_requests`, `group_member`), Datenübernahme aus der alten Struktur, Entfernen der Alttabellen |
| `V1.2.0` | Benachrichtigungsspalten in `settings` mit Standardwerten |
| `V1.3.0` | Sequenzen für `users`, `groups`, `message`, `pushsubscription`, `settings` |
| `V1.4.0` | Gruppenmitglieder werden mit der Gruppe gelöscht (`ON DELETE CASCADE`) |
| `V1.5.0` | Zeitstempel und Indizes für die Freundschaftssuche |
| `V1.6.0` | Tabelle `push_notification_log` |
| `V1.7.0` | `last_seen` an `users` |
| `V1.8.0` | Sequenz für `push_notification_log` |
| `V2.0.0` | Entfernt `users`; Benutzerverweise werden `oidcid`-Spalten (Keycloak-Subject) |
| `V2.1.0` | Tabelle `user_profiles` als Profil-Cache, Befüllung aus `users` |

Test-Fixtures (`V99.0.0__test_fixtures.sql`) liegen unter `src/test/resources/db/testdata` und werden nur in Tests geladen. Flyway-Basis: `baseline-on-migrate` mit `baseline-version=1.0.0`.
