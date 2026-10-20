#!/usr/bin/env bash
# Recreates both databases (the contract scenario needs empty ones), restarts Keycloak (dev mode: the realm is
# re-imported, so users changed by an earlier run are fresh again) and restarts both backends.
set -euo pipefail

NS="${PARITY_NAMESPACE:-chronos-parity}"
kubectl -n "$NS" scale deploy/chronos-backend deploy/chronos-rust-backend --replicas=0
kubectl -n "$NS" wait --for=delete pod -l 'app in (chronos-backend,chronos-rust-backend)' --timeout=300s
for db in chronos chronos_rust; do
  kubectl -n "$NS" exec deploy/postgres -- psql -U postgres -c "DROP DATABASE IF EXISTS $db WITH (FORCE)" -c "CREATE DATABASE $db"
done
kubectl -n "$NS" rollout restart deploy/keycloak
kubectl -n "$NS" rollout status deploy/keycloak --timeout=300s
kubectl -n "$NS" scale deploy/chronos-backend deploy/chronos-rust-backend --replicas=1
kubectl -n "$NS" rollout status deploy/chronos-backend deploy/chronos-rust-backend --timeout=300s
# a terminating old pod would still be picked by `kubectl port-forward svc/...`
for app in keycloak chronos-backend chronos-rust-backend; do
  until [ "$(kubectl -n "$NS" get pods -l "app=$app" --no-headers | wc -l)" = 1 ]; do sleep 2; done
done
