---
id: AE-009
title: "Pinia für den Frontend-Zustand"
status: entwurf
typ: architekturentscheidung
datum: 2025-09-28
quellen:
  - frontend/app/stores/
  - frontend/nuxt.config.ts
  - kb/adr/ADR-2025-09-28-006-pinia.md (Git-Historie, Begründung und Alternativen)
verknuepft: [AE-001]
---

# AE-009: Pinia für den Frontend-Zustand

**Entscheidung:** Anwendungszustand liegt in Pinia-Stores (Composition-API-Stil, Modul `@pinia/nuxt`, Auto-Import): `appointments`, `auth`, `friendships`, `groups`, `settings`. Aktionen rufen die API über `$fetch` auf (`/api/v2/*`) und aktualisieren danach den lokalen Zustand.

**Begründung (laut früherem ADR):** Offizielle Vue-Bibliothek, TypeScript-first, wenig Boilerplate, Devtools.

**Alternativen (laut ADR):** Zustand, TanStack Query, reine Nuxt-Composables.

**Konsequenzen:** Es gibt weder eingebaute Persistenz noch Cache-Invalidierung; beides müssen die Stores selbst leisten. Unit-Tests der Stores fehlen noch ([TD-004](../11-risiken/TD-004-frontend-unit-tests-fehlen.md)).
