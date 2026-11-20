---
id: AE-055
title: "Gruppeneinladung als abgeleitete Einzelteilnahmen"
status: entwurf
typ: architekturentscheidung
datum: 2025-12-27
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/AppointmentParticipationService.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/domain/AppointmentGroupParticipation.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/domain/AppointmentParticipation.java
verknuepft: [AE-048, AE-050, G-003, G-006, G-008]
---

# AE-055: Gruppeneinladung als abgeleitete Einzelteilnahmen

**Entscheidung:** Wird eine Gruppe zu einem Termin eingeladen, speichert das Backend die Einladung (`AppointmentGroupParticipation`, Rolle für alle Mitglieder) und legt **für jedes Mitglied eine eigene `AppointmentParticipation`** mit Status `PENDING` an; ihr Feld `groupParticipationId` enthält die ID der **Gruppe** (nicht der Gruppenteilnahme) als Herkunftsmarke. Spätere Mitgliederwechsel schreibt `AppointmentParticipationService` per Observer fort: `GroupMemberAddedEvent` legt Teilnahmen für alle Termine der Gruppe an, `GroupMemberRemovedEvent` löscht die abgeleiteten Teilnahmen des Mitglieds. Hat jemand bereits eine eigene Teilnahme am Termin, wird sie nicht überschrieben und bleibt unmarkiert.

**Begründung:** Zu- und Absage, Rolle und Push-Einstellungen gelten je Person; alle Abfragen (Teilnehmerliste, Zusagezähler, Berechtigung nach [AE-051](AE-051-berechtigung-ueber-teilnehmerrolle-am-termin.md)) arbeiten ohne Sonderfall nur auf Einzelteilnahmen. Eine dokumentierte Abwägung fehlt **(?)**.

**Alternativen:** Mitglieder zur Laufzeit aus der Gruppe auflösen; im Code nicht erkennbar erwogen **(?)**.

**Konsequenzen:** Die Einzelteilnahmen sind Kopien; die Synchronisation hängt an Ereignissen ([AE-048](AE-048-domaenenereignisse-ueber-cdi-events.md)). Wer einzeln und über eine Gruppe eingeladen ist, wird beim Entfernen der Gruppe nicht mit entfernt.
