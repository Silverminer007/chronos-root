# Chronos Performance Load Tests

This directory contains k6 load testing scenarios for the Chronos backend (Java/Quarkus). These tests measure baseline performance metrics: response latency, throughput, error rates, and resource usage.

## Overview

- **Realistic scenario**: 10 VUs over 5 minutes, simulating typical user workflows (list appointments, groups, friends)
- **Stress scenario**: 100 RPS sustained for 5 minutes against GET /appointments endpoint
- **Metrics**: HTTP response times (p50/p95/p99), error rates, JVM heap/GC via Prometheus

## Prerequisites

### Install k6

```bash
# macOS
brew install k6

# Linux (Ubuntu/Debian)
sudo apt-get install k6

# Or download from https://k6.io/docs/getting-started/installation/
```

### Running Backend

The Chronos backend (Quarkus) must be running and accessible:

```bash
cd backend
./mvnw quarkus:dev
# Backend starts at http://localhost:8080
```

### Prometheus + Grafana (Optional)

For live metric collection and dashboards:

```bash
# Start Prometheus (scrapes http://localhost:8080/q/metrics)
docker run -d --name prometheus \
  -p 9090:9090 \
  -v $(pwd)/prometheus.yml:/etc/prometheus/prometheus.yml \
  prom/prometheus

# Start Grafana
docker run -d --name grafana \
  -p 3000:3000 \
  -e GF_SECURITY_ADMIN_PASSWORD=admin \
  grafana/grafana

# Import dashboard: deployment/grafana-dashboards/java-backend-performance.json
```

## Usage

### Run Realistic Scenario

```bash
k6 run load-tests/main.js \
  --env BACKEND_URL=http://localhost:8080 \
  --env K6_STRESS_TEST=false
```

**Output**: Text summary to stdout, JSON results to `/tmp/k6-results.json`

### Run Stress Scenario

```bash
k6 run load-tests/main.js \
  --env BACKEND_URL=http://localhost:8080 \
  --env K6_STRESS_TEST=true
```

### Run Against Kubernetes Cluster

If backend is deployed in k8s (e.g., service DNS `chronos-backend.chronos-prod.svc.cluster.local`):

```bash
k6 run load-tests/main.js \
  --env BACKEND_URL=http://chronos-backend.chronos-prod.svc.cluster.local:8080
```

### Run Both Scenarios Sequentially

```bash
# First realistic
k6 run load-tests/main.js \
  --env BACKEND_URL=http://localhost:8080 \
  --env K6_STRESS_TEST=false

# Then stress
k6 run load-tests/main.js \
  --env BACKEND_URL=http://localhost:8080 \
  --env K6_STRESS_TEST=true
```

## Test Scenarios

### Realistic (config.js)
- **VUs**: 10 (virtual users)
- **Duration**: 5 minutes
- **Endpoints tested**:
  - GET /api/v2/appointments (list appointments)
  - GET /api/v2/groups (list groups)
  - GET /api/v2/friends (list friendships)
- **Thresholds**:
  - p95 response time < 1000ms
  - p99 response time < 2000ms
  - Error rate < 1%

### Stress
- **Rate**: 100 RPS (requests per second)
- **Duration**: 5 minutes
- **Target**: GET /api/v2/appointments (single endpoint, high load)
- **Thresholds**: Same as realistic

## Metrics Captured

### From Backend (Prometheus)

The Quarkus backend exposes Micrometer metrics at `GET http://localhost:8080/q/metrics`:

- `http_server_requests_seconds` — HTTP latency histogram (p50, p95, p99)
- `jvm_memory_used_bytes` — Heap memory used
- `jvm_memory_max_bytes` — Heap size
- `jvm_gc_pause_seconds` — GC pause duration
- `jvm_threads_live` — Active thread count
- `jvm_threads_peak` — Peak thread count

### From k6 (Client-side)

- `http_req_duration` — Request duration (min, avg, max, p95, p99)
- `http_req_failed` — Failed request rate
- `http_reqs` — Total request count

## Grafana Dashboard

Import the dashboard JSON for live visualization:

1. Open Grafana at http://localhost:3000
2. Go to **Dashboards → Import**
3. Upload `deployment/grafana-dashboards/java-backend-performance.json`
4. Select Prometheus as data source
5. View panels:
   - HTTP Response Latency Percentiles (p50/p95/p99)
   - Request Rate per Endpoint
   - JVM Heap Memory Usage
   - GC Pause Times
   - JVM Thread Count
   - HTTP Error Rate

## Troubleshooting

### "Connection refused"
- Ensure backend is running: `curl http://localhost:8080/api/v2/appointments`
- Or customize `BACKEND_URL`: `--env BACKEND_URL=http://your-host:8080`

### "401 Unauthorized"
- The backend requires authentication for most endpoints
- Tests handle 401 as expected (unauthenticated flow)
- To test authenticated flow, extend scenarios with auth setup

### No Prometheus metrics
- Verify Quarkus metrics endpoint: `curl http://localhost:8080/q/metrics`
- Check Prometheus configuration scrapes the backend
- Ensure Grafana datasource is configured to Prometheus

## Next Steps

- Run tests during development to detect performance regressions
- Use Grafana dashboard to identify bottlenecks
- Consider load testing as part of CI/CD pipeline (future ticket)
- Baseline metrics will inform optimization work (separate ticket)
