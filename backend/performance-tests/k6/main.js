import { sleep } from 'k6';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';
import { simpleOptions } from './config/options.js';
import { testAppointments } from './scenarios/appointments.js';
import { testParticipants } from './scenarios/participants.js';
import { testMessages } from './scenarios/messages.js';
import { testGroups } from './scenarios/groups.js';
import { testUser } from './scenarios/user.js';
import { testFriendships } from './scenarios/friendships.js';
import { testSettings } from './scenarios/settings.js';
import { testPush } from './scenarios/push.js';

// Export options for K6
export const options = simpleOptions;

// Setup function - runs once before the test
export function setup() {
  console.log('='.repeat(60));
  console.log('Starting Chronos API Performance Tests');
  console.log(`VUs: ${options.vus}, Duration: ${options.duration}`);
  console.log('='.repeat(60));
  return {};
}

// Default function - main test execution
export default function () {
  // User scenario first to ensure user exists
  testUser();
  sleep(0.5);

  // Core functionality tests
  testAppointments();
  sleep(0.5);

  testParticipants();
  sleep(0.5);

  testMessages();
  sleep(0.5);

  testGroups();
  sleep(0.5);

  testFriendships();
  sleep(0.5);

  testSettings();
  sleep(0.5);

  testPush();
  sleep(0.5);
}

// Teardown function - runs once after the test
export function teardown(data) {
  console.log('='.repeat(60));
  console.log('Chronos API Performance Tests Completed');
  console.log('='.repeat(60));
}

// Handle summary for custom output
export function handleSummary(data) {
  // Build summary object with all metrics
  const summary = {
    timestamp: new Date().toISOString(),
    test_run: {
      vus: options.vus,
      duration: options.duration,
    },
    metrics: {},
    endpoints: {},
  };

  // Extract metrics
  for (const [name, metric] of Object.entries(data.metrics)) {
    if (metric.type === 'trend') {
      const metricData = {
        avg: metric.values.avg ? metric.values.avg.toFixed(2) : null,
        min: metric.values.min ? metric.values.min.toFixed(2) : null,
        max: metric.values.max ? metric.values.max.toFixed(2) : null,
        med: metric.values.med ? metric.values.med.toFixed(2) : null,
        p90: metric.values['p(90)'] ? metric.values['p(90)'].toFixed(2) : null,
        p95: metric.values['p(95)'] ? metric.values['p(95)'].toFixed(2) : null,
        p99: metric.values['p(99)'] ? metric.values['p(99)'].toFixed(2) : null,
      };

      // Check if this is an endpoint-specific metric
      if (name.startsWith('http_req_duration_')) {
        const endpoint = name.replace('http_req_duration_', '');
        if (!summary.endpoints[endpoint]) {
          summary.endpoints[endpoint] = {};
        }
        summary.endpoints[endpoint].duration = metricData;
      } else {
        summary.metrics[name] = metricData;
      }
    } else if (metric.type === 'counter') {
      const metricData = {
        count: metric.values.count || 0,
        rate: metric.values.rate ? metric.values.rate.toFixed(2) : null,
      };

      if (name.startsWith('http_reqs_')) {
        const endpoint = name.replace('http_reqs_', '');
        if (!summary.endpoints[endpoint]) {
          summary.endpoints[endpoint] = {};
        }
        summary.endpoints[endpoint].requests = metricData;
      } else {
        summary.metrics[name] = metricData;
      }
    } else if (metric.type === 'rate') {
      const metricData = {
        rate: metric.values.rate ? (metric.values.rate * 100).toFixed(2) + '%' : '0%',
        passes: metric.values.passes || 0,
        fails: metric.values.fails || 0,
      };

      if (name.startsWith('http_req_failed_')) {
        const endpoint = name.replace('http_req_failed_', '');
        if (!summary.endpoints[endpoint]) {
          summary.endpoints[endpoint] = {};
        }
        summary.endpoints[endpoint].failures = metricData;
      } else {
        summary.metrics[name] = metricData;
      }
    }
  }

  // Generate formatted report
  const report = generateReport(summary);

  return {
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
    '/results/summary.json': JSON.stringify(summary, null, 2),
    '/results/k6-results.json': JSON.stringify(data, null, 2),
    '/results/report.txt': report,
  };
}

function generateReport(summary) {
  let report = '';
  report += '='.repeat(80) + '\n';
  report += 'CHRONOS API PERFORMANCE TEST RESULTS\n';
  report += '='.repeat(80) + '\n\n';
  report += `Timestamp: ${summary.timestamp}\n`;
  report += `VUs: ${summary.test_run.vus}, Duration: ${summary.test_run.duration}\n\n`;

  report += '-'.repeat(80) + '\n';
  report += 'ENDPOINT METRICS (Response Time in ms)\n';
  report += '-'.repeat(80) + '\n\n';

  const header = '| Endpoint                        | Avg     | Min     | Max     | P95     | P99     | Reqs   | Fail % |';
  const separator = '|' + '-'.repeat(33) + '|' + '-'.repeat(9) + '|' + '-'.repeat(9) + '|' + '-'.repeat(9) + '|' + '-'.repeat(9) + '|' + '-'.repeat(9) + '|' + '-'.repeat(8) + '|' + '-'.repeat(8) + '|';

  report += header + '\n';
  report += separator + '\n';

  const endpoints = Object.keys(summary.endpoints).sort();
  for (const endpoint of endpoints) {
    const data = summary.endpoints[endpoint];
    const d = data.duration || {};
    const r = data.requests || {};
    const f = data.failures || {};

    const row = `| ${endpoint.padEnd(31)} | ${(d.avg || 'N/A').toString().padStart(7)} | ${(d.min || 'N/A').toString().padStart(7)} | ${(d.max || 'N/A').toString().padStart(7)} | ${(d.p95 || 'N/A').toString().padStart(7)} | ${(d.p99 || 'N/A').toString().padStart(7)} | ${(r.count || 0).toString().padStart(6)} | ${(f.rate || '0%').toString().padStart(6)} |`;
    report += row + '\n';
  }

  report += '\n' + '='.repeat(80) + '\n';

  return report;
}
