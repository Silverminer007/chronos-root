# Chronos Deployment

Helm chart for deploying Chronos (a group scheduling app) to Kubernetes.

## Architecture

The chart manages:
- **Frontend** (Nuxt 3 + Vue 3 PWA)
- **Backend** (Quarkus 3 REST API)
- **Rust Backend** (optional alternative backend)
- **PostgreSQL** (with backup to S3)
- **Monitoring** (Prometheus metrics, Grafana dashboards)

## Deployment

Dieses Deployment nutzt GitHub Actions um den helm chart im Cluster zu aktualisieren.

Damit das möglich ist, müssen die Cluster Zugangsdaten hinterlegt werden:

```bash
# 1. Service Account erstellen
kubectl apply -f github-actions-rbac.yaml

# 2. Secrets extrahieren
kubectl get secret github-actions-deployer-token -n chronos-prod -o jsonpath='{.data.token}' | base64 -d
# => KUBE_TOKEN

kubectl get secret github-actions-deployer-token -n chronos-prod -o jsonpath='{.data.ca\.crt}' | base64 -d  
# => KUBE_CA_CERT

kubectl config view --minify -o jsonpath='{.clusters[0].cluster.server}'
# => KUBE_SERVER
```

Diese Variablen müssen als Actions Secret im Repo angelegt werden.

## Secret Management

The chart supports multiple secret backends via the `secrets.provider` value. Choose the provider based on your environment:

### Provider: `bitwarden` (default)

Uses the **Bitwarden Secrets Manager operator** to fetch secrets from Bitwarden.

**Requirements:**
- Bitwarden Secrets Manager operator installed in the cluster
- `bw-auth-token` Secret with the operator token

**Values:**
```yaml
secrets:
  provider: bitwarden
  organisationId: "..."
  db:
    password: "secret-id-uuid"
  backend:
    clientId: "secret-id-uuid"
    clientSecret: "secret-id-uuid"
  # ... (see values-prod.yaml)
```

**Usage:**
```bash
helm install chronos . -f values-prod.yaml
```

### Provider: `values`

Renders a plain Kubernetes Secret from literal values. Suitable for **local development** and **CI/CD**.

**Requirements:**
- Secret values provided via `--set` or `--set-file`
- No external operator needed

**Values:**
```yaml
secrets:
  provider: values
  values:
    database_password: "..."
    backend_client_id: "..."
    # ... (all 15 secret keys)
```

**Usage (local dev):**
```bash
# With a values file (documented in values-local.yaml):
helm install chronos . -f values-local.yaml

# With individual --set (not recommended for secrets):
helm install chronos . \
  --set secrets.provider=values \
  --set secrets.values.database_password=mypassword \
  # ... (all other values)
```

**Important:** Secret values must not be committed to git. Use:
- Local `.env` files (add to `.gitignore`)
- `--set-file` to load from local files
- `--set` for non-sensitive values only
- CI/CD secret management (GitHub Actions Secrets, etc.)

### Provider: `existing`

Expects the `chronos-secret` Secret to already exist in the cluster (created by other tools).

**Requirements:**
- Secret `chronos-secret` with all required keys already exists
- Can be created via:
  - **Sealed Secrets**: `kubeseal` + Sealed Secrets operator
  - **External Secrets**: ExternalSecrets operator + Bitwarden/Vault/AWS Secrets Manager
  - **SOPS**: Sealed with `sops` + decrypted by a CI/CD pipeline
  - **Manual**: `kubectl create secret generic chronos-secret --from-literal=...`

**Values:**
```yaml
secrets:
  provider: existing
  existingSecretName: chronos-secret  # optional, defaults to 'chronos-secret'
```

**Usage:**
```bash
# Create the secret manually (example):
kubectl create secret generic chronos-secret \
  --from-literal=database_password=... \
  --from-literal=backend_client_id=... \
  # ... (all other keys)

# Then deploy:
helm install chronos . -f values-existing.yaml
```

## Image Pull Secrets

By default, the chart expects the image pull secret `chronos-docker-secret` to exist in the cluster.

To have Helm create it automatically with the `values` provider:

