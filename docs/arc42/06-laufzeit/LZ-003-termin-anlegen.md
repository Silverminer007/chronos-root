---
id: LZ-003
title: Termin anlegen und Teilnehmer einladen
status: entwurf
typ: laufzeitszenario
quellen:
  - backend/.../AppointmentsResource.java
  - backend/.../AppointmentService.java
  - kb/adr/ADR-2026-10-03-011-appointment-mutations.md
verknuepft: [FA-001, FA-002, KZ-002, KZ-003]
---

# Termin anlegen und Teilnehmer einladen

1. `POST /api/v2/appointments` → `AppointmentsResource` → `AppointmentService`.
2. Der Ersteller wird Teilnehmer mit Rolle `RESPONSIBLE`.
3. `AppointmentCreatedEvent`; Einladungen über `POST /appointments/{id}/participants/{userId}` (Freundschaftsprüfung) oder `/groups/{groupId}` (Gruppenmitglieder erhalten eigene `AppointmentParticipation`).
4. Teilnahme- und Gruppen-Events lösen Push und Systemnachricht aus (LZ-005).
