---
id: AE-056
title: "Zeitpunkte als UTC-Instant, Fristregeln in UTC"
status: entwurf
typ: architekturentscheidung
datum: 2025-12-27
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/domain/Appointment.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/utils/AppointmentTimeUtil.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/AppointmentParticipationValidationService.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/reminder/
verknuepft: [AE-052, G-001, G-010, G-049]
---

# AE-056: Zeitpunkte als UTC-Instant, Fristregeln in UTC

**Entscheidung:** Alle Zeitpunkte (`startTime`, `endTime`, `timeStamp`) sind `java.time.Instant`; das Backend kennt keine Zeitzone eines Termins oder Benutzers. Fristregeln, die vom Wochentag abhängen (Erinnerungen, Mindestteilnehmer-Prüfung), werten den Wochentag in UTC aus (`AppointmentTimeUtil.isWeekday`: Montag bis Donnerstag, `isWeekend`: Freitag bis Sonntag; `ZoneOffset.UTC`). Die Dauer (< 24 h oder ab 24 h) entscheidet zusammen mit dem Wochentag, welche Regel gilt.

**Begründung:** nicht dokumentiert **(?)**; erkennbar ist der Verzicht auf Zeitzonenverwaltung, solange Nutzer in einer Zeitzone leben (Zielgruppe deutschsprachig, [Annahme RA-001](../../req42/12-risiken-annahmen/RA-001-zielgruppe-jugendorganisationen-im-deutschsprachigen-raum.md)).

**Alternativen:** Zeitzone je Termin oder Benutzer speichern; Fristen in lokaler Zeit (`Europe/Berlin`) rechnen.

**Konsequenzen:** Ein Termin am Freitag kurz nach Mitternacht Ortszeit zählt in UTC noch als Donnerstag und erhält die Wochentagsfristen; die Annahme ist in req42 festgehalten ([RA-008](../../req42/12-risiken-annahmen/RA-008-wochentagsregeln-gelten-in-utc.md)).
