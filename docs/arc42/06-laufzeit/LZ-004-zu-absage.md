---
id: LZ-004
title: Zu- und Absage
status: entwurf
typ: laufzeitszenario
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/presentation/AppointmentParticipationResource.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/AppointmentParticipationService.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/MessageService.java
verknuepft: [FA-003, KZ-003, LZ-011, BB-006]
---

# Zu- und Absage

```mermaid
sequenceDiagram
  participant T as Teilnehmer
  participant S as AppointmentParticipationService
  participant M as MessageService
  participant P as WebPushService
  participant V as ValidationService
  T->>S: POST /appointments/{id}/participation {status}
  S->>S: Status speichern
  S-->>M: StatusChanged (async)
  S-->>P: StatusChanged (async)
  S-->>V: StatusChanged (async)
  M->>M: Systemnachricht "<Name> hat zugesagt/abgesagt"
  P->>P: Push PARTICIPATION_STATUS_CHANGED an andere Teilnehmer
  V->>V: Terminstatus prüfen (LZ-011)
```

`POST /api/v2/appointments/{id}/participation` mit `status` `APPROVED` oder `REJECTED`. `changeParticipationStatus` verlangt irgendeine Rolle außer `NONE`, lehnt `PENDING` und den bereits gesetzten Status mit 400 ab und verlangt eine bestehende Teilnahme. Der Service löst `AppointmentParticipationStatusChangedEvent` synchron und zusätzlich asynchron aus; die drei Beobachter oben laufen asynchron in eigener Transaktion. Die Systemnachricht entsteht im Namen des Handelnden. Das Frontend bietet Zu-/Absage auch als Aktion der Push-Benachrichtigung an; beide Clients nutzen Pfade, die im Backend-Code anders lauten ([BB-006](../05-bausteine/BB-006-java-schichten.md)).
