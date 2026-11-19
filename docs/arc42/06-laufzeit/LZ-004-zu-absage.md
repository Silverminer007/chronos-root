---
id: LZ-004
title: Zu- und Absage
status: entwurf
typ: laufzeitszenario
quellen:
  - backend/.../AppointmentParticipationResource.java
  - backend/.../AppointmentParticipationStatusConfirmationService.java
verknuepft: [FA-003, KZ-003]
---

# Zu- und Absage

`PUT /api/v2/appointments/{id}/participation` setzt `ParticipationStatus` (`APPROVED`/`REJECTED`/`PENDING`). Der Service löst `AppointmentParticipationStatusChangedEvent` aus; Systemnachricht („zugesagt“/„abgesagt“) wird im Termin-Chat im Namen des Benutzers erzeugt. Unterschreitet die Zahl der Zusagen `minimalAttendees`, wird der Terminstatus `NOT_ENOUGH_ATTENDEES` **(?)** gesetzt; Details: `AppointmentParticipationValidationService`.
