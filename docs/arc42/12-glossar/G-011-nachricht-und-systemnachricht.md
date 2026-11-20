---
id: G-011
title: "Nachricht / Systemnachricht"
status: entwurf
typ: begriff
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/domain/Message.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/MessageService.java
verknuepft: [G-001, G-012]
---

# G-011: Nachricht / Systemnachricht

**Code-Name:** `Message` (Tabelle `message`, Felder `body`, `senderOidcId`, `timeStamp`; API `/api/v2/appointments/{id}/messages`)

Kurze Textnachricht innerhalb eines [Termins](G-001-termin.md); es gibt keinen Chat außerhalb davon. Lesen und Schreiben dürfen alle mit Rolle ≠ `NONE`.
**Systemnachricht:** automatisch erzeugte `Message` (Zu-/Absage, Absage des Termins, [Verschiebung](G-012-verschiebung.md)). Sie hat keine eigene Kennzeichnung im Modell; als Absender steht der handelnde Benutzer.
