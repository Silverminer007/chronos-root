---
title: Begriffe Deutsch ↔ Code-Namen
date: 2026-11-12
---

# Begriffe Deutsch ↔ Code-Namen

Die Oberfläche ist deutsch, Code und Tickets mischen Begriffe. Diese Tabelle ordnet die Fachbegriffe den Namen in Java (`backend/.../chronos_date_api/domain`), Rust (`rust-backend/src`) und Frontend (`frontend/app/types/index.ts`) zu. Allgemeine und technische Begriffe stehen in [glossary.md](glossary.md).

| Deutsch | Code-Name (Java / Rust / Frontend) | Kurzdefinition |
|---|---|---|
| Termin | `Appointment` (Java, Rust, Frontend) | Geplante Veranstaltung mit Name, Zeitraum, Ort, `AppointmentStatus` und `minimalAttendees` (Frontend: `minimal_attendees`). |
| Terminstatus | `AppointmentStatus`: `PLANNED`, `CANCELLED`, `DELETED`, `NOT_ENOUGH_ATTENDEES` | Zustand eines Termins; `NOT_ENOUGH_ATTENDEES` gilt, wenn zu wenige Zusagen vorliegen. |
| Teilnehmer | `AppointmentParticipation` (Java, Rust); `UserParticipant` (Frontend) | Beteiligung eines Benutzers an einem Termin mit Rolle und Zusage-Status. |
| Rolle (Teilnehmer) | `UserRole` (Java, Rust); `Role` (Frontend): `NONE`, `GUEST`, `ATTENDANT`, `HELPER`, `RESPONSIBLE` | Aufgabe eines Teilnehmers im Termin. |
| Zusage / Absage | `ParticipationStatus`: `APPROVED` (Zusage), `REJECTED` (Absage), `PENDING` (ausstehend) | Antwort eines Teilnehmers auf die Einladung zu einem Termin. |
| Gruppe | `Group` (Java, Rust, Frontend) | Benannte Menge von Benutzern mit Besitzer (`ownerOidcId`), die gesammelt zu Terminen eingeladen werden kann. |
| Gruppenmitglied | `GroupMember` (Java, Rust) | Zuordnung eines Benutzers zu einer Gruppe. |
| Gruppenteilnahme | `AppointmentGroupParticipation` (Java); `ParticipantGroup` (Frontend) | Einladung einer ganzen Gruppe zu einem Termin; die Mitglieder erhalten daraus eigene `AppointmentParticipation` (`groupParticipationId`). |
| Freundschaft | `FriendshipRequest` (Java, Frontend); `FriendshipStatus`: `PENDING`, `ACCEPTED`, `DECLINED`; `Friend` (Frontend) | Anfrage zwischen zwei Benutzern; angenommen (`ACCEPTED`) sind die Benutzer befreundet. |
| Erinnerung | `AppointmentReminderEvent`; `ReminderRule` (Java) | Zeitgesteuerte Push-Nachricht zu einem Termin, erzeugt durch Regeln der Reminder-Rule-Engine. |
| Systemnachricht | `Message` (Java, Rust, Frontend) | Nachricht im Termin-Chat; Systemnachrichten (z. B. bei Verschiebung) erzeugt `MessageService` im Namen des handelnden Benutzers. |
| Verschiebung | `AppointmentMovedEvent` (Java); `APPOINTMENT_MOVED` (Rust); Einstellung `appointment_moved` (Frontend) | Änderung von Start oder Ende eines Termins; löst Push-Benachrichtigung und Systemnachricht aus. |
| Push-Benachrichtigung | `PushSubscription` (Java); `NotificationSetting` (Java); `NotificationSettings` (Frontend) | Web-Push an Geräte eines Benutzers, je Ereignisart einstellbar. |
