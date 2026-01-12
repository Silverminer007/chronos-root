#!/usr/bin/env node

/**
 * K6 Results Parser
 * Parses K6 JSON output and generates formatted reports
 *
 * Usage: node parse-results.js <results-file.json> [--format=table|json|markdown]
 */

const fs = require('fs');
const path = require('path');

// Parse command line arguments
const args = process.argv.slice(2);
const resultsFile = args[0];
const format = args.find((a) => a.startsWith('--format='))?.split('=')[1] || 'table';

if (!resultsFile) {
  console.error('Usage: node parse-results.js <results-file.json> [--format=table|json|markdown]');
  console.error('');
  console.error('Formats:');
  console.error('  table    - ASCII table (default)');
  console.error('  json     - JSON output');
  console.error('  markdown - Markdown table');
  process.exit(1);
}

// Read and parse results
let results;
try {
  const content = fs.readFileSync(resultsFile, 'utf8');
  results = JSON.parse(content);
} catch (error) {
  console.error(`Error reading results file: ${error.message}`);
  process.exit(1);
}

// Extract endpoint metrics from K6 output
function extractEndpointMetrics(data) {
  const endpointMetrics = {};

  for (const [name, metric] of Object.entries(data.metrics || {})) {
    // Look for endpoint-specific duration metrics (custom trend metrics)
    if (name.startsWith('http_req_duration_') && metric.type === 'trend') {
      const endpoint = name.replace('http_req_duration_', '');

      if (!endpointMetrics[endpoint]) {
        endpointMetrics[endpoint] = {};
      }

      endpointMetrics[endpoint].duration = {
        avg: metric.values.avg?.toFixed(2) || 'N/A',
        min: metric.values.min?.toFixed(2) || 'N/A',
        max: metric.values.max?.toFixed(2) || 'N/A',
        med: metric.values.med?.toFixed(2) || 'N/A',
        p90: metric.values['p(90)']?.toFixed(2) || 'N/A',
        p95: metric.values['p(95)']?.toFixed(2) || 'N/A',
        p99: metric.values['p(99)']?.toFixed(2) || 'N/A',
      };
    }

    // Look for request counts
    if (name.startsWith('http_reqs_') && metric.type === 'counter') {
      const endpoint = name.replace('http_reqs_', '');

      if (!endpointMetrics[endpoint]) {
        endpointMetrics[endpoint] = {};
      }

      endpointMetrics[endpoint].requests = {
        count: metric.values.count || 0,
        rate: metric.values.rate?.toFixed(2) || 'N/A',
      };
    }

    // Look for failure rates
    if (name.startsWith('http_req_failed_') && metric.type === 'rate') {
      const endpoint = name.replace('http_req_failed_', '');

      if (!endpointMetrics[endpoint]) {
        endpointMetrics[endpoint] = {};
      }

      endpointMetrics[endpoint].failures = {
        rate: (metric.values.rate * 100).toFixed(2) + '%',
        passes: metric.values.passes || 0,
        fails: metric.values.fails || 0,
      };
    }

    // Also check for tagged http_req_duration metrics
    if (name === 'http_req_duration' && metric.type === 'trend') {
      endpointMetrics['overall'] = endpointMetrics['overall'] || {};
      endpointMetrics['overall'].duration = {
        avg: metric.values.avg?.toFixed(2) || 'N/A',
        min: metric.values.min?.toFixed(2) || 'N/A',
        max: metric.values.max?.toFixed(2) || 'N/A',
        med: metric.values.med?.toFixed(2) || 'N/A',
        p90: metric.values['p(90)']?.toFixed(2) || 'N/A',
        p95: metric.values['p(95)']?.toFixed(2) || 'N/A',
        p99: metric.values['p(99)']?.toFixed(2) || 'N/A',
      };
    }
  }

  return endpointMetrics;
}

// Format as ASCII table
function formatAsTable(metrics) {
  const header =
    '| Endpoint                        | Avg (ms) | Min (ms) | Max (ms) | P95 (ms) | P99 (ms) | Requests | Fail % |';
  const separator =
    '|---------------------------------|----------|----------|----------|----------|----------|----------|--------|';

  const rows = Object.entries(metrics)
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([endpoint, data]) => {
      const d = data.duration || {};
      const r = data.requests || {};
      const f = data.failures || {};
      return `| ${endpoint.padEnd(31)} | ${(d.avg || 'N/A').toString().padStart(8)} | ${(d.min || 'N/A').toString().padStart(8)} | ${(d.max || 'N/A').toString().padStart(8)} | ${(d.p95 || 'N/A').toString().padStart(8)} | ${(d.p99 || 'N/A').toString().padStart(8)} | ${(r.count || 0).toString().padStart(8)} | ${(f.rate || '0%').toString().padStart(6)} |`;
    });

  return [header, separator, ...rows].join('\n');
}

// Format as Markdown
function formatAsMarkdown(metrics) {
  let md = '# K6 Performance Test Results\n\n';
  md += `**Generated:** ${new Date().toISOString()}\n\n`;
  md += '## Response Time Metrics by Endpoint\n\n';
  md +=
    '| Endpoint | Avg (ms) | Min (ms) | Max (ms) | P95 (ms) | P99 (ms) | Requests | Fail % |\n';
  md += '|----------|----------|----------|----------|----------|----------|----------|--------|\n';

  const rows = Object.entries(metrics)
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([endpoint, data]) => {
      const d = data.duration || {};
      const r = data.requests || {};
      const f = data.failures || {};
      return `| ${endpoint} | ${d.avg || 'N/A'} | ${d.min || 'N/A'} | ${d.max || 'N/A'} | ${d.p95 || 'N/A'} | ${d.p99 || 'N/A'} | ${r.count || 0} | ${f.rate || '0%'} |`;
    });

  md += rows.join('\n');

  md += '\n\n## Summary\n\n';
  const overall = metrics['overall'] || {};
  if (overall.duration) {
    md += `- **Average Response Time:** ${overall.duration.avg}ms\n`;
    md += `- **P95 Response Time:** ${overall.duration.p95}ms\n`;
    md += `- **P99 Response Time:** ${overall.duration.p99}ms\n`;
  }

  return md;
}

// Format as JSON
function formatAsJson(metrics) {
  return JSON.stringify(
    {
      timestamp: new Date().toISOString(),
      endpoints: metrics,
    },
    null,
    2
  );
}

// Main execution
const metrics = extractEndpointMetrics(results);

let output;
switch (format) {
  case 'markdown':
    output = formatAsMarkdown(metrics);
    break;
  case 'json':
    output = formatAsJson(metrics);
    break;
  case 'table':
  default:
    output = formatAsTable(metrics);
}

console.log(output);

// Also write to file if results directory exists
const resultsDir = path.dirname(resultsFile);
const ext = format === 'json' ? 'json' : format === 'markdown' ? 'md' : 'txt';
const outputFile = path.join(resultsDir, `parsed-results.${ext}`);
try {
  fs.writeFileSync(outputFile, output);
  console.error(`\nResults also written to: ${outputFile}`);
} catch (error) {
  // Ignore write errors
}
