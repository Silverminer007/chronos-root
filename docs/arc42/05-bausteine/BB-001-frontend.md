---
id: BB-001
title: Frontend (Ebene 1)
status: entwurf
typ: baustein-ebene-1
quellen:
  - frontend/package.json
  - frontend/nuxt.config.ts
  - frontend/app
  - frontend/server
  - frontend/Dockerfile
verknuepft: [LS-001, BB-002, BB-003, BB-004, BB-014]
---

# Frontend (Ebene 1)

Nuxt 4 / Vue 3 SPA (`ssr: false`) mit PrimeVue, Tailwind, Pinia und Luxon; Build zu einem Node-Server (`.output/server/index.mjs`, Port 3000, Container `node:26-alpine` mit nicht privilegiertem Benutzer).

| Teil | Pfad | Baustein / Aufgabe |
|---|---|---|
| Seiten | `app/pages` | [BB-004](BB-004-frontend-seiten.md) |
| Stores, Composables | `app/stores`, `app/composables` | [BB-014](BB-014-frontend-stores.md) |
| Komponenten | `app/components` | Dialoge, Karten und Stepper für Termine, Gruppen, Freunde, Benachrichtigungseinstellungen, Push- und PWA-Hinweise |
| Layouts | `app/layouts/default.vue`, `landingpage.vue` | `default` blendet Dialoge für PWA-Installation, Push-Erlaubnis und neue Funktionen ein |
| Middleware | `app/middleware/auth.global.ts` | prüft die Sitzung, leitet Unangemeldete (außer `/` und `/public/*`) auf `/`, Angemeldete von `/` auf `/agenda`, bei erster Nutzung auf `/onboarding` |
| Plugins | `app/plugins/auth.client.ts`, `register-sw.client.ts` | `SESSION_EXPIRED` → `/`; Registrierung des Service Workers |
| Utilities | `app/utils`, `app/types` | `getErrorMessage`, `getErrorStatus`, Base64-Helfer; gemeinsame Typen (`types/index.ts`) |
| Server-Routen | `server/api`, `server/utils` | [BB-002](BB-002-frontend-server-routen.md) |
| Service Worker | `public/push-sw.js` | [BB-003](BB-003-frontend-service-worker.md) |
| Statische Dateien | `public/` | Manifest, Icons, `_headers`, `robots.txt` |
| Tests | `e2e/*.spec.ts` | Playwright ([KZ-010](../08-konzepte/KZ-010-teststrategie.md)) |

Laufzeitkonfiguration über `NUXT_*`-Variablen ([KZ-013](../08-konzepte/KZ-013-konfiguration.md)).
