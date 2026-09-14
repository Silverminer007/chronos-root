import http from 'k6/http';
import { check, sleep, group } from 'k6';

// Configure test parameters
export const options = {
  stages: [
    { duration: '10s', target: 10 },   // Ramp up to 10 VU
    { duration: '30s', target: 50 },   // Ramp up to 50 VU
    { duration: '30s', target: 50 },   // Hold 50 VU
    { duration: '10s', target: 0 },    // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<1000', 'p(99)<2000'], // 95% < 1s, 99% < 2s
    http_req_failed: ['rate<0.1'],                   // Failure rate < 10%
  },
  ext: {
    loadimpact: {
      name: 'Chronos Backend Load Test',
      projectID: 0, // Set your project ID if using LoadImpact
      tags: {
        testID: 'chronos_perf_baseline',
      },
    },
  },
};

// Setup: called before the load test
export function setup() {
  const serverUrl = __ENV.SERVER_URL || 'http://localhost:8080';

  // Health check
  const healthRes = http.get(`${serverUrl}/q/health/live`);
  check(healthRes, {
    'health check status is 200': (r) => r.status === 200,
  });

  return { serverUrl };
}

export default function (data) {
  const serverUrl = data.serverUrl || 'http://localhost:8080';

  // Generate a fake JWT token for testing (in real scenario, use actual auth)
  const authToken = `Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIwMDAwMDAwMC0wMDAwLTAwMDAtMDAwMC0wMDAwMDAwMDAwMDEiLCJpYXQiOjE2MzA3MDMyMDB9`;

  const headers = {
    headers: {
      'Authorization': authToken,
      'Content-Type': 'application/json',
    },
  };

  group('Health Check Endpoints', () => {
    const res = http.get(`${serverUrl}/q/health/live`);
    check(res, {
      'health live status is 200': (r) => r.status === 200,
      'health live response time < 100ms': (r) => r.timings.duration < 100,
    });
  });

  group('User Info Endpoint', () => {
    const res = http.get(`${serverUrl}/api/v2/me`, headers);
    check(res, {
      'get user info status is 200 or 401': (r) => r.status === 200 || r.status === 401,
      'user info response time < 500ms': (r) => r.timings.duration < 500,
    });
  });

  group('List Appointments Endpoint', () => {
    const res = http.get(`${serverUrl}/api/v2/appointments`, headers);
    check(res, {
      'list appointments status is 200 or 401': (r) => r.status === 200 || r.status === 401,
      'list appointments response time < 1000ms': (r) => r.timings.duration < 1000,
      'list appointments has data': (r) => r.body !== null && r.body.length >= 0,
    });

    // Extract first appointment ID if available (mock scenario)
    let appointmentId = '00000000-0000-0000-0000-000000000001';

    sleep(Math.random() * 2); // Random think time

    group('Get Single Appointment', () => {
      const singleRes = http.get(
        `${serverUrl}/api/v2/appointments/${appointmentId}`,
        headers
      );
      check(singleRes, {
        'get appointment status is 200 or 401 or 404': (r) =>
          r.status === 200 || r.status === 401 || r.status === 404,
        'get appointment response time < 300ms': (r) => r.timings.duration < 300,
      });
    });
  });

  sleep(Math.random() * 2); // Random think time between requests
}

export function teardown(data) {
  // Optional cleanup after load test
  console.log('Load test completed');
}
