---
id: AE-029
title: "Prometheus metrics and health endpoint parity with Quarkus"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2026-10-20
quellen:
  - kb/adr/ADR-2026-10-20-020-prometheus-metrics-health-parity.md
verknuepft: []
---

# AE-029: Prometheus metrics and health endpoint parity with Quarkus

Quelle: `kb/adr/ADR-2026-10-20-020-prometheus-metrics-health-parity.md` (Status im ADR: accepted, Datum: 2026-10-20). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

Ticket #102. The Helm chart ships a ServiceMonitor, Prometheus alerts and a Grafana dashboard for the Rust backend. The Java backend exposes Micrometer metrics on `/metrics` and SmallRye Health on `/q/health/*`. The Rust backend had the health routes but no `/metrics`, and the chart referenced metric names (`http_requests_total`, `http_request_duration_seconds_bucket`, `http_request_total`) that no backend emits.

## Entscheidung

- **`GET /metrics`** (public, no auth, as in Java) in Prometheus text format, served by the main listener. The chart's `metrics` service port already targets the Rust container port, so the ServiceMonitor (`port: metrics`, `path: /metrics`) needs no change.
- **HTTP series** use the Micrometer names: `http_server_requests_seconds_{count,sum,bucket}` (histogram) and `http_server_requests_seconds_max` with labels `exception="none"`, `method`, `outcome`, `status`, `uri`. `uri` is the matched route template with `{id}` placeholders (axum `:id` converted); requests matching no route get `NOT_FOUND`, like Micrometer. `/metrics` itself is not recorded (Java `ignore-patterns=^/metrics$`).
- **Buckets are emitted although Micrometer's defaults emit none**, because the chart's latency alert and dashboard use `histogram_quantile`. Extra series are a superset and do not break Java-based queries …

## Konsequenzen

(?) im ADR nicht ausgewiesen.
