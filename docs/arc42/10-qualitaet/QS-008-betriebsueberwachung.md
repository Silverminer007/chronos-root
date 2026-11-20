---
id: QS-008
title: "Betriebsüberwachung"
status: entwurf
typ: qualitaetsszenario
quellen:
  - deployment/templates/prometheus-alerts.yaml
  - deployment/templates/backend-service-monitor.yaml
  - deployment/values.yaml
  - backend/src/main/resources/application.properties
verknuepft: [QA-007, VT-006, TD-011]
---

# QS-008: Betriebsüberwachung

| | |
|---|---|
| **Reiz** | Ein Chronos-Pod fällt aus oder startet wiederholt neu, die Datenbank ist nicht erreichbar oder voll, CPU/Speicher sind knapp oder ein Zertifikat läuft ab. |
| **Antwort** | Prometheus-Alarme aus `prometheus-alerts.yaml` feuern. Das Backend liefert Micrometer-Metriken auf dem Management-Port 9000 (`/metrics`, Scrape alle 30 s) und Health unter `/q/health/{live,ready}`. |
| **Messung** | `ChronosPodDown` nach 2 min, `PostgreSQLDown` nach 1 min, `ChronosHighCPU` bei mehr als 0,8 CPU-Kernen (Rate des CPU-Zeitzählers, nicht Prozent des Limits; Schwelle wirkungslos, siehe [TD-011](../11-risiken/TD-011-cpu-alarm-schwelle-ueber-dem-cpu-limit.md)) und `ChronosHighMemory` > 90 % des Limits je 5 min, `PostgreSQLDiskFull` > 85 % nach 2 min, `ChronosPodRestarting` > 3 Neustarts je Stunde, `SSLCertExpiringSoon` unter 7 Tagen. |
| **Nachweis** | Regeln im Chart (Schwellen in `values.yaml`, `alerts.enabled`); ein Auslöse-Test existiert nicht **(?)**. Empfänger der Alarme: außerhalb des Repositories **(?)**. |
