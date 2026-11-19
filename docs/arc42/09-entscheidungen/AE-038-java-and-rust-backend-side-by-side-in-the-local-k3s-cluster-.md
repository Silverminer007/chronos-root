---
id: AE-038
title: "Java and Rust backend side by side in the local k3s cluster (parity run)"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2026-10-20
quellen:
  - kb/adr/ADR-2026-10-20-024-parity-deployment-local-k3s.md
verknuepft: []
---

# AE-038: Java and Rust backend side by side in the local k3s cluster (parity run)

Quelle: `kb/adr/ADR-2026-10-20-024-parity-deployment-local-k3s.md` (Status im ADR: accepted, Datum: 2026-10-20). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

Ticket #137. The contract harness (#136, ADR-2026-10-20-021) compares the Java reference and the Rust backend by URL; both need a database each and the same Keycloak realm. The workstation has a single-node k3s cluster without Keycloak and without Helm, a shared host (several agents, 98 GB disk) and a registry (ghcr) whose CI image of the Rust backend may lag behind the commit under test. The Rust backend must not reach staging or production before parity is proven (#140, ADR-2026-10-20-018).

## Entscheidung

- **Namespace `chronos-parity`**, nothing outside it is touched. Removing the namespace removes everything (Keycloak, PVC, Secrets).
- **Helm chart with an overlay**: `deployment/values-local.yaml` + new `deployment/values-parity.yaml` (namespace, resources, Rust environment). `values-local.yaml` itself is unchanged, so a plain local install keeps working. Helm 3.16.4 is installed into `~/.local/bin` (no root, no package manager); the steps are in `deployment/parity/README.md`.
- **Each backend owns its database, one PostgreSQL instance**: the chart gets `postgresql.extraDatabases` (a ConfigMap with `CREATE DATABASE` mounted into `/docker-entrypoint-initdb.d`). Java uses `chronos`, Rust `chronos_rust` …

## Konsequenzen

- A parity run is reproducible from the repository on any k3s with Helm; teardown is `kubectl delete namespace chronos-parity`.
- The Rust image is not deployed anywhere outside this namespace. Rolling it out to staging/production still needs the chart change and the `rust-backend` branch in `deployment-ci.yml` named in ADR-2026-10-20-018.
- The harness exercises the Rust backend with `APP_ENV=production` (a missing `DATABASE_URL` fails the start) and leader election enabled, as it would run in a cluster.
