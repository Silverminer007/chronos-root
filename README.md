# Chronos

Chronos is a group scheduling and appointment management app. Users can create appointments, invite friends and groups, track participation, and receive push notifications. The UI is in German.

## Repository structure

```
Chronos/
├── backend/        Quarkus (Java 21) REST API, PostgreSQL, Keycloak OIDC
├── frontend/       Nuxt 3 + Vue 3 PWA, proxies all API calls server-side
└── deployment/     Helm chart + GitHub Actions for Kubernetes deployments
```

Each sub-project has its own CI pipeline triggered only on changes to its directory. See [CI/CD](#cicd) below.

## Getting started

### Backend

Requires Java 21 and Docker (for Testcontainers).

```bash
cd backend
./mvnw quarkus:dev        # dev mode with live reload at http://localhost:8080
./mvnw test               # run tests
./mvnw package -DskipTests
java -jar target/quarkus-app/quarkus-run.jar
```

The Quarkus Dev UI is available at `http://localhost:8080/q/dev/` in dev mode.

### Frontend

Requires Node.js 26.

```bash
cd frontend
cp .env.example .env      # fill in Keycloak and backend URL
npm install
npm run dev               # dev server at http://localhost:3000
npm run build
npm run lint
npm run typecheck
npm run test:e2e          # requires a built app; see frontend/CLAUDE.md
```

### Deployment

Requires `kubectl` access to the target cluster and Helm 3.

```bash
cd deployment

# First-time cluster setup: create the GitHub Actions service account
kubectl apply -f github-actions-rbac.yaml

# Extract the three secrets needed for CI (see Secrets below)
kubectl get secret github-actions-deployer-token -n chronos-prod \
  -o jsonpath='{.data.token}' | base64 -d          # → KUBE_TOKEN
kubectl get secret github-actions-deployer-token -n chronos-prod \
  -o jsonpath='{.data.ca\.crt}' | base64 -d        # → KUBE_CA_CERT
kubectl config view --minify \
  -o jsonpath='{.clusters[0].cluster.server}'       # → KUBE_SERVER

# Manual deploy (production)
helm upgrade chronos . -f values-prod.yaml --namespace chronos-prod --install

# Manual deploy (staging)
helm upgrade chronos . -f values-staging.yaml --namespace chronos-staging --install
```

## CI/CD

| Workflow | File | Trigger |
|---|---|---|
| Backend CI | `.github/workflows/backend-ci.yml` | push/PR to `backend/**` |
| Frontend CI | `.github/workflows/frontend-ci.yml` | push/PR to `frontend/**` |
| Deploy to Kubernetes | `.github/workflows/deployment-ci.yml` | push to `deployment/**` or `repository_dispatch` |

**Branch model:**
- `master` → semantic-versioned Docker image tagged `latest`, triggers production deployment
- `develop` → Docker image tagged `develop`, triggers staging deployment

The backend CI builds and pushes the Docker image, then fires a `repository_dispatch` event that triggers the deployment workflow. The frontend follows the same pattern via `workflow_run` chaining.

## Secrets

Set these in the GitHub repository settings:

| Secret | Used by | Description |
|---|---|---|
| `KUBE_TOKEN` | deployment CI | Service account token for `kubectl` |
| `KUBE_CA_CERT` | deployment CI | Base64-encoded cluster CA certificate |
| `KUBE_SERVER` | deployment CI | Kubernetes API server URL |

`GITHUB_TOKEN` is used automatically for GHCR image pushes and cross-workflow `repository_dispatch` calls — no manual setup needed.