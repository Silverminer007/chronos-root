---
id: TD-017
title: "Sentry nur konfiguriert, kein SDK"
status: entwurf
typ: technische-schuld
quellen:
  - frontend/nuxt.config.ts
  - frontend/package.json
  - frontend/public/push-sw.js
  - deployment/values-prod.yaml
verknuepft: [KT-008, QA-007, BB-003]
---

# TD-017: Sentry nur konfiguriert, kein SDK

Konfiguration und Deployment kennen Sentry (`NUXT_PUBLIC_SENTRY_DSN`, `SENTRY_AUTH_TOKEN` in `values-prod.yaml`, `sentryDsn` in `runtimeConfig`, Kommentar im Service Worker über ein Sentry-Plugin). `frontend/package.json` enthält kein Sentry-SDK, `frontend/app/plugins/` nur `auth.client.ts` und `register-sw.client.ts`; das Backend hat keine Sentry-Anbindung. Fehler gelangen daher nicht an Sentry.

**Auswirkung:** Frontend-Fehler (auch die vom Service Worker gemeldeten) sind nur im Browser sichtbar; die Beobachtbarkeit ([QA-007](../../req42/06-qualitaet/QA-007-beobachtbarkeit.md)) deckt nur Metriken und Alarme der Infrastruktur ab.

**Maßnahme:** SDK und Plugin ergänzen oder Konfiguration und Geheimnisse entfernen.
