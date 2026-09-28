import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';
import { config } from './config.js';
import { realisticScenario, stressScenario } from './scenarios/realistic.js';

// Test scenario options
export const options = {
  scenarios: {
    realistic: {
      executor: 'constant-vus',
      vus: config.scenarios.realistic.vus,
      duration: config.scenarios.realistic.duration,
      exec: 'runRealisticScenario',
      gracefulStop: '30s',
      env: { SCENARIO: 'realistic' },
    },
    // Stress scenario runs as a follow-up or separately
    // Can be enabled by setting K6_STRESS_TEST=true
  },
  thresholds: config.thresholds,
  ext: {
    loadimpact: {
      projectID: 3654204,
      name: 'Chronos Backend Performance Test (Realistic)',
    },
  },
};

// Check if stress scenario should run
if (__ENV.K6_STRESS_TEST === 'true') {
  options.scenarios.stress = {
    executor: 'constant-arrival-rate',
    rate: config.scenarios.stress.rps,
    duration: config.scenarios.stress.duration,
    exec: 'runStressScenario',
    gracefulStop: '30s',
    preAllocatedVUs: 10,
    env: { SCENARIO: 'stress' },
  };
}

export function runRealisticScenario() {
  realisticScenario();
}

export function runStressScenario() {
  stressScenario();
}

export function handleSummary(data) {
  return {
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
    '/tmp/k6-results.json': JSON.stringify(data, null, 2),
  };
}
