#!/bin/bash
set -e

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
PERF_TEST_DIR="${PROJECT_ROOT}/performance-tests"
DOCKER_DIR="${PERF_TEST_DIR}/docker"
RESULTS_DIR="${PERF_TEST_DIR}/results"

# Default values (can be overridden via environment or arguments)
K6_VUS="${K6_VUS:-50}"
K6_DURATION="${K6_DURATION:-2m}"
CLEANUP="${CLEANUP:-true}"
BUILD_APP="${BUILD_APP:-true}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

show_help() {
    echo "Usage: $0 [options]"
    echo ""
    echo "Chronos API Performance Test Runner"
    echo ""
    echo "Options:"
    echo "  --vus <number>       Number of virtual users (default: 50)"
    echo "  --duration <time>    Test duration (default: 2m)"
    echo "  --no-cleanup         Don't stop containers after test"
    echo "  --no-build           Don't rebuild the application"
    echo "  --help               Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                           # Run with defaults (50 VUs, 2 min)"
    echo "  $0 --vus 100 --duration 5m   # 100 users for 5 minutes"
    echo "  $0 --vus 10 --duration 30s   # Quick smoke test"
    echo ""
}

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --vus)
            K6_VUS="$2"
            shift 2
            ;;
        --duration)
            K6_DURATION="$2"
            shift 2
            ;;
        --no-cleanup)
            CLEANUP="false"
            shift
            ;;
        --no-build)
            BUILD_APP="false"
            shift
            ;;
        --help)
            show_help
            exit 0
            ;;
        *)
            log_error "Unknown option: $1"
            show_help
            exit 1
            ;;
    esac
done

# Create results directory
mkdir -p "${RESULTS_DIR}"

# Cleanup function
cleanup() {
    if [ "${CLEANUP}" = "true" ]; then
        log_info "Cleaning up containers..."
        cd "${DOCKER_DIR}"
        docker compose -f docker-compose.yml --profile test down -v --remove-orphans 2>/dev/null || true
    else
        log_warning "Containers left running (--no-cleanup specified)"
        log_info "To stop manually: cd ${DOCKER_DIR} && docker compose -f docker-compose.perf.yml --profile test down -v"
    fi
}

# Set trap for cleanup on exit
trap cleanup EXIT

echo ""
echo "========================================"
echo "  Chronos API Performance Test Runner"
echo "========================================"
echo ""

# Check for required tools
log_info "Checking prerequisites..."
command -v docker >/dev/null 2>&1 || { log_error "docker is required but not installed."; exit 1; }
command -v docker compose >/dev/null 2>&1 || { log_error "docker compose is required but not installed."; exit 1; }

# Build application if needed
if [ "${BUILD_APP}" = "true" ]; then
    log_info "Building Quarkus application..."
    cd "${PROJECT_ROOT}"
    ./mvnw -B clean package -DskipTests -q
    log_success "Application built successfully"
else
    log_info "Skipping application build (--no-build specified)"
fi

# Stop any existing containers
log_info "Stopping any existing test containers..."
cd "${DOCKER_DIR}"
docker compose -f docker-compose.yml --profile test down -v --remove-orphans 2>/dev/null || true

# Start infrastructure services
log_info "Starting infrastructure services (PostgreSQL, Keycloak)..."
docker compose -f docker-compose.yml up -d postgres-perf keycloak-db keycloak-perf

# Wait for PostgreSQL to be ready
log_info "Waiting for PostgreSQL to be ready..."
MAX_ATTEMPTS=30
ATTEMPT=0
until docker exec chronos-perf-postgres pg_isready -U chronos_perf > /dev/null 2>&1; do
    ATTEMPT=$((ATTEMPT + 1))
    if [ $ATTEMPT -ge $MAX_ATTEMPTS ]; then
        log_error "PostgreSQL failed to start within timeout"
        exit 1
    fi
    echo -n "."
    sleep 2
done
echo ""
log_success "PostgreSQL is ready"

# Wait for Keycloak to be ready
log_info "Waiting for Keycloak to be ready (this may take a minute)..."
ATTEMPT=0
MAX_ATTEMPTS=60
until curl -sf http://localhost:8180/health/ready > /dev/null 2>&1; do
    ATTEMPT=$((ATTEMPT + 1))
    if [ $ATTEMPT -ge $MAX_ATTEMPTS ]; then
        log_error "Keycloak failed to start within timeout"
        docker logs chronos-perf-keycloak --tail 50
        exit 1
    fi
    echo -n "."
    sleep 5
done
echo ""
log_success "Keycloak is ready"

# Build and start the API
log_info "Starting Chronos API..."
docker compose -f docker-compose.yml up -d --build chronos-api

# Wait for API to be ready
log_info "Waiting for Chronos API to be ready..."
ATTEMPT=0
MAX_ATTEMPTS=60
until curl -s http://localhost:8080/q/health/ready > /dev/null 2>&1; do #docker exec chronos-perf-api
    ATTEMPT=$((ATTEMPT + 1))
    if [ $ATTEMPT -ge $MAX_ATTEMPTS ]; then
        log_error "Chronos API failed to start within timeout"
        docker logs chronos-perf-api --tail 100
        exit 1
    fi
    echo -n "."
    sleep 3
done
echo ""
log_success "Chronos API is ready"

# Run K6 tests
echo ""
log_info "Running K6 performance tests..."
log_info "Configuration: VUs=${K6_VUS}, Duration=${K6_DURATION}"
echo ""

# Export environment variables for docker compose
export K6_VUS
export K6_DURATION

# Run K6 container
docker compose -f docker-compose.yml --profile test run --rm k6 run /scripts/main.js

echo ""
log_success "Performance tests completed!"
echo ""

# Display results summary
if [ -f "${RESULTS_DIR}/report.txt" ]; then
    log_info "Results Summary:"
    echo ""
    cat "${RESULTS_DIR}/report.txt"
fi

log_info "Full results available at: ${RESULTS_DIR}/"
log_info "  - summary.json: Aggregated metrics"
log_info "  - k6-results.json: Full K6 output"
log_info "  - report.txt: Human-readable report"
echo ""
