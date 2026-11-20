---
id: QS-011
title: "Veraltete Push-Abos bereinigen"
status: entwurf
typ: qualitaetsszenario
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/infrastructure/WebPushAdapter.java
  - backend/src/test/java/de/chronos_live/chronos_date_api/application/WebPushServiceTest.java
verknuepft: [AE-053, G-013]
---

# QS-011: Veraltete Push-Abos bereinigen

| | |
|---|---|
| **Reiz** | Das Backend versendet eine Push-Nachricht an ein Abo, das der Push-Dienst nicht mehr kennt (HTTP 404 oder 410). |
| **Antwort** | `WebPushAdapter` löscht das Abo und protokolliert den Versuch in `push_notification_log`; andere Abos desselben Benutzers werden weiter bedient. |
| **Messung** | Nach dem ersten 404/410 wird an diesen Endpunkt nicht erneut gesendet. |
| **Nachweis** | `WebPushServiceTest` (Abdeckung des 404/410-Zweigs im Adapter nicht verifiziert **(?)**). |
