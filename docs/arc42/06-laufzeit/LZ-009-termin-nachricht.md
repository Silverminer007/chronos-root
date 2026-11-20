---
id: LZ-009
title: Nachricht im Termin senden
status: entwurf
typ: laufzeitszenario
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/presentation/MessageResource.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/MessageService.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/WebPushService.java
verknuepft: [FA-005, KZ-002, LZ-005]
---

# Nachricht im Termin senden

`POST /api/v2/appointments/{id}/messages` verlangt irgendeine Rolle außer `NONE` (`requireSendMessage`), speichert die `Message` mit Absender und Zeitstempel und löst `MessageSentEvent` aus. Nach dem Commit sendet `WebPushService` an alle Teilnehmer außer dem Absender, deren Einstellung `appointment_message` es zulässt, eine Nutzlast mit `title` („<Name> schreibt zu <Termin>“) und `body` (Nachrichtentext) ohne `type` ([LZ-005](LZ-005-verschiebung-mit-push.md)). `GET …/messages` liefert die Nachrichten mit aufgelösten Absendernamen. Systemnachrichten (Zu-/Absage, Absage, Verschiebung) entstehen im `MessageService` im Namen des Handelnden ([LZ-004](LZ-004-zu-absage.md), [LZ-005](LZ-005-verschiebung-mit-push.md)).
