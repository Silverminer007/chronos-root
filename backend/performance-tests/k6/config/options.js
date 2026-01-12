// K6 shared options configuration

// Configurable thresholds (can be overridden via environment variables for CI)
const THRESHOLD_P95 = __ENV.THRESHOLD_P95 ? parseInt(__ENV.THRESHOLD_P95) : 2000;
const THRESHOLD_P99 = __ENV.THRESHOLD_P99 ? parseInt(__ENV.THRESHOLD_P99) : 5000;

export const defaultOptions = {
  // Use environment variables with sensible defaults
  vus: __ENV.K6_VUS ? parseInt(__ENV.K6_VUS) : 50,
  duration: __ENV.K6_DURATION || '2m',

  // Thresholds for pass/fail criteria
  thresholds: {
    http_req_duration: [`p(95)<${THRESHOLD_P95}`, `p(99)<${THRESHOLD_P99}`],
    http_req_failed: ['rate<0.05'],
    'http_req_duration{endpoint:appointments_list}': ['p(95)<1000'],
    'http_req_duration{endpoint:appointments_create}': ['p(95)<1500'],
    'http_req_duration{endpoint:appointments_get}': ['p(95)<500'],
    'http_req_duration{endpoint:user_get}': ['p(95)<300'],
    'http_req_duration{endpoint:groups_list}': ['p(95)<800'],
    'http_req_duration{endpoint:friends_list}': ['p(95)<600'],
    'http_req_duration{endpoint:settings_get}': ['p(95)<300'],
  },
};

// Simple load test options (for single command execution)
export const simpleOptions = {
  vus: __ENV.K6_VUS ? parseInt(__ENV.K6_VUS) : 50,
  duration: __ENV.K6_DURATION || '2m',
  thresholds: {
    http_req_duration: [`p(95)<${THRESHOLD_P95}`, `p(99)<${THRESHOLD_P99}`],
    http_req_failed: ['rate<0.05'],
  },
};

// Ramping scenarios for more realistic load patterns
export const rampingOptions = {
  scenarios: {
    ramp_up_down: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 25 },
        { duration: '1m', target: __ENV.K6_VUS ? parseInt(__ENV.K6_VUS) : 50 },
        { duration: '30s', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<2000', 'p(99)<5000'],
    http_req_failed: ['rate<0.05'],
  },
};
