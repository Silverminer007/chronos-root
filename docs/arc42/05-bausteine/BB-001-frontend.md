---
id: BB-001
title: Frontend (Ebene 1)
status: entwurf
typ: baustein-ebene-1
quellen:
  - frontend/
  - LLM_WIKI.md
verknuepft: [LS-001, BB-002, BB-003, BB-004]
---

# Frontend (Ebene 1)

Nuxt 4 / Vue 3 SPA (PWA), PrimeVue, Tailwind, Pinia.

| Teil | Pfad | Baustein |
|---|---|---|
| Seiten | `app/pages` | BB-004 |
| Stores | `app/stores` | BB-014 |
| Layouts | `app/layouts/default.vue`, `landingpage.vue` | – |
| Middleware | `app/middleware/auth.global.ts` | prüft Sitzung, leitet auf `/` oder `/onboarding` |
| Plugins | `app/plugins/auth.client.ts` | reagiert auf `SESSION_EXPIRED` |
| Server-Routen | `server/api` | BB-002 |
| Service Worker | `public/push-sw.js` | BB-003 |
| E2E-Tests | `e2e/*.spec.ts` (Playwright) | – |
