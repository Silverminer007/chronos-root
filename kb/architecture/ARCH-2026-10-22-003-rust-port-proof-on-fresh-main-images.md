---
title: ARCH-2026-10-22-003: Parity proof on fresh images of main (harness and explorative pass, local k3s)
date: 2026-10-22
component: Rust backend, Deployment, Contract tests
relates_to:
  - ARCH-2026-10-20-002: Final proof run of the Rust port (harness and explorative pass, local k3s)
  - ADR-2026-10-22-030: Token audience handling (#174)
  - ADR-2026-10-20-024: Java and Rust backend side by side in the local k3s cluster
---

## Result

Ticket #189. Both backends were rebuilt from `main` `6d25d10` (contains #173 error bodies and #174 `aud` claim) and compared in the namespace `chronos-parity` on fresh databases.

- **Contract harness: exit 0, 90 steps, 0 mismatches, 3 allowed differences, all `java-defect (#162)`.**
- **Explorative pass: exit 0, 106 steps, 0 differences, 11 allowed differences, all `java-defect (#162)`.** The 7 appointment error-body deviations of ARCH-2026-10-20-002 do not occur on the fresh image; no Rust code change was needed.
- `parity-user-2` (composite role `default-roles-chronos`, token with `aud: account`) is authorized by Rust like by Java (#174 proven on the live cluster).
- Nothing was deployed outside `chronos-parity`; the Rust backend is not in staging or production (#140).

## Environment

| Item | Value |
|---|---|
| Date | 2026-10-22, 01:40-02:05 UTC |
| Commit under test | `main` `6d25d10` |
| Rust image | `chronos-rust-backend:parity`, docker image id `sha256:58149bf234ac688b53094880ba6c8e2bdd2d07c56ac16dbdd0efc766fef49d2c`, containerd manifest `sha256:bc1b2c390734ae83d18867287ca897217ff4099c8af58892bb359676a84665b1`, pinned |
| Java image | `chronos-backend:parity-patched`, built from the same checkout with `deployment/parity/java-participation-routes.patch` applied for the build only, docker image id `sha256:0b79904991a81f4f1ca0a8be0ce574d4290466d1f68aeb5bd5ad21c10655f5e3`, containerd manifest `sha256:6216639946f4f3d96d9720b85db9026c7b75408a98b42871bf0a112ad191a9f5`, pinned |
| Cluster | k3s single node, namespace `chronos-parity`, Keycloak 26.0.7 dev mode, PostgreSQL 16 |

## Findings in the parity tooling (fixed here)

- The `keycloak-realm` ConfigMap in the cluster predated #174: `parity-user-2` had no `default-roles-chronos`, so its token had no `aud` and `run-contract.sh` correctly aborted. The ConfigMap was re-applied from `deployment/parity/chronos-realm.json`; the file itself was already correct. After changing the realm file, update the ConfigMap and run `reset-databases.sh` (Keycloak re-imports the realm on restart).
- The `aud` pre-check in `run-contract.sh` had an operator precedence bug (`"="*-len(p)%4` is a string format, not padding) and crashed with `TypeError` instead of checking the claim. Fixed with parentheses: `"="*(-len(p)%4)`.

## Commands

```bash
docker build -t chronos-rust-backend:parity rust-backend
git apply deployment/parity/java-participation-routes.patch
docker run --rm -v "$PWD/backend":/src -w /src -v maven-repo-parity:/root/.m2 maven:3.9-eclipse-temurin-21 \
  mvn -q -B package -Dmaven.test.skip=true -Dcheckstyle.skip=true -Dspotbugs.skip=true -Dpmd.skip=true
git apply -R deployment/parity/java-participation-routes.patch
docker build -f backend/src/main/docker/Dockerfile.jvm -t chronos-backend:parity-patched backend
# docker save | sudo k3s ctr -n k8s.io images import -, label io.cri-containerd.pinned=pinned (see deployment/parity/README.md)
kubectl -n chronos-parity create configmap keycloak-realm --from-file=deployment/parity/chronos-realm.json --dry-run=client -o yaml | kubectl apply -f -
deployment/parity/reset-databases.sh && deployment/parity/run-contract.sh --explore   # exit 0, 106 steps, 0 differences
deployment/parity/reset-databases.sh && deployment/parity/run-contract.sh             # exit 0, 90 steps, 0 mismatches
```
