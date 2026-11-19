---
id: QA-007
title: Beobachtbarkeit
status: entwurf
typ: qualitaetsanforderung
quellen: [kb/adr/ADR-2026-10-20-020-prometheus-metrics-health-parity.md]
verknuepft: []
---

# QA-007: Beobachtbarkeit

**Qualitätsmerkmal:** Betreibbarkeit

## Szenario
Beide Backends liefern Prometheus-Metriken und einen Health-Endpunkt; Alerts und ein Grafana-Dashboard für das Rust-Backend existieren.

## Messbar durch
`deployment/templates/prometheus-alerts.yaml`, `backend-service-monitor.yaml`.