```yaml
imagePullSecret:
  name: chronos-docker-secret
  create: true
  registry: ghcr.io
  username: _json_key
  password: '{"type":"service_account",...}'  # base64 decoded
```

To disable image pull secrets entirely (e.g., for public images):

```yaml
imagePullSecret:
  name: ""
  create: false
```

## Local Development

For local development with `k3s`, `kind`, or `minikube`:

```bash
# 1. Create secrets manually:
kubectl create secret generic chronos-secret \
  --from-literal=database_password=changeme \
  --from-literal=backend_client_id=chronos-backend \
  # ... (see values-local.yaml for all keys)

# 2. Deploy:
helm install chronos . -f values-local.yaml

# Or, use the values provider to have Helm create the secret:
helm install chronos . \
  -f values-local.yaml \
  --set secrets.provider=values \
  --set secrets.values.database_password=changeme \
  # ... (all other values)
```

## Testing

Lint and template the chart for all providers:

```bash
# Test bitwarden (default)
helm lint . -f values-prod.yaml
helm template chronos . -f values-prod.yaml

# Test values provider
helm lint . -f values-local.yaml
helm template chronos . -f values-local.yaml

# Test existing provider
helm lint . -f values-existing.yaml
helm template chronos . -f values-existing.yaml
```

CI/CD automatically tests all providers on pull requests (see `.github/workflows/deployment-helm-test.yml`).

## Secret Keys

The chart expects the following keys in `chronos-secret`:

| Key | Purpose | Used by |
|---|---|---|
| `database_password` | PostgreSQL password | PostgreSQL, Quarkus backend, Rust backend |
| `backend_client_id` | Keycloak OAuth client ID | Quarkus backend |
| `backend_client_secret` | Keycloak OAuth client secret | Quarkus backend |
| `frontend_client_id` | Keycloak OAuth client ID | Nuxt frontend |
| `frontend_sentry_auth_token` | Sentry authentication token | Nuxt frontend |
| `frontend_sentry_dsn` | Sentry DSN | Nuxt frontend |
| `vapid_public_key` | Web Push VAPID public key | Quarkus backend |
| `vapid_private_key` | Web Push VAPID private key | Quarkus backend |
| `vapid_mailto` | Web Push VAPID mailto | Quarkus backend |
| `backup_bucket` | S3 bucket name | PostgreSQL backup |
| `backup_access_key_id` | S3 access key ID | PostgreSQL backup |
| `backup_secret_access_key` | S3 secret access key | PostgreSQL backup |
| `backup_region` | S3 region | PostgreSQL backup |
| `backup_endpoint_url` | S3 endpoint URL | PostgreSQL backup |
| `container_registry_secret` | Docker registry credentials (base64 dockerconfigjson) | Bitwarden provider only |

**Note:** The `container_registry_secret` key is only used by the Bitwarden provider. For the `values` provider, use the `imagePullSecret` section instead (with `create: true`).

## Files

- `Chart.yaml` — Helm chart metadata
- `values.yaml` — Default values (base configuration)
- `values-prod.yaml` — Production environment (Bitwarden provider)
- `values-staging.yaml` — Staging environment (Bitwarden provider)
- `values-local.yaml` — Local development (values provider)
- `values-existing.yaml` — Example for existing provider
- `templates/` — Kubernetes resource templates
- `templates/_helpers.tpl` — Helm template helpers

## Key Templates

- `secrets.yaml` — Creates secrets (provider-specific)
- `frontend-deployment.yaml` — Frontend pod
- `backend-deployment.yaml` — Quarkus backend pod
- `rust-backend-deployment.yaml` — Rust backend pod (optional)
- `postgresql.yaml` — PostgreSQL StatefulSet
- `postgres-backup.yaml` — PostgreSQL backup CronJob
- `ingress.yaml` — Ingress for frontend
- `prometheus-alerts.yaml` — Prometheus alert rules

## References

- [Helm Documentation](https://helm.sh/docs/)
- [Kubernetes Secrets](https://kubernetes.io/docs/concepts/configuration/secret/)
- [Chronos Architecture](../kb/architecture/)
- ADR: [Pluggable Secret Provider](../kb/adr/ADR-2026-10-03-001-pluggable-secret-provider.md)
