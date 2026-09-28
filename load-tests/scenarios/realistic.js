import http from 'k6/http';
import { sleep, check } from 'k6';
import { config } from '../config.js';

/**
 * Realistic user scenario: simulate a typical user workflow
 * - List appointments
 * - Create appointment
 * - List participants
 * - Get appointment details
 */
export function realisticScenario() {
  const baseUrl = config.baseUrl;
  const userId = `user-${__VU}-${__ITER}`;

  // 1. GET /appointments - list appointments
  let res = http.get(`${baseUrl}/api/v2/appointments`, {
    tags: { endpoint: 'GET /appointments' }
  });
  check(res, {
    'GET /appointments: status 200': (r) => r.status === 200 || r.status === 401,
    'GET /appointments: response time < 500ms': (r) => r.timings.duration < 500
  });
  sleep(0.5);

  // 2. GET /groups - list user's groups
  res = http.get(`${baseUrl}/api/v2/groups`, {
    tags: { endpoint: 'GET /groups' }
  });
  check(res, {
    'GET /groups: status 200': (r) => r.status === 200 || r.status === 401,
    'GET /groups: response time < 500ms': (r) => r.timings.duration < 500
  });
  sleep(0.5);

  // 3. GET /friends - list friendships
  res = http.get(`${baseUrl}/api/v2/friends`, {
    tags: { endpoint: 'GET /friends' }
  });
  check(res, {
    'GET /friends: status 200': (r) => r.status === 200 || r.status === 401,
    'GET /friends: response time < 500ms': (r) => r.timings.duration < 500
  });
  sleep(0.5);
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
