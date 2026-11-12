#!/usr/bin/env bash
# Nightly-Befehl von chronos-root für die Pod-Plattform (Vertrag: deployment/k8s/nightly/README.md des agent-orchestrator).
# Jobs: rust (fmt, clippy, cargo test gegen den Postgres-Sidecar über TEST_DATABASE_URL), frontend (npm ci, lint, typecheck, build),
# java (./mvnw verify ohne Testausführung: Kompilieren inkl. Testbaum und statische Prüfungen; die Quarkus-Tests brauchen Docker/Testcontainers).
# Ausgabe `ci-local: <job>: ok|FEHLER|SKIP`, fehlgeschlagene Rust-Tests als `test <name> ... FAILED` (Bisect und Folgetickets je Test).
set -u
P=ci-local
jobs="${NIGHTLY_ONLY_JOB:-${NIGHTLY_JOBS:-rust frontend java}}"
logs="${NIGHTLY_ROOT:-${TMPDIR:-/tmp}}/chronos-logs"; mkdir -p "$logs"
export CARGO_TARGET_DIR="${NIGHTLY_ROOT:-$HOME}/chronos-target" npm_config_cache="${NIGHTLY_ROOT:-$HOME}/.npm" MAVEN_OPTS="${MAVEN_OPTS:-} -Dmaven.repo.local=${NIGHTLY_ROOT:-$HOME}/.m2/repository"
[ -z "${TEST_DATABASE_URL:-}" ] || export DATABASE_URL="$TEST_DATABASE_URL"
rc=0
run() { local job=$1 what=$2; shift 2
  if ( "$@" ) >"$logs/$job.log" 2>&1; then echo "$P: $job: ok"
  else tail -n 60 "$logs/$job.log"; grep -E '^test .* \.\.\. FAILED$' "$logs/$job.log" | sort -u; echo "$P: $job: FEHLER ($what, Log $logs/$job.log)"; rc=1; fi; }
for j in $jobs; do
  case "$j" in
    rust)
      if [ -z "${TEST_DATABASE_URL:-}" ]; then echo "$P: rust: SKIP keine Datenbank (Sidecar fehlt)"; continue; fi
      run rust "cargo fmt/clippy/test" bash -c 'cd rust-backend && cargo fmt --check && cargo clippy --all-targets -- -D warnings && cargo test';;
    frontend) run frontend "npm ci/lint/typecheck/build" bash -c 'cd frontend && npm ci --no-audit --no-fund && npm run lint && npm run typecheck && npm run build';;
    java) run java "./mvnw verify -DskipTests" bash -c 'cd backend && ./mvnw -B -q -DskipTests verify';;
    *) echo "$P: $j: SKIP unbekannter Job";;
  esac
done
exit "$rc"
