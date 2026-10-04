---
title: ADR-2026-10-03-001: Pluggable secret provider for Helm chart
date: 2026-10-03
status: Accepted
supercedes: (none)
superceded_by: (none)
---

## Context

The Helm chart in `deployment/` currently only works on clusters running the **Bitwarden Secrets Manager operator**. The chart renders a `k8s.bitwarden.com/v1` `BitwardenSecret` resource, making it impossible to deploy on:
- Local development clusters (`k3s`, `kind`)
- CI/CD runners without the Bitwarden operator
- Clusters using alternative secret management (Sealed Secrets, SOPS, External Secrets, manual `kubectl create secret`)

This tight coupling forced operators to either install the Bitwarden CRD or manually create secrets outside the chart, creating friction and inconsistency across environments.

## Decision

Make the secret source **pluggable** via a new `secrets.provider` value that supports three backends:

1. **`bitwarden`** (default) — renders a `BitwardenSecret` CRD, mapping secret IDs to keys
2. **`values`** — renders a plain Kubernetes `Secret` from literal values in `secrets.values.*`
3. **`existing`** — renders nothing; expects the secret to be pre-created (e.g., by External Secrets, Sealed Secrets, or manual setup)

All templates read from a single **contract**: a Kubernetes `Secret` named `chronos-secret` (configurable via `secrets.existingSecretName`) with a fixed set of keys.

The image pull secret (`chronos-docker-secret`) follows the same logic:
- **`bitwarden`**: Not created by the chart; must exist or be managed outside
- **`values`**: Optionally created from `secrets.values.imagePullSecret` when `imagePullSecret.create: true`
- **`existing`**: Never created; assumed to exist or be managed outside

## Consequences

### Positive
- ✅ Local development and CI/CD no longer require the Bitwarden operator
- ✅ Bitwarden deployments remain byte-identical (no breaking change for prod/staging)
- ✅ Supports multiple secret backends without changing the chart structure
- ✅ Clear contract: all templates use a shared `chronos.secretName` helper
- ✅ Transparent to end-users: `values-prod.yaml` and `values-staging.yaml` need no changes
- ✅ Extensible: adding new providers (e.g., `externalSecrets`) only requires a new provider type

### Negative
- ❌ More configuration options → documentation must be clear (mitigated by examples)
- ❌ Secret values in `secrets.values.*` can be committed if not careful (mitigated by `.helmignore` and docs)
- ❌ Image pull secret logic adds conditional complexity (mitigated by helper functions)

### Trade-offs
- **Flexibility vs. simplicity**: Three providers instead of one, but each is simple and orthogonal
- **Default behavior**: Bitwarden (existing behavior) is the default, so no friction for current users

## Contract: Secret Key Names

All three providers must create a Kubernetes `Secret` named `chronos-secret` (or the value of `secrets.existingSecretName`) containing these keys:

| Key | Purpose |
|---|---|
| `database_password` | PostgreSQL password |
| `backend_client_id` | Keycloak client ID (backend) |
| `backend_client_secret` | Keycloak client secret (backend) |
| `frontend_client_id` | Keycloak client ID (frontend) |
| `frontend_sentry_auth_token` | Sentry token (frontend) |
| `frontend_sentry_dsn` | Sentry DSN (frontend) |
| `vapid_public_key` | Web Push VAPID public key |
| `vapid_private_key` | Web Push VAPID private key |
| `vapid_mailto` | Web Push VAPID mailto |
| `backup_bucket` | S3 bucket name (backup) |
| `backup_access_key_id` | S3 access key ID (backup) |
| `backup_secret_access_key` | S3 secret access key (backup) |
| `backup_region` | S3 region (backup) |
| `backup_endpoint_url` | S3 endpoint URL (backup) |
| `container_registry_secret` | Base64 dockerconfigjson for image pull (bitwarden only) |

**Note**: The chart tolerates extra keys in `chronos-secret` (used by future integrations like the Rust backend) — it only reads the keys it needs.

## Alternatives Considered

### All-in-one CRD (e.g., `externalSecrets`)
Could add support for `secrets.provider: externalSecrets`, but this requires installing another operator. Keep it as an example of `existing` in the README for now.

### Environment variable-based configuration
Could use env vars to select the provider, but Helm values are clearer and more idiomatic.

## Implementation

### Helpers
Add to `templates/_helpers.tpl`:
- `chronos.secretName` — returns the secret name (default `chronos-secret`)
- `chronos.imagePullSecretName` — returns the image pull secret name (default `chronos-docker-secret`)

### templates/secrets.yaml
Rewrite to:
1. Validate `secrets.provider` (fail if unknown)
2. Conditionally render:
   - `bitwarden`: BitwardenSecret (existing behavior)
   - `values`: Kubernetes Secret with stringData from `secrets.values.*`
   - `existing`: nothing
3. When `provider: values` and `imagePullSecret.create: true`, render a dockerconfigjson Secret

### Deployments
Replace hardcoded `chronos-secret` and `chronos-docker-secret` with helper calls:
- `secretName: {{ include "chronos.secretName" . }}`
- `imagePullSecrets: [{ name: {{ include "chronos.imagePullSecretName" . }} }]` (when not empty)

### Configuration
- Add `secrets.provider` to `values.yaml` (default: `bitwarden` for backward compatibility)
- Add `secrets.existingSecretName` (default: `chronos-secret`)
- Add `imagePullSecret.name` (default: `chronos-docker-secret`)
- Add `imagePullSecret.create` (default: `false`)
- Expand `secrets.values.*` structure for `provider: values`

## Validation & Testing

- **`helm lint`** must pass for all three providers
- **`helm template`** with `values-prod.yaml` / `values-staging.yaml` must produce byte-identical output to `main`
- **`helm template` with `provider: values`** must contain no `BitwardenSecret`
- **`helm template` with `provider: existing`** must contain neither `BitwardenSecret` nor a `Secret` named `chronos-secret`
- All templates using secret names must call `chronos.secretName` or `chronos.imagePullSecretName`

## References

- Ticket: #94 (Helm: make secret source pluggable)
- Current chart: `deployment/`
- Related: [C2025-09-28-002: OIDC required](../constraints/C2025-09-28-002-oidc-required.md)
- Kubernetes secrets: https://kubernetes.io/docs/concepts/configuration/secret/
- Helm conditionals: https://helm.sh/docs/chart_template_guide/functions_and_pipelines/#if-else

## Related Entries

- Deployment constraints: `kb/constraints/003-deployment-rules.md`

