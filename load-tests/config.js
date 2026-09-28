// Load test configuration
export const config = {
  // Backend URL from environment or default to localhost
  baseUrl: __ENV.BACKEND_URL || 'http://localhost:8080',

  // Scenario parameters
  scenarios: {
    realistic: {
      vus: 10,
      duration: '5m',
      description: 'Realistic user flow with 10 VUs over 5 minutes'
    },
    stress: {
      rps: 100,
      duration: '5m',
      description: 'Stress test: GET /appointments at 100 RPS for 5 minutes'
    }
  },

  // Thresholds for acceptance criteria
  thresholds: {
    http_req_duration: ['p(95)<1000', 'p(99)<2000'], // Response times
    http_req_failed: ['rate<0.01'], // Error rate < 1%
  }
};
