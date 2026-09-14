# Rust Backend Kubernetes Deployment & Canary Strategy

This document describes the deployment strategy for the Rust backend to staging and production Kubernetes clusters, including canary rollout procedures and monitoring requirements.

## Deployment Architecture

### Staging Deployment

The Rust backend is deployed to the staging cluster with the following configuration:

- **Namespace**: `chronos-staging`
- **Initial Replicas**: 1 (can scale to 1-3 with autoscaling enabled)
- **Resource Requests**: 64 MiB memory, 50m CPU
- **Resource Limits**: 128 MiB memory, 500m CPU
- **Container Port**: 8080
- **Service Port**: 80 (load balanced through Kubernetes service)

### Health Checks

- **Liveness Probe**: `GET /q/health/live` (port 8080) — restarts pod if unresponsive
- **Readiness Probe**: `GET /q/health/ready` (port 8080) — removes from load balancer if not ready
- **Initial Delay**: 30s (liveness), 10s (readiness)
- **Period**: 10s (liveness), 5s (readiness)

## Canary Rollout Strategy

The canary deployment strategy involves a gradual rollout to minimize risk and detect issues early.

### Wave-Based Rollout

Deploy in phases, monitoring key metrics at each wave:

#### Wave 1: Canary (5% Traffic)
- **Duration**: 5 minutes
- **Replicas**: 1 out of 20 production pods
- **Success Criteria**:
  - Liveness probes passing for 2 consecutive check periods
  - Error rate < 1% (threshold: < 5 errors per 100 requests)
  - Latency p95 < 1000ms
  - Memory usage < 50 MiB per pod
- **Rollback Trigger**: Any metric exceeds threshold

#### Wave 2: Rolling (25% Traffic)
- **Duration**: 10 minutes
- **Replicas**: 5 out of 20 production pods
- **Success Criteria**:
  - All Wave 1 criteria still met
  - No increased pod restart rate
  - Gradual traffic shift with no spike in errors
- **Rollback Trigger**: Pod restart count > 3 or metrics exceed thresholds

#### Wave 3: Rolling (50% Traffic)
- **Duration**: 10 minutes
- **Replicas**: 10 out of 20 production pods
- **Success Criteria**:
  - All previous criteria sustained
  - Connection pool stable (no timeout spikes)
  - Database query performance stable
- **Rollback Trigger**: Sustained error rate > 1% or latency p95 > 1s

#### Wave 4: Full Rollout (100% Traffic)
- **Duration**: Complete after 30 minutes of Wave 3 success
- **Replicas**: 20 out of 20 production pods
- **Success Criteria**:
  - All previous criteria sustained for full 30-minute window
  - Ready for production validation
- **Rollback Trigger**: Error rate > 5% or sustained p95 latency > 2s

## Pre-Deployment Checklist

### 1. Staging Validation (before Wave 1)

- [ ] All unit and integration tests passing
- [ ] E2E test suite passing against staging backend
- [ ] Code review complete and approved
- [ ] Security scan passed (no vulnerabilities)
- [ ] Load test harness ready (k6 or similar)
- [ ] Prometheus and Grafana accessible
- [ ] Monitoring dashboards created and validated
- [ ] Alerts configured and tested
- [ ] Team notification channels set up
- [ ] Rollback runbook reviewed and approved

### 2. Metrics & Monitoring Setup

- [ ] Prometheus ServiceMonitor configured for Rust backend
- [ ] Health check endpoints responding correctly
- [ ] Metrics endpoint available at `/metrics`
- [ ] Grafana dashboard created with:
  - Response latency (p50, p95, p99)
  - Error rate per endpoint
  - Memory and CPU usage
  - Pod restart count
  - Request rate per second
- [ ] Alerts configured:
  - Memory > 50 MiB → warning
  - Latency p95 > 1000ms → warning
  - Latency p95 > 2000ms → critical
  - Error rate > 1% → warning
  - Error rate > 5% → critical
  - Pod restart count > 3 in 5 min → critical

### 3. Load Test Setup

- [ ] k6 load test script ready (`load-tests/staging.js`)
- [ ] Target endpoints identified for testing
- [ ] Load profile defined (ramp-up, steady-state, ramp-down)
- [ ] Expected baseline metrics documented
- [ ] Team trained on load test execution

## Canary Deployment Procedure

### Pre-Deployment

1. Create feature branch: `feature/39-<version>`
2. Tag the Rust backend image: `chronos-rust-backend:v<version>`
3. Update `values-staging.yaml` with new image tag
4. Verify Helm rendering: `helm template chronos deployment/ -f deployment/values-staging.yaml`
5. Create PR with deployment changes

### Wave Execution

For each wave:

1. **Update Replica Count**:
   ```bash
   kubectl patch deployment chronos-rust-backend \
     -n chronos-staging \
     -p '{"spec":{"replicas":'<wave-replicas>'}}'
   ```

2. **Monitor Metrics** (continuously during wave):
   - Open Grafana dashboard
   - Watch metrics for 5-10 minutes
   - Check Prometheus for spike anomalies
   - Verify pod logs for errors

3. **Evaluate Success**:
   - Check all success criteria met
   - Review alert history (no alerts fired)
   - Confirm no errors in pod logs

4. **Proceed or Rollback**:
   - If success: document observation, proceed to next wave
   - If failure: execute rollback immediately (see below)

