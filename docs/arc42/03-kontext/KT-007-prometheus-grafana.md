---
id: KT-007
title: Nachbarsystem Prometheus und Grafana
status: entwurf
typ: nachbarsystem
quellen:
  - deployment/templates/prometheus-alerts.yaml
  - deployment/templates/*service-monitor.yaml
  - deployment/templates/grafana-dashboard-rust-backend.yaml
verknuepft: [KZ-009, VT-006]
---

# Nachbarsystem Prometheus und Grafana

- **Rolle:** Metriken und Alarme.
- **Schnittstelle:** Beide Backends liefern `/q/metrics` (Micrometer bzw. `rust-backend/src/metrics.rs`) und `/q/health/{live,ready}`; ServiceMonitor-Ressourcen binden sie ein.
- **Alarme (PrometheusRule):** `ChronosPodDown`, `PostgreSQLDown`, `ChronosHighCPU`, `ChronosHighMemory`, `PostgreSQLDiskFull`, `ChronosPodRestarting`, `SSLCertExpiringSoon`, `RustBackend*`.
- **Dashboard:** `grafana-dashboard-rust-backend.yaml`.
