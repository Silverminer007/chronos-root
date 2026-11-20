---
id: AE-051
title: "Berechtigung über Teilnehmerrolle am Termin"
status: entwurf
typ: architekturentscheidung
datum: 2025-12-27
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/AuthorizationService.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/security/PrincipalContextFilter.java
  - backend/src/main/java/de/chronos_live/admin/presentation/
  - backend/src/test/java/de/chronos_live/chronos_date_api/presentation/AdminEndpointIntegrationTest.java
verknuepft: [AE-010, G-004, G-051, KZ-002, QS-007]
---

# AE-051: Berechtigung über Teilnehmerrolle am Termin

**Entscheidung:** Es gibt keine globale Benutzerrolle für Fachfunktionen. Was jemand an einem Termin darf, ergibt sich aus seiner `UserRole` an diesem Termin (Reihenfolge `NONE < GUEST < ATTENDANT < HELPER < RESPONSIBLE`); Gruppen gehören ihrem Besitzer (`owner_oidcid`). Alle Prüfungen stehen in `AuthorizationService` (`require…`-Methoden, Verstoß = `ForbiddenException`, 403), aufgerufen aus den Services:

| Aktion | Voraussetzung |
|---|---|
| Termin lesen, Nachricht senden | Rolle ≠ `NONE` |
| Termin ändern | mindestens `ATTENDANT` |
| Termin löschen/absagen, Teilnehmer oder Gruppe hinzufügen/entfernen, Rolle ändern | `RESPONSIBLE` |
| Teilnehmer hinzufügen | zusätzlich: Ziel ist befreundet |
| Gruppe zum Termin hinzufügen | zusätzlich: Handelnder ist Gruppenmitglied |
| Gruppenmitglied hinzufügen | Handelnder ist Mitglied und mit dem Ziel befreundet |
| Gruppe ändern/löschen, Mitglied entfernen | Gruppenbesitzer (Mitglieder können sich nicht selbst entfernen) |

Die Admin-API (`/api/v2/admin/*`, sechs Resources) verlangt die Keycloak-Realm-Rolle `ADMIN_API` (`@RolesAllowed`); `PrincipalContextFilter` markiert solche Anfragen als `adminRequest`, und alle `require…`-Methoden lassen sie dann ohne Rollenprüfung durch.

**Begründung:** Ein Termin ist die natürliche Berechtigungsgrenze der Fachlichkeit (Verantwortliche, Teilnehmer, Gäste); zentrale Regeln verhindern verstreute Prüfungen. Der Admin-Bypass erlaubt Support und Auswertung über die reguläre Logik.

**Alternativen:** Globale Rollen in Keycloak (nur für `ADMIN_API` genutzt); Prüfung in den Resources.

**Konsequenzen:** Jeder Service muss die Prüfung selbst aufrufen; fehlt sie, gibt es keinen Auffangschutz. Der Admin-Bypass hängt an der Pfadprüfung `api/v2/admin/` im Filter und an `@RolesAllowed`.
