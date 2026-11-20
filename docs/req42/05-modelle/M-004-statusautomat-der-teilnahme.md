---
id: M-004
title: Statusautomat der Teilnahme
status: entwurf
typ: modell
quellen: [backend/src/main/java/de/chronos_live/chronos_date_api/application/AppointmentParticipationService.java]
verknuepft: [F-005, FA-003]
---

# M-004: Statusautomat der Teilnahme

```mermaid
stateDiagram-v2
  [*] --> PENDING: eingeladen oder angelegt
  PENDING --> APPROVED: zusagen
  PENDING --> REJECTED: absagen
  APPROVED --> REJECTED: absagen
  REJECTED --> APPROVED: zusagen
```

Nur die betroffene Person ändert ihren Status; zurück auf `PENDING` und Wiederholung des Status sind unzulässig. Entfernen löscht die Teilnahme. Wirkung auf den Termin: [M-003](M-003-statusautomat-des-termins.md).
