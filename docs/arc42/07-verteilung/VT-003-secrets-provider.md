---
id: VT-003
title: Secret-Management
status: entwurf
typ: verteilung
quellen:
  - deployment/README.md
  - deployment/templates/secrets.yaml
  - kb/adr/ADR-2026-10-03-001-pluggable-secret-provider.md
verknuepft: [BB-009, LS-009]
---

# Secret-Management

Der Wert `secrets.provider` wählt das Backend: Standard `bitwarden` (Bitwarden Secrets Manager Operator, im Chart nur Secret-IDs, Operator-Token im Secret `bw-auth-token`); weitere Provider laut ADR **(?)** (siehe `test-providers.sh`). Geheimnisse (DB, Keycloak-Client, VAPID, Sentry, S3, Image-Pull) gehören nie in Git. Der Deploy-Account der CI nutzt `KUBE_TOKEN`, `KUBE_CA_CERT`, `KUBE_SERVER` als Actions-Secrets.
