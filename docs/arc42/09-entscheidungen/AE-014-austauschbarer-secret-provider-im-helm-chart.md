---
id: AE-014
title: "Austauschbarer Secret-Provider im Helm-Chart"
status: entwurf
typ: architekturentscheidung
datum: 2026-10-03
quellen:
  - deployment/templates/secrets.yaml
  - deployment/templates/_helpers.tpl
  - deployment/values.yaml
  - deployment/test-providers.sh
  - .github/workflows/deployment-helm-test.yml
  - kb/adr/ADR-2026-10-03-001-pluggable-secret-provider.md (Git-Historie)
verknuepft: [AE-054, RA-009]
---

# AE-014: Austauschbarer Secret-Provider im Helm-Chart

**Problem:** Das Chart lief ursprünglich nur mit dem Bitwarden-Secrets-Manager-Operator (`BitwardenSecret`-CRD), nicht lokal (k3s, kind) und nicht in CI.

**Entscheidung:** `secrets.provider` wählt die Quelle; alle Templates lesen aus **einem** Kubernetes-Secret `chronos-secret` (Name über `secrets.existingSecretName`, Helper `chronos.secretName`) mit festen Schlüsseln.

| Provider | Verhalten |
|---|---|
| `bitwarden` (Standard) | rendert `BitwardenSecret`; `values-prod.yaml` und `values-staging.yaml` nutzen ihn |
| `values` | rendert ein Secret aus `secrets.values.*` (lokal, CI; `values-local.yaml`) |
| `existing` | rendert nichts; das Secret kommt von außen (Sealed Secrets, External Secrets, SOPS, `kubectl`) |

Das Pull-Secret `chronos-docker-secret` folgt derselben Logik (`imagePullSecret.create`).

**Konsequenzen:** Prod/Staging bleiben unverändert; neue Provider brauchen nur einen weiteren Zweig. Klartextwerte im `values`-Provider dürfen nicht ins Repository gelangen; ein Scan für `deployment/` existiert nicht (gitleaks läuft nur in `frontend-ci.yml`). Alle drei Provider rendert `test-providers.sh` in `deployment-helm-test.yml`.
