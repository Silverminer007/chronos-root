---
id: AE-001
title: "Use Nuxt 3 + Vue 3 for frontend"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2025-09-28
quellen:
  - kb/adr/ADR-2025-09-28-001-nuxt-vue.md
verknuepft: []
---

# AE-001: Use Nuxt 3 + Vue 3 for frontend

Quelle: `kb/adr/ADR-2025-09-28-001-nuxt-vue.md` (Status im ADR: accepted, Datum: 2025-09-28). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

Chronos needed a modern, responsive frontend for a PWA (Progressive Web App) targeting youth organizations. Key requirements:
- Real-time interactivity (RSVP updates, invitation acceptance)
- Offline support (via Service Worker)
- Mobile-first design
- Quick time-to-market
- Strong TypeScript support for type safety

The team evaluated:
1. **React** — ecosystem-heavy, more boilerplate, requires routing + state management libraries
2. **Angular** — powerful but opinionated, steeper learning curve
3. **Vue 3 + Nuxt 3** — batteries-included, minimal boilerplate, built-in routing/state management
4. **Svelte** — lighter, but smaller ecosystem

## Entscheidung

Use **Nuxt 3 + Vue 3** for the frontend, deployed as a Single Page Application (SPA) with SSR disabled.

### Key Choices
- **Nuxt 3**: Provides file-based routing, auto-imports, built-in middleware, unified server routes
- **Vue 3**: Composition API with `<script setup>` syntax — modern, concise, reactive
- **SPA mode**: Simplifies auth flow (no server-side session state), auth handled in Service Worker
- **Pinia**: Official state management (replaces Vuex)
- **TypeScript**: `<script setup lang="ts">` everywhere

## Konsequenzen

### Positive
- ✅ Rapid development — conventions over configuration
- ✅ Built-in middleware for auth checks (on every navigation)
- ✅ File-based routing auto-scales as app grows
- ✅ Auto-imports reduce boilerplate
- ✅ Strong TypeScript integration (types auto-generated for routes)
- ✅ Server routes act as natural auth proxy (httpOnly cookies, token refresh)
- ✅ Composition API is ergonomic for complex component logic
- ✅ Good PWA support (Service Worker integration)

### Negative
- ❌ Smaller ecosystem than React (but sufficient for this project)
- ❌ Nuxt has frequent breaking changes between major versions (requires careful upgrades)
- ❌ SPA mode means larger initial bundle than SSR (mitigated by gzip + caching)
- ❌ Team less familiar with Vue than React (mitigated by onboarding docs)

### Trade-offs
- **Bundle size vs …
