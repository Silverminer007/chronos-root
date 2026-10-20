---
title: ADR-2026-10-20-020: Prometheus metrics and health endpoint parity with Quarkus
date: 2026-10-20
status: Accepted
supercedes: (none)
superceded_by: (none)
---

## Context

Ticket #102. The Helm chart ships a ServiceMonitor, Prometheus alerts and a Grafana dashboard for the Rust backend. The Java backend exposes Micrometer metrics on `/metrics` and SmallRye Health on `/q/health/*`. The Rust backend had the health routes but no `/metrics`, and the chart referenced metric names (`http_requests_total`, `http_request_duration_seconds_bucket`, `http_request_total`) that no backend emits.

## Decision

- **`GET /metrics`** (public, no auth, as in Java) in Prometheus text format, served by the main listener. The chart's `metrics` service port already targets the Rust container port, so the ServiceMonitor (`port: metrics`, `path: /metrics`) needs no change.
- **HTTP series** use the Micrometer names: `http_server_requests_seconds_{count,sum,bucket}` (histogram) and `http_server_requests_seconds_max` with labels `exception="none"`, `method`, `outcome`, `status`, `uri`. `uri` is the matched route template with `{id}` placeholders (axum `:id` converted); requests matching no route get `NOT_FOUND`, like Micrometer. `/metrics` itself is not recorded (Java `ignore-patterns=^/metrics$`).
- **Buckets are emitted although Micrometer's defaults emit none**, because the chart's latency alert and dashboard use `histogram_quantile`. Extra series are a superset and do not break Java-based queries. The bounds are fixed in `src/metrics.rs` (`BUCKETS`): 5 ms, 10 ms, 25 ms, 50 ms, 100 ms, 250 ms, 500 ms, 1 s, 2.5 s, 5 s, 10 s (plus `+Inf`). They cover the 1 s p95 alert threshold with sub-second resolution; changing them changes the `le` label set, so dashboards and alerts using `histogram_quantile` must be re-checked together with them.
- **Process metrics** with equivalents: `process_uptime_seconds`, `process_start_time_seconds`, `system_cpu_count`, `process_resident_memory_bytes` (Linux only). JVM metrics (`jvm_*`) have no equivalent and are not emitted.
- **DB pool metrics** are named like Quarkus' Agroal metrics: `agroal_active_count`, `agroal_available_count` (label `datasource="default"`), plus `agroal_max_size` as an addition.
- **Health**: `/q/health/live` and `/q/health/ready` return the SmallRye JSON shape (`{"status":"UP|DOWN","checks":[...]}`); readiness runs `SELECT 1` and returns 503 with `DOWN` when the database is unreachable. `/health` stays plain `OK`.
- **Chart**: the Rust dashboard and alerts now query the Micrometer names. The names did not have to differ.
- **Implementation**: a small hand-written registry (`src/metrics.rs`) and an axum middleware, no new dependency. `build_router` creates the registry, so its signature is unchanged.

## Known differences

- `_max` is the all-time maximum, Micrometer uses a decaying window.
- Unmatched paths are answered by the auth layer (401) before any 404, so they appear as `status="401",uri="NOT_FOUND"`.
- Chart alerts are only partly at Java parity, on purpose: `RustBackendHighErrorRate` is the ratio of 5xx to all requests (`> 0.01`, i.e. 1%, matching its description) over the Micrometer series, and the latency alerts use `histogram_quantile` over the buckets above. `RustBackendHighMemory` is intentionally unchanged: it still uses the cAdvisor metric `container_memory_usage_bytes` (50 MiB threshold), not `process_resident_memory_bytes` or `jvm_*`, so it measures the container, not the process. `RustBackendPodDown` (kube-state-metrics) is unchanged as well. Moving them to the app's own series is a separate decision.
- The `exception` label is always `none`.

## Alternatives considered

| Option | Result |
|---|---|
| `metrics-exporter-prometheus` / `prometheus` crate | Extra dependency; label order and name control would need workarounds for what is a small amount of code. |
| Change the chart to the crate's default names | Breaks the contract that one dashboard/alert set works for both backends. |