### Wave Progression Summary

| Wave | Replicas | Duration | Rollback Condition |
|------|----------|----------|-------------------|
| 1 | 1 | 5 min | Error rate > 1% OR p95 latency > 1s |
| 2 | 5 | 10 min | Restart count > 3 OR metrics threshold exceeded |
| 3 | 10 | 10 min | Error rate > 1% OR p95 latency > 1s |
| 4 | 20 | 30 min success | Error rate > 5% OR p95 latency > 2s |

## Rollback Procedure

If any metric exceeds threshold during canary deployment:

### Immediate Actions

1. **Alert Team**: Post to #deployments Slack channel
2. **Pause Deployment**: Do not proceed to next wave
3. **Capture Metrics**: Screenshot Grafana dashboard for analysis
4. **Check Logs**: `kubectl logs -f -l app=chronos-rust-backend -n chronos-staging --tail=100`

### Rollback Execution

1. **Revert Image**:
   ```bash
   kubectl patch deployment chronos-rust-backend \
     -n chronos-staging \
     -p '{"spec":{"template":{"spec":{"containers":[{"name":"chronos-rust","image":"<previous-image>"}]}}}}'
   ```

2. **Verify Rollback**:
   ```bash
   kubectl rollout status deployment/chronos-rust-backend -n chronos-staging --timeout=5m
   ```

3. **Health Check**:
   ```bash
   kubectl get pods -n chronos-staging -l app=chronos-rust-backend
   kubectl logs -l app=chronos-rust-backend -n chronos-staging --tail=50
   ```

4. **Post-Mortem**:
   - Document what failed and when
   - Analyze logs and metrics
   - File issue for root cause analysis
   - Update runbook if needed

## Production Deployment (Future)

After staging canary succeeds and issues are resolved:

1. Tag production image: `chronos-rust-backend:latest` or `v<version>`
2. Update `values-prod.yaml` with production resource limits (may be higher)
3. Execute same canary strategy in production
4. Production waves may be slower (15 min intervals) for extra safety
5. Full production rollout requires sign-off from on-call engineer

## Monitoring & Dashboards

### Key Metrics to Monitor

- **Request Latency**: p50, p95, p99 (target: p95 < 1s for most endpoints)
- **Error Rate**: % of requests returning 5xx (target: < 1%)
- **Memory Usage**: Current and peak (target: < 50 MiB)
- **CPU Usage**: Request-based CPU (target: < 100m during load)
- **Pod Restarts**: Count per 5-minute window (target: 0)
- **Request Rate**: Requests per second (baseline during load test)

### Grafana Dashboard

Create a dashboard with the following panels:

1. **Request Rate** (req/s) - stacked by endpoint
2. **Latency** (p50, p95, p99) - by endpoint
3. **Error Rate** (%) - by status code
4. **Memory Usage** (MiB) - current and peak
5. **CPU Usage** (m) - by pod
6. **Pod Restarts** - count and reason
7. **HTTP Status Distribution** - by status code

## Staging Load Test

### Test Scenario

Run a 30-minute load test during Wave 3 to validate production readiness:

```
- Ramp-up: 2 minutes (0 → 10 req/s)
- Steady-state: 25 minutes (constant 10 req/s)
- Ramp-down: 3 minutes (10 → 0 req/s)
```

### Expected Baseline Metrics

- **Throughput**: 10 req/s sustained
- **Latency**: p95 < 500ms, p99 < 1000ms
- **Error Rate**: < 0.1%
- **Memory Peak**: < 100 MiB
- **CPU Peak**: < 200m

## Post-Deployment Validation

After full rollout (Wave 4):

1. Run full E2E test suite against production environment
2. Verify all user-facing endpoints functioning correctly
3. Check monitoring dashboard for anomalies
4. Confirm backup and restore procedures work
5. Document any issues found and track in GitHub

## Contacts & Escalation

- **On-Call Engineer**: [TBD]
- **Slack Channel**: #deployments
- **Status Page**: [TBD]

## Appendix: Common Issues & Solutions

### Issue: Pod CrashLoopBackOff

**Symptom**: Pod repeatedly restarts, readiness probe fails

**Diagnosis**:
```bash
kubectl describe pod <pod-name> -n chronos-staging
kubectl logs <pod-name> -n chronos-staging
```

**Common Causes**:
- Database connection string invalid
- Keycloak OIDC configuration missing
- Out of memory (increase limit)
- Port already in use

**Solution**: Check environment variables, secrets, and resource limits

### Issue: High Memory Usage (> 50 MiB)

**Symptom**: Memory usage climbing or spiking

**Diagnosis**:
- Check for memory leaks in business logic
- Verify connection pool size (not creating excessive connections)
- Review Tokio task spawning (not creating unbounded tasks)

**Solution**: Profile with heaptrack or valgrind (see ticket #38)

### Issue: Latency Spike (p95 > 1s)

**Symptom**: Sudden latency increase during canary

**Diagnosis**:
- Check database query performance (N+1 queries?)
- Verify no resource contention (CPU throttling?)
- Check for network issues between pods and DB

**Solution**: Run profiler, optimize hot paths

## References

- Kubernetes Deployments: https://kubernetes.io/docs/concepts/workloads/controllers/deployment/
- Canary Deployments: https://martinfowler.com/bliki/CanaryRelease.html
- Prometheus Monitoring: https://prometheus.io/docs/
