---
id: G-index
title: "12 Glossar"
status: entwurf
typ: index
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/domain/
  - frontend/app/types/index.ts
verknuepft: []
---

# 12 Glossar

Begriffe, die Leser:innen für Fachlichkeit und Code brauchen, je Datei (`G-nnn`) mit Code-Namen (Backend/Frontend). Allgemein bekannte Technikbegriffe (Cookie, JWT, DTO, ORM u. ä.) stehen hier nicht. IDs sind stabil; entfallene IDs werden nicht wiederverwendet.

## Fachbegriffe

| ID | Begriff | Code-Name |
|---|---|---|
| [G-001](G-001-termin.md) | Termin | `Appointment` |
| [G-002](G-002-terminstatus.md) | Terminstatus | `AppointmentStatus` |
| [G-003](G-003-teilnehmer.md) | Teilnehmer | `AppointmentParticipation` |
| [G-004](G-004-rolle-teilnehmer.md) | Rolle (Teilnehmer) | `UserRole` |
| [G-005](G-005-zusage-absage.md) | Zusage / Absage | `ParticipationStatus` |
| [G-006](G-006-gruppe.md) | Gruppe (und Mitglied) | `Group`, `GroupMember` |
| [G-008](G-008-gruppenteilnahme.md) | Gruppenteilnahme | `AppointmentGroupParticipation` |
| [G-009](G-009-freundschaft.md) | Freundschaft | `FriendshipRequest` |
| [G-010](G-010-erinnerung.md) | Erinnerung | `ReminderRule` |
| [G-011](G-011-nachricht-und-systemnachricht.md) | Nachricht / Systemnachricht | `Message` |
| [G-012](G-012-verschiebung.md) | Verschiebung | `AppointmentMovedEvent` |
| [G-013](G-013-push-benachrichtigung.md) | Push-Benachrichtigung | `PushSubscription`, `Settings` |
| [G-049](G-049-mindestteilnehmerzahl.md) | Mindestteilnehmerzahl | `minimalAttendees` |
| [G-051](G-051-admin-api.md) | Admin-API | `/api/v2/admin/*`, `ADMIN_API` |
| [G-052](G-052-rueckmeldung-rsvp.md) | Rückmeldung (RSVP) | `RSVP`, `sendRSVPReminder` |

## Technische Begriffe mit Chronos-Bedeutung

| ID | Begriff | Code-Name |
|---|---|---|
| [G-014](G-014-access-und-refresh-token.md) | Access- und Refresh-Token | `kc_access`, `kc_refresh`, `kc_expires` |
| [G-027](G-027-keycloak.md) | Keycloak | Realm `chronos` |
| [G-034](G-034-principalcontext.md) | PrincipalContext | `PrincipalContext` |
| [G-039](G-039-service-worker.md) | Service Worker | `push-sw.js` |
| [G-045](G-045-vapid.md) | VAPID | `push.vapid.*` |
| [G-048](G-048-oidcid.md) | oidcId | `userOidcId`, `user_id` |
| [G-050](G-050-profil-cache.md) | Profil-Cache | `user_profiles`, `IdentityPort` |
| [G-053](G-053-leader.md) | Leader | `isLeader()`, Lease `chronos-scheduler` |
| [G-054](G-054-domaenenereignis.md) | Domänenereignis | `application.events` |
| [G-055](G-055-pwa.md) | PWA (installierbare Web-App) | `manifest.webmanifest`, `push-sw.js` |
