#!/bin/bash
# Run Chronos performance load tests
# Usage: ./load-tests/run-tests.sh [realistic|stress|all] [backend-url]

set -e

SCENARIO=${1:-realistic}
BACKEND_URL=${2:-http://localhost:8080}

echo "=========================================="
echo "Chronos Performance Load Tests"
echo "Scenario: $SCENARIO"
echo "Backend: $BACKEND_URL"
echo "=========================================="
echo ""

case "$SCENARIO" in
  realistic)
    echo "Running realistic scenario (10 VUs, 5 min)..."
    k6 run load-tests/main.js \
      --env BACKEND_URL="$BACKEND_URL" \
      --env K6_STRESS_TEST=false
    ;;

  stress)
    echo "Running stress scenario (100 RPS, 5 min)..."
    k6 run load-tests/main.js \
      --env BACKEND_URL="$BACKEND_URL" \
      --env K6_STRESS_TEST=true
    ;;

  all)
    echo "Running realistic scenario first..."
    k6 run load-tests/main.js \
      --env BACKEND_URL="$BACKEND_URL" \
      --env K6_STRESS_TEST=false

    echo ""
    echo "Sleeping 30s before stress scenario..."
    sleep 30

    echo "Running stress scenario..."
    k6 run load-tests/main.js \
      --env BACKEND_URL="$BACKEND_URL" \
      --env K6_STRESS_TEST=true
    ;;

  *)
    echo "Usage: $0 [realistic|stress|all] [backend-url]"
    echo ""
    echo "Arguments:"
    echo "  scenario    - realistic, stress, or all (default: realistic)"
    echo "  backend-url - Backend URL (default: http://localhost:8080)"
    echo ""
    echo "Examples:"
    echo "  $0 realistic                                           # Local backend"
    echo "  $0 stress http://chronos-backend.chronos-prod:8080     # Kubernetes backend"
    echo "  $0 all http://staging.example.com:8080                # All scenarios"
    exit 1
    ;;
esac

echo ""
echo "Tests completed. Results saved to /tmp/k6-results.json"
