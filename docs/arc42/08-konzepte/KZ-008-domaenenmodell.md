---
id: KZ-008
title: Domänenmodell
status: entwurf
typ: konzept
quellen:
  - backend/.../domain
  - kb/glossary/begriffe.md
verknuepft: [BB-006, BB-018, FA-001, FA-003]
---

# Domänenmodell

```mermaid
erDiagram
  Appointment ||--o{ AppointmentParticipation : hat
  Appointment ||--o{ Message : hat
  Appointment ||--o{ AppointmentGroupParticipation : lädt_ein
  Group ||--o{ GroupMember : enthält
  Group ||--o{ AppointmentGroupParticipation : wird_eingeladen
  FriendshipRequest }o--|| UserProfile : zwischen
```
Entitäten (Java `domain/`): `Appointment` (`name`, `description`, `venue`, `startTime`, `endTime`, `status`, `minimalAttendees`), `AppointmentParticipation` (Rolle `UserRole`, Status `ParticipationStatus`), `AppointmentGroupParticipation`, `Group`, `GroupMember`, `FriendshipRequest` (`FriendshipStatus`), `Message`, `Settings`/`NotificationSetting`/`AppointmentNotificationSetting`, `PushSubscription`, `PushNotificationLog`, `UserProfile`, `UserIdentity`, `ParticipationStatistik`. `AppointmentStatus`: `PLANNED`, `CANCELLED`, `DELETED`, `NOT_ENOUGH_ATTENDEES`. Begriffe deutsch ↔ Code: `kb/glossary/begriffe.md`.
