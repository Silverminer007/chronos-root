---
id: AE-001
title: "Nuxt und Vue als SPA ohne SSR"
status: entwurf
typ: architekturentscheidung
datum: 2025-09-28
quellen:
  - frontend/nuxt.config.ts
  - frontend/package.json
  - frontend/Dockerfile
  - kb/adr/ADR-2025-09-28-001-nuxt-vue.md (Git-Historie, Begründung und Alternativen)
verknuepft: [AE-009, AE-013]
---

# AE-001: Nuxt und Vue als SPA ohne SSR

**Entscheidung:** Das Frontend ist eine Vue-3-Anwendung auf Nuxt (`nuxt ^4.4`) mit `ssr: false`, UI-Komponenten aus PrimeVue und Tailwind CSS 4, ausgeliefert als PWA (`manifest.webmanifest`, Service Worker).

**Begründung (laut früherem ADR):** Nuxt liefert Routing, Auto-Imports und Server-Routen ohne Zusatzbibliotheken (schnelle Umsetzung, TypeScript). Ohne SSR liegt kein Sitzungszustand auf dem Server; die Anmeldung läuft über Cookies, Nuxt-Server-Routen ([AE-013](AE-013-nuxt-serverrouten-als-auth-proxy.md)) und den Service Worker ([AE-008](AE-008-token-refresh-im-service-worker.md)).

**Alternativen (laut ADR):** React (mehr Boilerplate, Router und State separat), Angular (aufwendiger Einstieg), Svelte (kleineres Ökosystem).

**Konsequenzen:**
- Der Container (`node:26-alpine`) startet trotz SPA einen Nitro-Server, weil die Server-Routen `/api/auth/*` und `/api/v2/*` laufen müssen.
- Größeres initiales Bundle als bei SSR; Nuxt-Major-Upgrades können brechen (Nuxt 3 → 4 ist erfolgt).
- Zustand liegt im Browser ([AE-009](AE-009-pinia-fuer-den-frontend-zustand.md)).
