---
id: KT-008
title: Nachbarsystem Sentry
status: entwurf
typ: nachbarsystem
quellen:
  - frontend/nuxt.config.ts
  - deployment/values-prod.yaml
verknuepft: [KZ-009]
---

# Nachbarsystem Sentry

- **Rolle:** Fehlererfassung im Frontend (optional).
- **Konfiguration:** `NUXT_PUBLIC_SENTRY_DSN`; im Chart `secrets.frontend.sentryDsn` und `sentryAuthToken` (Source-Maps beim Build **(?)**).
- Ohne DSN läuft das Frontend ohne Fehlerberichte.
