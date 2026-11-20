---
id: G-045
title: "VAPID"
status: entwurf
typ: begriff
quellen:
  - backend/src/main/resources/application.properties
  - backend/src/main/java/de/chronos_live/chronos_date_api/config/PushConfig.java
verknuepft: [AE-053, G-013, RB-006]
---

# G-045: VAPID

**Code-Name:** Konfiguration `push.vapid.public`/`private`/`mailto` (Umgebung `VAPID_PUBLIC`, `VAPID_PRIVATE`, `VAPID_MAILTO`)

Schlüsselpaar, mit dem das Backend seine Web-Push-Nachrichten gegenüber den Push-Diensten signiert; der öffentliche Schlüssel dient dem Browser beim Abonnieren ([Push-Benachrichtigung](G-013-push-benachrichtigung.md)). Fehlen die Schlüssel, wird nicht gepusht.
