---
id: EIN-002
title: "Wesentliche Features"
status: entwurf
typ: einfuehrung
quellen:
  - backend/src/main/java/de/chronos_live
  - frontend/app/pages
  - frontend/server/api
verknuepft: [FA-001, FA-002, FA-003, FA-004, FA-005, FA-006, FA-007, FA-008, FA-009, FA-010, FA-011, FA-012, FA-013, FA-014, FA-015, FA-016, FA-017, FA-018, FA-019, FA-020]
---

# EIN-002: Wesentliche Features

Die Anforderungen stehen in [req42](../../req42/04-product-backlog/anforderungen/README.md). Diese Tabelle ordnet sie dem Einstiegspunkt im Code zu; das Zusammenspiel der Bausteine steht in [BB-019](../05-bausteine/BB-019-whitebox-gesamtsystem.md).

| Anforderung | Einstieg Backend | Einstieg Frontend |
|---|---|---|
| FA-001 Termine anlegen, ändern, verschieben, absagen, löschen | `AppointmentsResource` | `agenda.vue`, `appointment/[appointmentId].vue` |
| FA-002 Personen und Gruppen einladen | `AppointmentParticipationResource` (`…/participants`, `…/groups`) | `AddParticipantDialog.vue`, `GroupParticipantsCard.vue` |
| FA-003 Zu-/Absage, Mindestteilnehmerzahl | `AppointmentParticipationResource` (`POST …/participation`), `AppointmentParticipationValidationService` | `OwnParticipationStatusCard.vue` |
| FA-004 Push-Benachrichtigungen | `PushResource`, `WebPushService` | `usePush.ts`, `push-sw.js` |
| FA-005 Nachrichten zu einem Termin | `MessageResource` | `MessagesCard.vue` |
| FA-006 Freundschaften und Gruppen verwalten | `FriendshipResource`, `GroupsResource` | `friends.vue`, `groups/` |
| FA-007 Admin-API | `de.chronos_live.admin.presentation` | `admin/` |
| FA-008 Terminänderungen nachvollziehbar mitteilen | `AppointmentMovedEvent` → `MessageService`, `WebPushService` | `MessagesCard.vue` |
| FA-009 Terminliste paginiert | `AppointmentsResource` (`page`, `size`), `PagedResponse` | `stores/appointments.ts` |
| FA-010 Rolle eines Teilnehmers ändern | `PUT …/participation/{userId}/role` | `ParticipantsStatusCard.vue` |
| FA-011 Automatische Erinnerungen | `AppointmentReminderScheduler` und weitere Scheduler ([BB-012](../05-bausteine/BB-012-java-reminder.md)) | – |
| FA-012 Benachrichtigungen konfigurieren | `SettingsResource` | `settings.vue` |
| FA-013 Anmeldung über Keycloak, Onboarding | – (JWT-Validierung, [BB-016](../05-bausteine/BB-016-java-security.md)) | `server/api/auth`, `onboarding.vue` |
| FA-014 Profil und Konto verwalten | `UserResource`, `KeycloakProfileService` | `profile.vue` |
| FA-015 Nutzerumfrage | kein Endpunkt im Backend; die Seite ruft `/api/v2/survey/…` über den Proxy ([BB-002](../05-bausteine/BB-002-frontend-server-routen.md)) auf, das Backend kennt diese Route nicht | `survey/[surveyId].vue` |
| FA-016 Installation als PWA | – | `public/install.vue`, `manifest.webmanifest` |
| FA-017 Impressum und Datenschutz | – | `public/impressum.vue`, `public/datenschutz.vue` |
| FA-018 Termin teilen | – | siehe [FA-018](../../req42/04-product-backlog/anforderungen/FA-018-termin-teilen.md) |
| FA-019 Neuigkeiten-Hinweis | – | siehe [FA-019](../../req42/04-product-backlog/anforderungen/FA-019-neuigkeiten-hinweis.md) |
| FA-020 Termin im Detail ansehen | `AppointmentsResource` | `appointment/[appointmentId].vue` |
