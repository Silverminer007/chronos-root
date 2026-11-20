---
id: KZ-002
title: Autorisierung
status: entwurf
typ: konzept
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/AuthorizationService.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/domain/UserRole.java
  - backend/src/main/java/de/chronos_live/admin/presentation
verknuepft: [BB-006, BB-015, KZ-001, LZ-003]
---

# Autorisierung

Berechtigungen prüft ausschließlich der `AuthorizationService` (`require…`-Methoden, Verstoß ⇒ `ForbiddenException`, 403), aufgerufen von den Services; nicht JAX-RS und nicht die Repositories. Ausnahme: Admin-Ressourcen schützt `@RolesAllowed("ADMIN_API")` ([BB-015](../05-bausteine/BB-015-admin-bereich.md)). Maßstab ist die `UserRole` des Handelnden am Termin, geordnet `NONE` < `GUEST` < `ATTENDANT` < `HELPER` < `RESPONSIBLE`; für Gruppen zählen Mitgliedschaft und Besitz.

| Aktion | Voraussetzung |
|---|---|
| Termin lesen, Teilnehmer lesen, Nachrichten lesen und senden, Zu-/Absage | Rolle ≠ `NONE` |
| Termin ändern oder verschieben | mindestens `ATTENDANT` |
| Termin absagen oder löschen | `RESPONSIBLE` |
| Person einladen | `RESPONSIBLE` und Freundschaft mit der Person |
| Gruppe zu Termin hinzufügen | `RESPONSIBLE` und eigene Mitgliedschaft in der Gruppe |
| Person oder Gruppe entfernen, Rolle ändern | `RESPONSIBLE` |
| Gruppe umbenennen, löschen, Mitglied entfernen | Besitzer der Gruppe (Mitglied entfernen nie sich selbst) |
| Mitglied zur Gruppe hinzufügen | Mitglied der Gruppe und Freundschaft mit der Person |
| Gruppenmitglieder lesen | Mitglied der Gruppe |
| Admin-Endpunkte | Rolle `ADMIN_API`; Anfragen unter `/api/v2/admin/` überspringen alle obigen Prüfungen |

Nicht im `AuthorizationService` geprüft: Freundschaftsanfragen (Prüfung im `FriendshipService`: nur der Empfänger nimmt an oder lehnt ab, nur der Absender zieht zurück). Terminlisten liefern nur Termine, an denen der Benutzer teilnimmt (Filter im Repository).
