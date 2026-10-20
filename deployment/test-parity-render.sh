#!/usr/bin/env bash
set -euo pipefail

# Renders the chart with the parity overlay (values-local.yaml + values-parity.yaml) and checks the
# parts the parity run depends on (ticket #137), and that the defaults for staging/prod are unchanged.
# Usage: ./test-parity-render.sh

CHART_DIR="$(cd "$(dirname "$0")" && pwd)"
fail() { echo "❌ $1"; exit 1; }
render() { helm template chronos "$CHART_DIR" "$@" 2>&1; }

parity=$(render -n chronos-parity -f "$CHART_DIR/values-local.yaml" -f "$CHART_DIR/values-parity.yaml") \
    || fail "parity render failed: $parity"

grep -q 'CREATE DATABASE "chronos_rust"' <<<"$parity" || fail "extra database chronos_rust is not created"
grep -q 'mountPath: /docker-entrypoint-initdb.d' <<<"$parity" || fail "init scripts are not mounted into postgres"
grep -q 'name: chronos-rust-backend' <<<"$parity" || fail "rust backend is not rendered"
grep -q 'kind: CronJob' <<<"$parity" && fail "backup CronJobs rendered although postgresql.backup.enabled=false"
grep -q 'kind: ServiceMonitor' <<<"$parity" && fail "ServiceMonitor rendered although backup is disabled"

# $(DB_PASSWORD) is only expanded if DB_PASSWORD is defined before DATABASE_URL in the container env
rust=$(awk '/name: chronos-rust-backend/{f=1} f' <<<"$parity")
password_line=$(grep -n 'name: DB_PASSWORD' <<<"$rust" | head -1 | cut -d: -f1)
url_line=$(grep -n 'name: DATABASE_URL' <<<"$rust" | head -1 | cut -d: -f1)
[ -n "$password_line" ] && [ -n "$url_line" ] && [ "$password_line" -lt "$url_line" ] \
    || fail "DB_PASSWORD must precede DATABASE_URL in the rust container env"

# Defaults keep the production behaviour: backup resources rendered, no extra databases
prod=$(render -f "$CHART_DIR/values-local.yaml") || fail "default render failed: $prod"
grep -q 'name: postgres-backup$' <<<"$prod" || fail "backup CronJob missing with default values"
grep -q 'postgres-init' <<<"$prod" && fail "init ConfigMap rendered without extraDatabases"

echo "✅ parity overlay renders as expected"
