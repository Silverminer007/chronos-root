---
id: G-054
title: "Domänenereignis"
status: entwurf
typ: begriff
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/events/
verknuepft: [AE-048, KZ-003]
---

# G-054: Domänenereignis

**Code-Name:** Java-Records im Paket `application.events` (`AppointmentMovedEvent`, `AppointmentCancelledEvent`, `GroupMemberAddedEvent` u. a.), ausgelöst per CDI `Event<T>`

Meldung, dass in der Fachlogik etwas geschehen ist; Beobachter erzeugen daraus [Push-Benachrichtigungen](G-013-push-benachrichtigung.md), [Systemnachrichten](G-011-nachricht-und-systemnachricht.md) und Folgeaktionen ([AE-048](../09-entscheidungen/AE-048-domaenenereignisse-ueber-cdi-events.md)). Die Ereignisse leben nur im Prozess und werden nicht gespeichert.
