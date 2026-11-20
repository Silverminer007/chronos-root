---
id: M-005
title: Statusautomat der Freundschaft
status: entwurf
typ: modell
quellen: [backend/src/main/java/de/chronos_live/chronos_date_api/application/FriendshipService.java]
verknuepft: [F-008, FA-006]
---

# M-005: Statusautomat der Freundschaft

```mermaid
stateDiagram-v2
  [*] --> PENDING: Anfrage senden
  PENDING --> ACCEPTED: Empfänger nimmt an
  PENDING --> DECLINED: Empfänger lehnt ab
  PENDING --> [*]: Absender zieht zurück
  ACCEPTED --> [*]: eine Seite beendet
  DECLINED --> PENDING: Absender sendet erneut
```

`ACCEPTED` heißt befreundet; nur dann sind Einladungen zu Terminen und Gruppen möglich ([FA-002](../04-product-backlog/anforderungen/FA-002-personen-und-gruppen-einladen.md)).
