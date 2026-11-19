---
id: KZ-009
title: Observability
status: entwurf
typ: konzept
quellen:
  - rust-backend/src/metrics.rs
  - deployment/templates/prometheus-alerts.yaml
verknuepft: [KT-007, VT-006]
---

# Observability

Metriken über `/q/metrics` (Micrometer/Prometheus bzw. Rust-`metrics.rs`), Gesundheitsendpunkte `/q/health/live|ready` (Parität laut ADR-020), strukturierte Logs (`tracing` in Rust), Fehler im Frontend optional über Sentry. Alarme und Dashboards siehe VT-006.
