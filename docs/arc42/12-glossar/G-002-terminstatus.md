---
id: G-002
title: "Terminstatus"
status: entwurf
typ: begriff
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/domain/AppointmentStatus.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/AppointmentService.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/AppointmentParticipationValidationService.java
verknuepft: [G-001, G-049, AE-050]
---

# G-002: Terminstatus

**Code-Name:** `AppointmentStatus`

| Wert | Bedeutung |
|---|---|
| `PLANNED` | findet statt, genug Zusagen möglich |
| `NOT_ENOUGH_ATTENDEES` | die [Mindestteilnehmerzahl](G-049-mindestteilnehmerzahl.md) ist gefährdet; kehrt zu `PLANNED` zurück, sobald wieder genug Teilnehmer nicht abgesagt haben |
| `CANCELLED` | von Verantwortlichen abgesagt; bleibt sichtbar |
| `DELETED` | gelöscht; wird nirgends mehr angezeigt, bleibt aber gespeichert ([AE-050](../09-entscheidungen/AE-050-termine-per-statuswechsel-loeschen-teilnahmen-hart.md)) |
