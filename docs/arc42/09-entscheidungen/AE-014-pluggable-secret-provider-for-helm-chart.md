---
id: AE-014
title: "Pluggable secret provider for Helm chart"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2026-10-03
quellen:
  - kb/adr/ADR-2026-10-03-001-pluggable-secret-provider.md
verknuepft: []
---

# AE-014: Pluggable secret provider for Helm chart

Quelle: `kb/adr/ADR-2026-10-03-001-pluggable-secret-provider.md` (Status im ADR: accepted, Datum: 2026-10-03). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

The Helm chart in `deployment/` currently only works on clusters running the **Bitwarden Secrets Manager operator**. The chart renders a `k8s.bitwarden.com/v1` `BitwardenSecret` resource, making it impossible to deploy on:
- Local development clusters (`k3s`, `kind`)
- CI/CD runners without the Bitwarden operator
- Clusters using alternative secret management (Sealed Secrets, SOPS, External Secrets, manual `kubectl create secret`)

This tight coupling forced operators to either install the Bitwarden CRD or manually create secrets outside the chart, creating friction and inconsistency across environments.

## Entscheidung

Make the secret source **pluggable** via a new `secrets.provider` value that supports three backends:

1. **`bitwarden`** (default) — renders a `BitwardenSecret` CRD, mapping secret IDs to keys
2. **`values`** — renders a plain Kubernetes `Secret` from literal values in `secrets.values.*`
3. **`existing`** — renders nothing; expects the secret to be pre-created (e.g., by External Secrets, Sealed Secrets, or manual setup)

All templates read from a single **contract**: a Kubernetes `Secret` named `chronos-secret` (configurable via `secrets.existingSecretName`) with a fixed set of keys.

The image pull secret (`chronos-docker-secret`) follows the same logic:
- **`bitwarden`**: Not created by the chart; must exist or be managed outside
- **`values`**: Optionally created from `secrets.values.imagePullSecret` when `imagePullSecret.create: true` …

## Konsequenzen

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
- **Flexibility vs …
