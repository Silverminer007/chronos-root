---
id: M-006
title: Rollenmatrix
status: entwurf
typ: modell
quellen: [backend/src/main/java/de/chronos_live/chronos_date_api/domain/UserRole.java, backend/src/main/java/de/chronos_live/chronos_date_api/application/AuthorizationService.java]
verknuepft: [F-004, FA-001, FA-002, FA-010]
---

# M-006: Rollenmatrix

Rollenordnung je Termin (aufsteigend): `NONE` < `GUEST` < `ATTENDANT` < `HELPER` < `RESPONSIBLE`. Wer keine Teilnahme hat, gilt als `NONE`.

| Aktion am Termin | Mindestrolle |
|---|---|
| Termin, Teilnehmer, Nachrichten lesen; Nachricht schreiben; zu-/absagen | `GUEST` |
| Termin ändern oder verschieben | `ATTENDANT` (fachlich offen, [RA-007](../12-risiken-annahmen/RA-007-berechtigung-zum-aendern-von-terminen-ist-weit-gefasst.md)) |
| Absagen, löschen, Person oder Gruppe einladen oder entfernen, Rolle ändern | `RESPONSIBLE` |

`HELPER` hat dieselben Rechte wie `ATTENDANT`, empfängt aber Benachrichtigungen mit Stufe `HELPER` ([M-007](M-007-benachrichtigungsmatrix.md)).

| Aktion an der Gruppe | Wer |
|---|---|
| Mitgliederliste lesen, Freunde hinzufügen, Gruppe zu einem Termin einladen | Mitglied |
| Umbenennen, löschen, Mitglieder entfernen | Besitzer (nie sich selbst entfernen) |

Administratoren (Rolle `ADMIN_API`) umgehen alle Prüfungen ([FA-007](../04-product-backlog/anforderungen/FA-007-admin-api.md)). Anzeigenamen der Rollen in der Oberfläche: `RESPONSIBLE` Organisator, `ATTENDANT` Teilnehmer, `HELPER` Helfer, `GUEST` Gast.
