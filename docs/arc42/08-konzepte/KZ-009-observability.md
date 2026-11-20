---
id: KZ-009
title: Observability
status: entwurf
typ: konzept
quellen:
  - backend/src/main/resources/application.properties
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/DebugLoggingService.java
  - deployment/values.yaml
verknuepft: [KT-007, VT-006, QA-007]
---

# Observability

- **Metriken:** Micrometer mit Prometheus-Export (`quarkus.micrometer.*`) auf der Management-Schnittstelle (Port 9000, Pfad `/metrics`, Selbstmessung ausgeschlossen). HTTP-Server-Metriken sind aktiv. Ressourcen (`api.*`) und Services (`service.*`) tragen `@Timed`, Beobachter eigene `Timer` (`observer.*`).
- **Health:** SmallRye Health, `/q/health/live` und `/q/health/ready` auf Port 9000 als Kubernetes-Probes ([VT-002](../07-verteilung/VT-002-kubernetes-ressourcen.md)).
- **Logs:** JBoss Logging; zusätzlich Datei `./logs/application.log` mit täglicher Rotation (10 Archive, Rotation beim Start); Debug-Zeilen tragen Präfixe `[Principal …][Appointment …]`; `DebugLoggingService` protokolliert einzelne Ereignisse; Log-Level über `QUARKUS_LOG_LEVEL` (Produktion `DEBUG`). Push-Versand steht zusätzlich in `push_notification_log` und über `GET /api/v2/admin/push/log` ([BB-015](../05-bausteine/BB-015-admin-bereich.md)).
- **Fehlererfassung:** Sentry ist nicht angebunden ([KT-008](../03-kontext/KT-008-sentry.md)).
- **Alarme:** [VT-006](../07-verteilung/VT-006-monitoring.md).
