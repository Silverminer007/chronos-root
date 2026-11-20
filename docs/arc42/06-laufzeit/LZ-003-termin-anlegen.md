---
id: LZ-003
title: Termin anlegen und Teilnehmer einladen
status: entwurf
typ: laufzeitszenario
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/presentation/AppointmentsResource.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/AppointmentService.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/AppointmentParticipationService.java
  - frontend/app/components/CreateAppointmentStepper.vue
verknuepft: [FA-001, FA-002, KZ-002, KZ-003, LZ-005]
---

# Termin anlegen und Teilnehmer einladen

1. `POST /api/v2/appointments` (`CreateAppointmentDto`) → `AppointmentsResource` → `AppointmentService.createAppointment`. Validiert: `name` nicht leer, `start` und `end` vorhanden, Ende nicht vor Start, `minimal_attendees` nicht negativ (sonst `ValidationException`, [KZ-005](../08-konzepte/KZ-005-fehlerbehandlung.md)). Speichert den Termin mit Status `PLANNED` und antwortet 201 mit dem Termin.
2. Das Event `AppointmentCreated` wird nach erfolgreicher Transaktion beobachtet: `AppointmentParticipationService` legt den Ersteller als Teilnehmer mit Rolle `RESPONSIBLE` und Status `PENDING` an (eigene Transaktion).
3. Einladen einer Person: `POST …/participants/{userId}` mit `user_role`. `AuthorizationService.requireAddUserToAppointment` verlangt `RESPONSIBLE` und eine Freundschaft zwischen Einladendem und Eingeladenem; ist die Person schon Teilnehmer, folgt `ValidationException`. Die Teilnahme beginnt mit `PENDING`; `AppointmentParticipationAddedEvent` löst Push `NEW_PARTICIPANT` aus ([LZ-005](LZ-005-verschiebung-mit-push.md) beschreibt die Zustellung).
4. Einladen einer Gruppe: [LZ-007](LZ-007-gruppe-einladen.md).

Das Frontend führt Anlegen und Einladen im `CreateAppointmentStepper` zusammen (Mindestteilnehmerzahl vorbelegt mit 5, 0 ohne Teilnehmer); die Pfade der Einladungsaufrufe weichen vom Backend ab ([BB-006](../05-bausteine/BB-006-java-schichten.md)).
