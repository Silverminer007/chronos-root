---
id: BB-006
title: "Java-Backend: Schichten und Ressourcen (Ebene 2)"
status: entwurf
typ: baustein-ebene-2
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/presentation
  - backend/src/main/java/de/chronos_live/chronos_date_api/application
  - backend/src/main/java/de/chronos_live/chronos_date_api/infrastructure
  - backend/src/main/java/de/chronos_live/chronos_date_api/domain
  - frontend/app/stores/appointments.ts
verknuepft: [BB-005, LS-005, KZ-002, KZ-004, LZ-004, TD-012, TD-016, R-010]
---

# Java-Backend: Schichten und Ressourcen (Ebene 2)

| Schicht | Inhalt |
|---|---|
| presentation | `AppointmentsResource`, `AppointmentParticipationResource`, `MessageResource`, `GroupsResource`, `FriendshipResource`, `SettingsResource`, `PushResource`, `UserResource` |
| application | Termine: `AppointmentService`, `AppointmentParticipationService`, `AppointmentParticipationQueryService`, `AppointmentParticipationValidationService`, `AppointmentParticipationStatusConfirmationService`, `AppointmentReminderService`; Gruppen und Freunde: `GroupService`, `FriendshipService`, `FriendshipQueryService`; Nachrichten und Push: `MessageService`, `WebPushService`, `PushSubscriptionService`, `SettingsService`; Identität: `UserQueryService`, `KeycloakProfileService`; übergreifend: `AuthorizationService`, `LeaderElectionService`, `DebugLoggingService` (Debug-Logs der Ereignisse) |
| infrastructure | Panache-Repositories (`Appointment`, `AppointmentParticipation`, `Friendship`, `Group`, `Message`, `PushNotificationLog`, `PushSubscription`, `Settings`), `WebPushAdapter`, `LocalDbIdentityAdapter` |
| domain | JPA-Entitäten und Enums ([KZ-008](../08-konzepte/KZ-008-domaenenmodell.md)) |

Ressourcen sind `@PermitAll` bzw. unannotiert, `@Timed` und lesen den Principal aus `PrincipalContext`; Services bekommen die handelnde `oidcId` als Parameter ([KZ-001](../08-konzepte/KZ-001-principal-kontext.md)).

## Endpunkte (Präfix `/api/v2`)

| Ressource | Pfade |
|---|---|
| `/appointments` | `GET /` (Query `page`, `size`, `start`, `end`, `search`, `participants`, `messages`, `groups`; Antwort `PagedResponse`, Standard `size=10`), `GET /{id}`, `POST /`, `PATCH /{id}`, `DELETE /{id}` (setzt Status `DELETED`), `POST /{id}/cancel` |
| Teilnahme (volle Pfade unter `/appointments/{id}`) | `POST /participation` (Zu-/Absage), `GET /participation/status`, `PUT /participation/{userId}/role`, `GET /participants`, `POST` und `DELETE /participants/{userId}`, `POST` und `DELETE /groups/{groupId}` |
| `/appointments/{id}/messages` | `GET /`, `POST /` |
| `/groups` | `GET /`, `POST /`, `PATCH` und `DELETE /{group}`, `GET /{group}/users`, `POST` und `DELETE /{groupId}/user/{userId}` |
| `/friendships` | `GET /suggestions`, `POST /requests` (Query `email` oder `adresse_id`), `POST /requests/{id}/accept` und `/decline`, `DELETE /requests/{id}`, `GET /requests/incoming` und `/outgoing`, `GET /friends`, `DELETE /friends/{friendId}` |
| `/settings` | `GET`, `PUT` |
| `/push` | `GET /public-key`, `POST /subscribe`, `GET /status`, `DELETE /unsubscribe` |
| `/user` | `GET`, `POST` (liefert nur das JWT-Profil), `PATCH` (Keycloak-Profil ändern), `DELETE` (405), `GET /linked`, `DELETE /linked/{providerId}`, `GET /link/{provider}`, `GET /link/passkey`, `GET /unlink/passkey`, `GET /passkeys`, `GET /change-password` |

## Abweichung Frontend und Backend

Das Frontend (`stores/appointments.ts`, `push-sw.js`) ruft für Teilnahmeaktionen andere Pfade auf, als `AppointmentParticipationResource` anbietet:

| Aktion | Frontend ruft | Backend-Code bietet |
|---|---|---|
| Zusage / Absage | `POST …/participants/approve` bzw. `…/reject` | `POST …/participation` mit `status` |
| Eigener Status (Service Worker) | `GET …/participants/status` | `GET …/participation/status` |
| Person einladen / entfernen | `POST …/participants/users`, `DELETE …/participants/users/{id}` | `POST` / `DELETE …/participants/{userId}` |
| Rolle ändern | `PATCH …/participants/users/{id}` | `PUT …/participation/{userId}/role` |
| Gruppe einladen / entfernen | `POST …/participants/groups`, `DELETE …/participants/groups/{id}` | `POST` / `DELETE …/groups/{groupId}` |

`AppointmentParticipationResource` trägt keinen Klassen-`@Path`; die vollständigen Pfade stehen an den Methoden, was in JAX-RS gültig ist. Das Backend registriert die Frontend-Pfade also nicht. Abweichungen im Admin-Bereich: [BB-015](BB-015-admin-bereich.md); Risikobewertung: [req42 RA-003](../../req42/12-risiken-annahmen/RA-003-frontend-und-backend-schnittstellen-weichen-ab.md).
