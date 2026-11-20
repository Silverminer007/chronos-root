---
id: TD-015
title: "Service Worker liest new_attendee, Backend sendet new_participant"
status: entwurf
typ: technische-schuld
quellen:
  - frontend/public/push-sw.js
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/WebPushService.java
verknuepft: [AE-053, G-013, BB-003]
---

# TD-015: Service Worker liest new_attendee, Backend sendet new_participant

Der Push-Payload für „Teilnehmer hinzugefügt“ enthält vom Backend die Felder `new_participant` (Benutzer- bzw. Gruppenname, `WebPushService`); `push-sw.js` liest `data.new_attendee`. Der Service Worker baut daraus „<Name> hat undefined zu <Termin> am <Datum> hinzugefügt“ (`body` für Typ `NEW_PARTICIPANT`).

**Auswirkung:** Falsche Push-Anzeige bei jedem „Teilnehmer hinzugefügt“ (Benutzer und Gruppe).

**Maßnahme:** Feldnamen angleichen; Payload-Schema in einem gemeinsamen Test prüfen.
