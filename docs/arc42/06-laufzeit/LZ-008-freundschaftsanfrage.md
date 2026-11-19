---
id: LZ-008
title: Freundschaftsanfrage
status: entwurf
typ: laufzeitszenario
quellen:
  - backend/.../FriendshipResource.java
verknuepft: [FA-006]
---

# Freundschaftsanfrage

`POST /api/v2/friendships/requests` (`FriendshipRequestSentEvent`) → Empfänger sieht `GET …/requests/incoming` → `…/accept` oder `…/decline` (`FriendshipStatus` `ACCEPTED`/`DECLINED`); Absender kann mit `DELETE …/requests/{id}` zurückziehen, `DELETE …/friends/{friendId}` beendet die Freundschaft. Vorschläge: `GET …/suggestions` (Keycloak-Suche). Jeder Schritt löst ein Event für Push aus.
