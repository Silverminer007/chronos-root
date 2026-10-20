# Parity environment (Java and Rust side by side)

Local k3s only, namespace `chronos-parity`. Proves that the Java backend (reference) and the Rust backend
expose the same API contract (ticket #137, KB entry `kb/137-rust-java-parity-run.md`).
**Never deploy the Rust backend to staging or production from here** (see #140 / ADR-2026-10-20-018).

| Component | What | Database |
|---|---|---|
| `chronos-backend` | Java image built from the checkout, imported into k3s (Helm chart) | `chronos` |
| `chronos-rust-backend` | Rust image built from the checkout, imported into k3s (Helm chart, `rustBackend`) | `chronos_rust` |
| `postgres` | one PostgreSQL 16 instance (chart), two databases, one per backend | |
| `keycloak` | Keycloak 26.0.7 dev mode, realm `chronos` from `chronos-realm.json` (this directory) | in-container H2 |

Both backends use the realm `chronos`, client `chronos-backend` (secret `backend-secret`, the value of
`values-local.yaml`). Test users (password = user name): `parity-user-1`, `parity-user-2`, `parity-admin`
(role `ADMIN_API`). The client's service account has `manage-users`/`view-users` for the user and admin endpoints.
`parity-user-1` already has the name and e-mail the contract scenario patches the user to (`Parity Test`,
`parity@example.org`): Java sends a verification mail when the e-mail changes, and Keycloak here has no SMTP server, so a
real change would make Java answer 500 while Rust answers 200 (environment, not a parity finding).
The credentials are throw-away values for this namespace only.

## Prerequisites

- `kubectl` pointing at the k3s cluster, Docker, `sudo` for `k3s ctr`.
- Helm 3 (not installed by default): `curl -sL https://get.helm.sh/helm-v3.16.4-linux-amd64.tar.gz | tar xz && install linux-amd64/helm ~/.local/bin/`.
  Without Helm, `helm template` output cannot be replaced by anything simpler; the chart needs Helm.

## Images

- **Java**: the CI image `ghcr.io/silverminer007/chronos-backend:latest` was **older than `main`** on 2026-10-20 (built
  2026-07-18, without the admin endpoints), so it is not a valid reference. Build it from the checkout. The host has only
  JDK 17, so Maven runs in a container (tests are skipped: `-Dmaven.test.skip=true`; the quality gates run in `backend-ci.yml`):

  ```bash
  docker run --rm -v "$PWD/backend":/src -w /src -v maven-repo-parity:/root/.m2 maven:3.9-eclipse-temurin-21 \
    mvn -q -B package -Dmaven.test.skip=true -Dcheckstyle.skip=true -Dspotbugs.skip=true -Dpmd.skip=true
  docker build -f backend/src/main/docker/Dockerfile.jvm -t chronos-backend:parity backend
  docker save chronos-backend:parity | sudo k3s ctr -n k8s.io images import -
  docker rmi chronos-backend:parity; docker volume rm maven-repo-parity; sudo rm -rf backend/target   # target/ is owned by root
  ```

- **Pin the imported images**: images that exist only in containerd are not in any registry. Above ~85 % disk usage the kubelet
  garbage-collects images no pod uses (e.g. while a backend is scaled to 0), and the pod then fails with `ErrImageNeverPull`
  (happened on 2026-10-20). Mark them as pinned after the import:
  `sudo k3s ctr -n k8s.io images label docker.io/library/chronos-rust-backend:parity io.cri-containerd.pinned=pinned`
  (same for `chronos-backend:parity`).
- **Rust**: the CI image `ghcr.io/silverminer007/chronos-rust-backend:latest` is published from `main`, but may lag behind the
  commit under test. The parity run therefore builds the image from the checkout and imports it into k3s
  (no registry, no second copy of the image on disk besides containerd's):

  ```bash
  docker build -t chronos-rust-backend:parity rust-backend
  docker save chronos-rust-backend:parity | sudo k3s ctr -n k8s.io images import -
  docker rmi chronos-rust-backend:parity      # free the Docker copy
  ```

  `values-parity.yaml` references `chronos-backend:parity` and `chronos-rust-backend:parity` with `pullPolicy: Never`.
  After a rebuild: `kubectl -n chronos-parity rollout restart deploy/<name>`.

## Deploy

```bash
cd deployment
kubectl create namespace chronos-parity
kubectl -n chronos-parity create configmap keycloak-realm --from-file=parity/chronos-realm.json
kubectl apply -f parity/keycloak.yaml

# VAPID key pair (raw P-256 key, base64url); only needed because the chart's secret requires values
openssl ecparam -name prime256v1 -genkey -noout -out /tmp/vapid.pem
openssl ec -in /tmp/vapid.pem -outform DER 2>/dev/null | tail -c +8 | head -c 32 | basenc --base64url -w0 | tr -d '=' > /tmp/vapid-priv.txt
openssl ec -in /tmp/vapid.pem -pubout -outform DER 2>/dev/null | tail -c 65 | basenc --base64url -w0 | tr -d '=' > /tmp/vapid-pub.txt

helm upgrade --install chronos . -n chronos-parity -f values-local.yaml -f values-parity.yaml \
  --set-file secrets.values.vapid_public_key=/tmp/vapid-pub.txt \
  --set-file secrets.values.vapid_private_key=/tmp/vapid-priv.txt
kubectl -n chronos-parity get pods -w
```

Resources are small on purpose (the host is shared): Postgres 128-384 Mi, Keycloak 512 Mi-1 Gi, Java 256-768 Mi,
Rust 64-128 Mi. The frontend is scaled to 0 and the ingress is off; nothing outside the namespace is touched.

## Check

```bash
kubectl -n chronos-parity port-forward svc/chronos-rust-backend 18081:80 &
curl -s -o /dev/null -w '%{http_code}\n' localhost:18081/q/health/ready     # 200
kubectl -n chronos-parity port-forward deploy/chronos-backend 19000:9000 &   # Java management port
curl -s -o /dev/null -w '%{http_code}\n' localhost:19000/q/health/ready     # 200
```

## Run the contract harness

```bash
deployment/parity/run-contract.sh              # forwards ports, fetches tokens, runs `python3 -m parity`
deployment/parity/run-contract.sh --explore    # multi-user flows and error paths (explore.py), same engine
```

Both databases must be fresh for a run (the scenario creates groups, appointments etc. and expects no earlier
data); reset with `deployment/parity/reset-databases.sh` (drops and recreates both databases, restarts Keycloak and both
backends; Keycloak keeps user changes of a run otherwise).

## Tear down

```bash
helm uninstall chronos -n chronos-parity
kubectl delete namespace chronos-parity      # also removes Keycloak and the PVC
sudo k3s ctr -n k8s.io images rm docker.io/library/chronos-rust-backend:parity docker.io/library/chronos-backend:parity
```
