---
id: KT-008
title: Nachbarsystem Sentry
status: entwurf
typ: nachbarsystem
quellen:
  - frontend/nuxt.config.ts
  - frontend/package.json
  - frontend/public/push-sw.js
  - deployment/values.yaml
verknuepft: [RA-009, BB-003]
---

# Nachbarsystem Sentry

- **Vorgesehene Rolle:** Fehlererfassung im Frontend.
- **Stand im Code:** Konfiguriert sind `NUXT_PUBLIC_SENTRY_DSN` (Nuxt-`runtimeConfig`), im Chart `frontend_sentry_dsn` und `frontend_sentry_auth_token` (als `SENTRY_AUTH_TOKEN`; die Provider `bitwarden` und `values` verlangen beide Schlüssel); der Frontend-Build setzt in der CI `SENTRY_DISABLE_AUTO_SOURCE_MAP_UPLOAD`. Der Service Worker meldet Fehler als `SW_ERROR`-Nachricht an alle Fenster mit dem Hinweis, ein Sentry-Plugin leite sie weiter.
- **Lücke:** `package.json` enthält kein Sentry-Paket und kein Plugin liest `SW_ERROR`; die Anbindung ist im Code nicht vorhanden. Fehler werden daher derzeit nicht an Sentry gesendet; die Konfiguration ist wirkungslos.
