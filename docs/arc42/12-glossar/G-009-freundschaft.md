---
id: G-009
title: "Freundschaft"
status: entwurf
typ: begriff
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/domain/FriendshipRequest.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/domain/FriendshipStatus.java
verknuepft: [G-006, G-004]
---

# G-009: Freundschaft

**Code-Name:** `FriendshipRequest` (Tabelle `friendship_requests`, `requesterId`/`addresseeId`); `FriendshipStatus`: `PENDING`, `ACCEPTED`, `DECLINED`; `Friend` (Frontend)

Anfrage zwischen zwei Benutzern; bei `ACCEPTED` sind sie befreundet. Nur Freunde dürfen einander zu Terminen oder [Gruppen](G-006-gruppe.md) hinzufügen.
