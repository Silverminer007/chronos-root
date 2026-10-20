---
title: ADR-2026-10-20-024: Java and Rust backend side by side in the local k3s cluster (parity run)
date: 2026-10-20
status: Accepted
supercedes: (none)
superceded_by: (none)
---

## Context

Ticket #137. The contract harness (#136, ADR-2026-10-20-021) compares the Java reference and the Rust backend by URL; both need a database each and the same Keycloak realm. The workstation has a single-node k3s cluster without Keycloak and without Helm, a shared host (several agents, 98 GB disk) and a registry (ghcr) whose CI image of the Rust backend may lag behind the commit under test. The Rust backend must not reach staging or production before parity is proven (#140, ADR-2026-10-20-018).

## Decision

- **Namespace `chronos-parity`**, nothing outside it is touched. Removing the namespace removes everything (Keycloak, PVC, Secrets).
- **Helm chart with an overlay**: `deployment/values-local.yaml` + new `deployment/values-parity.yaml` (namespace, resources, Rust environment). `values-local.yaml` itself is unchanged, so a plain local install keeps working. Helm 3.16.4 is installed into `~/.local/bin` (no root, no package manager); the steps are in `deployment/parity/README.md`.
- **Each backend owns its database, one PostgreSQL instance**: the chart gets `postgresql.extraDatabases` (a ConfigMap with `CREATE DATABASE` mounted into `/docker-entrypoint-initdb.d`). Java uses `chronos`, Rust `chronos_rust`. A second PostgreSQL pod would cost a PVC and ~128 MiB for no isolation the separate databases do not already give (separate schemas, `flyway_schema_history` of Java and `_sqlx_migrations` of Rust never meet). The init scripts run only on an empty volume; the default (`[]`) changes nothing for staging and production.
- **Backup CronJobs and their ServiceMonitor become optional** (`postgresql.backup.enabled`, default `true`): the cluster has no Prometheus CRDs and no S3, so the unconditional `ServiceMonitor` made `helm install` fail. Staging and production render the same manifests as before.
- **Rust `env` after `envFromSecrets`**: in the Rust Deployment the secret-backed variables are now emitted before the plain ones, so `DATABASE_URL: postgres://postgres:$(DB_PASSWORD)@postgres:5432/chronos_rust` can reference the secret through Kubernetes' `$(VAR)` expansion without adding a `DATABASE_URL` secret key (and a second place to keep the password).
- **Keycloak 26.0.7 in dev mode** as a plain manifest (`deployment/parity/keycloak.yaml`), not in the chart: the chart assumes an external Keycloak and a Keycloak subchart would be a long-term commitment for a test fixture. The realm `chronos` (`chronos-realm.json`) is imported at start; client `chronos-backend` (secret = `values-local.yaml` value), users `parity-user-1`, `parity-user-2`, `parity-admin` (role `ADMIN_API`) with fixed ids so the harness knows `CONTRACT_OTHER_USER_ID`, and a service account with `manage-users`/`view-users`. `KC_HOSTNAME=http://keycloak:8080` pins the token issuer, so a token fetched through `kubectl port-forward` is accepted by both backends, which reach Keycloak as `http://keycloak:8080`. State is in-container H2: a restart re-imports the realm. The credentials are throw-away values valid only in this namespace.
- **Images**: both are **built locally from the checkout** and imported into k3s (`docker save | k3s ctr images import`, then `docker rmi`; `pullPolicy: Never`; tags `chronos-rust-backend:parity` and `chronos-backend:parity-patched`, see the next bullet for the latter). The parity run must test the commit under test, not what CI last pushed: the ghcr `chronos-backend:latest` pulled on 2026-10-20 was built 2026-07-18 and lacked the whole admin API (every `/api/v2/admin/*` route except push answered 404), which would have made the Java reference wrong. The Java build runs in a `maven:3.9-eclipse-temurin-21` container (the host has only JDK 17) with `-Dmaven.test.skip=true`; the image is made from the repository's `Dockerfile.jvm`.
- **Java image carries a stored local patch** (`deployment/parity/java-participation-routes.patch`, applied with `git apply` before the build and reverted afterwards, the `backend/` sources in the repository stay unchanged). Reason: on the unmodified Java build `AppointmentParticipationResource` has no class-level `@Path`, so all eight participation/participant/group routes of the class answer `404 Unable to find matching target resource method`, and the harness could not compare any of them (every step "java 404, rust 200"). The patch adds `@Path("/api/v2/appointments")` on the class and makes the method paths relative; nothing else. This is a deliberate exception: the ticket rule "do not change Java code" is kept for the repository (no Java source change in a PR), but the compared reference is "Java as intended", not the shipped build. **It deviates from the coordinator's decision in #162**, which puts the Java defects (404 on the participation routes, NPE-500 on invalid input) into the allowlist as `java-defect`: the 404 cannot be allowlisted because the harness never allows `missing_route` diffs, so without the patch the participation endpoints would stay uncompared. The NPE-500 defects are allowlisted as decided (`java-defect (#162)` reasons in `contract-tests/allowlist.json`). The patch and the tag `parity-patched` are the only record needed to reproduce the run; the ARCH entry `ARCH-2026-10-20-001` describes the results. If the coordinator prefers the #162 line, drop the patch, rebuild the image as `parity`, and the participation steps appear as unallowlisted `missing_route` mismatches (and the Java fix goes to its owner). One copy of each image stays on disk (containerd's).
- **Resources are small** (host is shared): Postgres 128-384 Mi, Keycloak 512 Mi-1 Gi, Java 256-768 Mi (256 Mi from `values-local.yaml` is too small for Quarkus on JVM), Rust 64-128 Mi. Frontend is scaled to 0, ingress off.
- **Harness driver**: `deployment/parity/run-contract.sh` forwards the three services, fetches tokens by password grant and runs `python3 -m parity`; no credentials are stored besides the throw-away ones in the realm file.

## Consequences

- A parity run is reproducible from the repository on any k3s with Helm; teardown is `kubectl delete namespace chronos-parity`.
- The Rust image is not deployed anywhere outside this namespace. Rolling it out to staging/production still needs the chart change and the `rust-backend` branch in `deployment-ci.yml` named in ADR-2026-10-20-018.
- The harness exercises the Rust backend with `APP_ENV=production` (a missing `DATABASE_URL` fails the start) and leader election enabled, as it would run in a cluster.

## Alternatives considered

| Option | Result |
|---|---|
| Second PostgreSQL deployment for Rust | More pods and storage; no extra isolation over a second database. |
| Pull the CI images from ghcr | Can be older than `main` (the Java one was, by three months); slow pulls on this node. |
| Keycloak via a Helm subchart / operator | Heavy and long-lived for a test fixture. |
| `DATABASE_URL` as an additional chart secret key | Duplicates the password, touches the secret providers (Bitwarden, values). |
