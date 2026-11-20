---
id: BB-015
title: Admin-Bereich (Ebene 1)
status: entwurf
typ: baustein-ebene-1
quellen:
  - backend/src/main/java/de/chronos_live/admin
  - frontend/app/pages/admin
verknuepft: [FA-007, KZ-002, BB-016, BB-013, TD-014]
---

# Admin-Bereich (Ebene 1)

Eigenes Paket `de.chronos_live.admin` (`application`, `dto`, `presentation`) und zwei Frontend-Seiten. Alle Admin-Ressourcen tragen `@RolesAllowed("ADMIN_API")`; die Rolle kommt aus `realm_access/roles` des Tokens. Anfragen unter `/api/v2/admin/` setzt `PrincipalContextFilter` als Admin-Anfrage, und `AuthorizationService` überspringt dann alle Teilnehmer- und Besitzprüfungen ([KZ-002](../08-konzepte/KZ-002-autorisierung.md)). Fehlt die Rolle, antwortet Quarkus mit leerer 403.

| Ressource (`/api/v2/admin/…`) | Endpunkte |
|---|---|
| `users` | `GET /` (Seite aus Keycloak, `page`, `size`=20), `GET /{id}`, `DELETE /{id}` (löscht den Benutzer in Keycloak) |
| `appointments` | `GET /` (paginiert), `POST /` (Termin anlegen), `POST /{appointmentId}/participants/groups`, `POST /participants/status` (Status eines Teilnehmers setzen), `POST /message` (Nachricht mit Absender und Zeitstempel) |
| `groups` | `POST /` (mit Besitzer), `POST /{groupId}/users/{userId}` |
| `friendship` | `POST /befriend` (mehrere Benutzer gegenseitig befreunden) |
| `stats` | `GET /` (Zähler zu Benutzern, Terminen je Status, Teilnahmen, Gruppen, Freundschaften, Nachrichten, Push-Abos) |
| `push` | `POST /test/{userId}` (Testnachricht), `GET /log` (Filter `user_id`, `from`, `to`, `success`, `notification_type`, `page`, `size`=50) |

Services: `AdminUserService`, `AdminAppointmentService`, `AdminGroupService`, `AdminFriendshipService`, `AdminStatisticsService`; Termin-, Teilnahme- und Gruppenlogik wiederverwendet die Services der Hauptpakete. Frontend: `admin/index.vue` (Statistik, Benutzerliste, Umfragebenachrichtigung), `admin/push.vue` (Push-Log).

**Abweichung:** `admin/index.vue` ruft `/api/v2/admin/statistics`, `/api/v2/admin/user/` und `/api/v2/admin/survey/{id}/notify` auf; im Backend heißen die Pfade `…/stats` und `…/users`, Survey-Endpunkte gibt es nicht.
