#!/usr/bin/env bash
# Recreates both databases (the contract scenario needs empty ones) and restarts both backends.
set -euo pipefail

NS="${PARITY_NAMESPACE:-chronos-parity}"
kubectl -n "$NS" scale deploy/chronos-backend deploy/chronos-rust-backend --replicas=0
kubectl -n "$NS" wait --for=delete pod -l 'app in (chronos-backend,chronos-rust-backend)' --timeout=120s
for db in chronos chronos_rust; do
  kubectl -n "$NS" exec deploy/postgres -- psql -U postgres -c "DROP DATABASE IF EXISTS $db WITH (FORCE)" -c "CREATE DATABASE $db"
done
kubectl -n "$NS" scale deploy/chronos-backend deploy/chronos-rust-backend --replicas=1
kubectl -n "$NS" rollout status deploy/chronos-backend deploy/chronos-rust-backend --timeout=300s
