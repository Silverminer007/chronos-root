---
id: BB-006
title: Java-Backend: Schichten und Ressourcen (Ebene 2)
status: entwurf
typ: baustein-ebene-2
quellen:
  - backend/.../presentation
  - backend/.../application
  - backend/.../infrastructure
  - backend/.../domain
verknuepft: [BB-005, LS-005, KZ-002]
---

# Java-Backend: Schichten und Ressourcen (Ebene 2)

| Schicht | Inhalt |
|---|---|
| presentation | `AppointmentsResource`, `AppointmentParticipationResource`, `MessageResource`, `GroupsResource`, `FriendshipResource`, `SettingsResource`, `PushResource`, `UserResource` |
| application | `AppointmentService`, `AppointmentParticipation*Service`, `GroupService`, `FriendshipService`, `MessageService`, `SettingsService`, `PushSubscriptionService`, `WebPushService`, `KeycloakProfileService`, `AuthorizationService`, `LeaderElectionService` |
| infrastructure | Panache-Repositories, `WebPushAdapter`, `LocalDbIdentityAdapter` |
| domain | JPA-Entitäten (KZ-008) |

API-Präfix: `/api/v2/…`.
