# Chronos API Performance Tests

K6-based performance testing suite for the Chronos Date API.

## Prerequisites

- Docker and Docker Compose
- Node.js 18+ (for results parsing)
- Maven 3.8+ (for building the application)

## Quick Start

Run the complete test suite with default settings (50 VUs, 2 minutes):

```bash
./scripts/run-perf-tests.sh
```

## Configuration Options

```bash
# Custom VUs and duration
./scripts/run-perf-tests.sh --vus 100 --duration 5m

# Skip application rebuild
./scripts/run-perf-tests.sh --no-build

# Keep containers running after test
./scripts/run-perf-tests.sh --no-cleanup

# Quick smoke test
./scripts/run-perf-tests.sh --vus 10 --duration 30s
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| K6_VUS | 50 | Number of virtual users |
| K6_DURATION | 2m | Test duration |

## Test Scenarios

The test suite covers all API endpoints:

- **Appointments**: CRUD operations, cancellation
- **Participants**: Status management, user/group participation
- **Messages**: Listing and sending messages
- **Groups**: CRUD operations, member management
- **User**: Registration and profile management
- **Friendships**: Suggestions, requests, friend management
- **Settings**: User preferences
- **Push**: Web push subscription management

## Results

Results are stored in `results/`:

- `summary.json`: Aggregated metrics per endpoint
- `k6-results.json`: Full K6 output
- `report.txt`: Human-readable report

### Parsing Results

```bash
# ASCII table format
node scripts/parse-results.js results/k6-results.json --format=table

# Markdown format
node scripts/parse-results.js results/k6-results.json --format=markdown

# JSON format
node scripts/parse-results.js results/k6-results.json --format=json
```

## Thresholds

Default pass/fail criteria:

- P95 response time < 2000ms
- P99 response time < 5000ms
- Error rate < 5%

## CI Integration

Performance tests run automatically on:
- Pushes to master
- Manual workflow dispatch

Results are:
- Uploaded as artifacts (30 day retention)
- Added to the GitHub Actions job summary

## Directory Structure

```
performance-tests/
├── docker/
│   ├── docker-compose.perf.yml      # Test environment
│   └── keycloak/
│       └── realm-export.json        # Keycloak realm with 50 test users
├── k6/
│   ├── config/
│   │   ├── options.js               # K6 options and thresholds
│   │   └── constants.js             # API endpoints and test users
│   ├── lib/
│   │   ├── auth.js                  # JWT token acquisition
│   │   ├── http-helpers.js          # HTTP request wrappers
│   │   └── data-generators.js       # Test data factories
│   ├── scenarios/
│   │   ├── appointments.js
│   │   ├── participants.js
│   │   ├── messages.js
│   │   ├── groups.js
│   │   ├── user.js
│   │   ├── friendships.js
│   │   ├── settings.js
│   │   └── push.js
│   └── main.js                      # Entry point
├── scripts/
│   ├── run-perf-tests.sh            # Orchestration script
│   └── parse-results.js             # Results parser
├── results/                         # Output directory (gitignored)
└── README.md
```

## Manual Docker Commands

Start infrastructure only:
```bash
cd docker
docker compose -f docker-compose.yml up -d postgres-perf keycloak-perf
```

Start everything:
```bash
cd docker
docker compose -f docker-compose.yml up -d
```

Run K6 tests manually:
```bash
cd docker
docker compose -f docker-compose.yml --profile test run --rm k6 run /scripts/main.js
```

Stop and cleanup:
```bash
cd docker
docker compose -f docker-compose.yml --profile test down -v
```
