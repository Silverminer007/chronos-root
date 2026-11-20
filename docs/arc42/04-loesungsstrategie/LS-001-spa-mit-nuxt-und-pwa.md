---
id: LS-001
title: SPA mit Nuxt 4/Vue 3 als PWA
status: entwurf
typ: strategie
quellen:
  - frontend/nuxt.config.ts
  - frontend/package.json
  - frontend/app/stores
verknuepft: [AE-001, AE-009, BB-001, BB-014]
---

# SPA mit Nuxt 4/Vue 3 als PWA

Das Frontend ist eine reine Single-Page-Application (`ssr: false`, AE-001) auf Nuxt 4 und Vue 3 mit PrimeVue (Theme-Preset `ChronosTheme`), Tailwind 4 und Pinia. Eine Codebasis liefert die Weboberfläche und die installierbare PWA; Push und Token-Erneuerung laufen im Service Worker ([LS-004](LS-004-service-worker-token-refresh.md)). Die Nuxt-Server-Routen bleiben als einziger serverseitiger Teil ([LS-002](LS-002-server-routen-als-proxy.md)).

Folge: Alle Daten werden im Browser geladen; Lesen über `useFetch`, Mutationen über `$fetch` in Pinia-Store-Aktionen, Komponenten rufen nur Store-Aktionen auf ([BB-014](../05-bausteine/BB-014-frontend-stores.md)). Begründungen: [AE-001](../09-entscheidungen/AE-001-nuxt-und-vue-als-spa-ohne-ssr.md), [AE-009](../09-entscheidungen/AE-009-pinia-fuer-den-frontend-zustand.md).
