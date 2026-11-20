---
id: LZ-008
title: Freundschaftsanfrage
status: entwurf
typ: laufzeitszenario
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/presentation/FriendshipResource.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/FriendshipService.java
verknuepft: [FA-006, KZ-003, KZ-012]
---

# Freundschaftsanfrage

1. `POST /api/v2/friendships/requests?email=…` oder `?adresse_id=…`: Bei E-Mail sucht der Server per Keycloak und verlangt eine exakte Übereinstimmung (sonst 404). Abgelehnt werden Anfrage an sich selbst (400) sowie bestehende Freundschaft oder offene Anfrage (400). Antwort 201; `FriendshipRequestSentEvent` → Push `NEW_FRIENDSHIP_REQUEST` an den Empfänger.
2. Der Empfänger sieht sie unter `GET …/requests/incoming` (Absender unter `…/outgoing`) und entscheidet mit `POST …/requests/{id}/accept` oder `…/decline` (`FriendshipStatus` `ACCEPTED`/`DECLINED`); nur der Empfänger darf das, und nur für offene Anfragen. Annehmen und Ablehnen lösen Push `FRIENDSHIP_ACCEPTED` bzw. `FRIENDSHIP_DECLINED` an den Absender aus.
3. Der Absender zieht eine offene Anfrage mit `DELETE …/requests/{id}` zurück (kein Event); `DELETE …/friends/{friendId}` beendet die Freundschaft (Push `FRIENDSHIP_REMOVED`).
4. Vorschläge: `GET …/suggestions?search=` liefert Keycloak-Treffer ohne den Benutzer selbst und ohne bestehende Freunde (Limit 20); bei weniger als zwei Zeichen die ersten fünf Treffer der leeren Suche.
