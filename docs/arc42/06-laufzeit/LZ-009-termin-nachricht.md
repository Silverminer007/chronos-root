---
id: LZ-009
title: Nachricht im Termin senden
status: entwurf
typ: laufzeitszenario
quellen:
  - backend/.../MessageResource.java
  - kb/adr/ADR-2026-10-20-023-post-appointment-message.md
verknuepft: [FA-005]
---

# Nachricht im Termin senden

`POST /api/v2/appointments/{id}/messages` setzt irgendeine Rolle ungleich `NONE` voraus (`AuthorizationService`), speichert die `Message` und löst `MessageSentEvent` aus (Push an die übrigen Teilnehmer **(?)**). Lesen: `GET …/messages`. Systemnachrichten entstehen automatisch bei Zu-/Absage und Verschiebung.
