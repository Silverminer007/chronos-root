---
id: VT-006
title: Monitoring und Alarme
status: entwurf
typ: verteilung
quellen:
  - deployment/templates/prometheus-alerts.yaml
  - deployment/templates/backend-service-monitor.yaml
  - deployment/values.yaml
verknuepft: [KT-007, KZ-009, QA-007]
---

# Monitoring und Alarme

`ServiceMonitor` für das Backend (Schalter `metrics.enabled` und `metrics.serviceMonitor.enabled`) und `PrometheusRule` (`alerts.enabled`) mit Schwellwerten aus `alerts.rules.*`:

| Alarm | Bedingung | Schwere |
|---|---|---|
| `ChronosPodDown` | Pod `chronos-*` nicht `Running`, 2 min | critical |
| `PostgreSQLDown` | Pod `postgres*` nicht `Running`, 1 min | critical |
| `ChronosHighCPU` | CPU-Rate > 0,8 Kerne, 5 min | warning |
| `ChronosHighMemory` | Speicher > 90 % des Limits, 5 min | warning |
| `PostgreSQLDiskFull` | Volume zu > 85 % belegt, 2 min | warning |
| `ChronosPodRestarting` | mehr als 3 Neustarts in 1 h | warning |
| `SSLCertExpiringSoon` | Zertifikat läuft in weniger als 7 Tagen ab, 1 h | warning |

Die Alarme setzen kube-state-metrics, cAdvisor und den Blackbox-Exporter voraus ([RA-010](../02-randbedingungen/RA-010-kubernetes-helm-deployment-mit-zwei-umgebungen.md)); Empfänger und Routing konfiguriert das Chart nicht. Last- und Leistungstests: [KZ-010](../08-konzepte/KZ-010-teststrategie.md).
