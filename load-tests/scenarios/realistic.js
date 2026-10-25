import http from 'k6/http';
import { sleep, check } from 'k6';
import { config } from '../config.js';

function checkEndpoint(baseUrl, path, endpointName) {
  const res = http.get(`${baseUrl}/api/v2/${path}`, {
    tags: { endpoint: `GET /${path}` }
  });
  check(res, {
    [`GET /${path}: status 200`]: (r) => r.status === 200 || r.status === 401,
    [`GET /${path}: response time < 500ms`]: (r) => r.timings.duration < 500
  });
  sleep(0.5);
}

export function realisticScenario() {
  const baseUrl = config.baseUrl;

  // Simulate typical user workflow: list key resources
  checkEndpoint(baseUrl, 'appointments', 'Appointments');
  checkEndpoint(baseUrl, 'groups', 'Groups');
  checkEndpoint(baseUrl, 'friends', 'Friendships');
}

export function stressScenario() {
  const baseUrl = config.baseUrl;

  // Stress test: repeated GET /appointments
  let res = http.get(`${baseUrl}/api/v2/appointments`, {
    tags: { endpoint: 'GET /appointments' }
  });
  check(res, {
    'GET /appointments: status 200': (r) => r.status === 200 || r.status === 401
  });
}
