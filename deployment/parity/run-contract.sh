#!/usr/bin/env bash
# Runs the contract harness (contract-tests/) against the Java and Rust backends in the
# chronos-parity namespace. `--explore` runs deployment/parity/explore.py (multi-user flows, error paths)
# instead. Needs kubectl, curl and python3. Ports are forwarded to localhost.
set -euo pipefail

NS="${PARITY_NAMESPACE:-chronos-parity}"
JAVA_PORT="${PARITY_JAVA_PORT:-18080}"
RUST_PORT="${PARITY_RUST_PORT:-18081}"
JAVA_MGMT_PORT="${PARITY_JAVA_MGMT_PORT:-19000}"
KC_PORT="${PARITY_KC_PORT:-18082}"
CLIENT_ID="${PARITY_CLIENT_ID:-chronos-backend}"
CLIENT_SECRET="${PARITY_CLIENT_SECRET:-backend-secret}"
HERE="$(cd "$(dirname "$0")" && pwd)"

pids=()
cleanup() { for pid in "${pids[@]}"; do kill "$pid" 2>/dev/null || true; done; }
trap cleanup EXIT

forward() { kubectl -n "$NS" port-forward "$1" "$2:$3" >/dev/null & pids+=("$!"); }
forward svc/chronos-backend "$JAVA_PORT" 80
forward deploy/chronos-backend "$JAVA_MGMT_PORT" 9000
forward svc/chronos-rust-backend "$RUST_PORT" 80
forward svc/keycloak "$KC_PORT" 8080
for port in "$JAVA_PORT" "$JAVA_MGMT_PORT" "$RUST_PORT" "$KC_PORT"; do   # port-forward needs a moment
  for _ in $(seq 1 60); do (echo > "/dev/tcp/127.0.0.1/$port") 2>/dev/null && break; sleep 0.5; done
done

token() {
  curl -sf "http://localhost:$KC_PORT/realms/chronos/protocol/openid-connect/token" \
    -d grant_type=password -d "client_id=$CLIENT_ID" -d "client_secret=$CLIENT_SECRET" \
    -d "username=$1" -d "password=$1" | python3 -c 'import json,sys; print(json.load(sys.stdin)["access_token"])'
}

export CONTRACT_JAVA_URL="http://localhost:$JAVA_PORT"
export CONTRACT_JAVA_MANAGEMENT_URL="http://localhost:$JAVA_MGMT_PORT"   # Quarkus management interface
export CONTRACT_RUST_URL="http://localhost:$RUST_PORT"
export CONTRACT_USER_TOKEN="$(token parity-user-1)"
export CONTRACT_USER2_TOKEN="$(token parity-user-2)"
export CONTRACT_ADMIN_TOKEN="$(token parity-admin)"
export CONTRACT_OTHER_USER_ID="parity-user-2"

if [ "${1:-}" = "--explore" ]; then
  export EXPLORE_USER2_TOKEN="$CONTRACT_USER2_TOKEN"
  python3 "$HERE/explore.py"
else
  cd "$HERE/../../contract-tests"
  python3 -m parity "$@"
fi
