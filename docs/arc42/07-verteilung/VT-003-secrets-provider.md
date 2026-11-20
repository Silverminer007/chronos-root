---
id: VT-003
title: Secret-Management
status: entwurf
typ: verteilung
quellen:
  - deployment/README.md
  - deployment/templates/secrets.yaml
  - deployment/templates/_helpers.tpl
  - deployment/values-local.yaml
  - deployment/values-existing.yaml
verknuepft: [RA-009, AE-014, KT-012, VT-002]
---

# Secret-Management

`secrets.provider` wählt, wer das Secret `chronos-secret` bereitstellt:

| Provider | Verhalten | Einsatz |
|---|---|---|
| `bitwarden` (Standard) | `BitwardenSecret` mit Secret-IDs, der Operator schreibt das Secret; Operator-Token im Secret `bw-auth-token`; Pflichtfelder werden mit `required` geprüft | Produktion, Staging |
| `values` | Chart rendert ein Opaque-Secret aus `secrets.values.*` (optional auch das Pull-Secret mit `imagePullSecret.create`) | lokal, CI |
| `existing` | Chart erzeugt nichts, das Secret muss vorhanden sein (Sealed Secrets, External Secrets, SOPS oder manuell) | externe Verwaltung |

Schlüssel im Secret: `database_password`, `backend_client_id`, `backend_client_secret`, `frontend_client_id`, `frontend_sentry_auth_token`, `frontend_sentry_dsn`, `vapid_public_key`, `vapid_private_key`, `vapid_mailto`, `backup_bucket`, `backup_access_key_id`, `backup_secret_access_key`, `backup_region`, `backup_endpoint_url`, `container_registry_secret` (nur Bitwarden). Die Pods lesen sie per `secretKeyRef` als `DB_PASSWORD`, `AUTH_SERVER_CLIENT_ID`/`_SECRET`, `VAPID_*`, `NUXT_AUTH_CLIENT_ID`, `SENTRY_AUTH_TOKEN`, `NUXT_PUBLIC_SENTRY_DSN`; die Backup-Jobs als `AWS_*`/`S3_BUCKET`. Geheimnisse gehören nie in Git ([RA-009](../02-randbedingungen/RA-009-geheimnisse-ueber-austauschbare-provider.md)). Der Deploy-Account der CI nutzt die Actions-Secrets `KUBE_TOKEN`, `KUBE_CA_CERT` (Base64) und `KUBE_SERVER`.
