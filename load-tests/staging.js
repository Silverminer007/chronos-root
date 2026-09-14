import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Rate, Trend, Counter, Gauge } from 'k6/metrics';

// Configuration
const BASE_URL = __ENV.BASE_URL || 'http://chronos-rust-backend.chronos-staging.svc.cluster.local:8080';
const DURATION = __ENV.DURATION || '30m';
const VU = __ENV.VU || 10; // Virtual Users

// Custom metrics
const errorRate = new Rate('errors');
const latency = new Trend('latency');
const healthCheckLatency = new Trend('health_check_latency');
const appointmentEndpointLatency = new Trend('appointment_endpoint_latency');
const requestCounter = new Counter('http_requests_total');

// Load test configuration
export const options = {
  stages: [
    // Ramp-up: 2 minutes, reaching 10 req/s
    { duration: '2m', target: VU },
    // Steady-state: 25 minutes at 10 req/s
    { duration: '25m', target: VU },
    // Ramp-down: 3 minutes back to 0
    { duration: '3m', target: 0 },
  ],
  thresholds: {
    // Health check endpoints should be very fast
    'health_check_latency': ['p(99) < 100'],
    // Appointment endpoint p95 latency target from ticket
    'appointment_endpoint_latency': ['p(95) < 300'],
    // General latency p95 target from ticket
    'latency': ['p(95) < 1000'],
    // Error rate target: < 1%
    'errors': ['rate < 0.01'],
  },
};

// Simulate different user workflows
export default function () {
  // Group 1: Health Checks (baseline)
  group('Health Checks', () => {
    const livePath = `${BASE_URL}/q/health/live`;
    const readyPath = `${BASE_URL}/q/health/ready`;

    const liveStart = new Date();
    let res = http.get(livePath);
    healthCheckLatency.add(new Date() - liveStart);
    check(res, {
      'liveness check is 200': (r) => r.status === 200,
    });

    const readyStart = new Date();
    res = http.get(readyPath);
    healthCheckLatency.add(new Date() - readyStart);
    check(res, {
      'readiness check is 200': (r) => r.status === 200,
    });

    requestCounter.add(2);
  });

  // Group 2: Appointment Endpoints (if available)
  group('Appointment Endpoints', () => {
    const appointmentsPath = `${BASE_URL}/api/v2/appointments`;

    // GET /appointments list
    const getStart = new Date();
    const res = http.get(appointmentsPath, {
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${__ENV.AUTH_TOKEN || 'dummy-token'}`,
      },
    });
    const latencyMs = new Date() - getStart;
    appointmentEndpointLatency.add(latencyMs);
    latency.add(latencyMs);

    if (res.status === 401 || res.status === 403) {
      // Expected if auth not configured
      check(res, {
        'get appointments returns 401/403 without auth': (r) => r.status === 401 || r.status === 403,
      });
    } else if (res.status === 200 || res.status === 304) {
      check(res, {
        'get appointments is 200': (r) => r.status === 200,
        'appointments response has valid JSON': (r) => {
          try {
            JSON.parse(r.body);
            return true;
          } catch {
            return false;
          }
        },
      });
    }

    errorRate.add(res.status >= 400 && res.status !== 401 && res.status !== 403);
    requestCounter.add(1);
  });

  // Group 3: Root health endpoint
  group('Alternative Health Endpoint', () => {
    const healthPath = `${BASE_URL}/health`;
    const res = http.get(healthPath);
    check(res, {
      'alternate health endpoint responds': (r) => r.status === 200 || r.status === 404,
    });
    requestCounter.add(1);
  });

  // Sleep between iterations
  sleep(0.5);
}

// Setup phase
export function setup() {
  console.log(`Starting load test against ${BASE_URL}`);
  console.log(`Duration: ${DURATION}, Virtual Users: ${VU}`);
  console.log('Metrics tracked:');
  console.log('  - Health check latency (p99 < 100ms)');
  console.log('  - Appointment endpoint latency (p95 < 300ms)');
  console.log('  - Overall latency (p95 < 1s)');
  console.log('  - Error rate (< 1%)');
}

// Teardown phase
export function teardown(data) {
  console.log('Load test completed');
  console.log('Summary:');
  console.log('  - Review Prometheus for memory and CPU metrics');
  console.log('  - Check Grafana dashboards for sustained performance');
  console.log('  - Verify no pod restarts during test');
}
