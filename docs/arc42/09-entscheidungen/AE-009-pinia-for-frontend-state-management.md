---
id: AE-009
title: "Pinia for frontend state management"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2025-09-28
quellen:
  - kb/adr/ADR-2025-09-28-006-pinia.md
verknuepft: []
---

# AE-009: Pinia for frontend state management

Quelle: `kb/adr/ADR-2025-09-28-006-pinia.md` (Status im ADR: accepted, Datum: 2025-09-28). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

The Nuxt 3 frontend needed state management for:
- Appointment list, filters, pagination
- Current user profile, permissions
- Notification queue
- Modal/dialog state
- Caching (avoid redundant API calls)

Options evaluated:
1. **Pinia** — official Vue 3 state library, simple, TypeScript-first
2. **Zustand** — lightweight, minimal boilerplate
3. **Tanstack Query** — focused on async data, built-in caching
4. **Nuxt auto-imports** — no library, just modules

## Entscheidung

Use **Pinia** for all application state. Each store is a TypeScript function with composition API, containing both state and actions. Store actions call `$fetch` to mutate backend, then update local state.

### Key Choices
- **Pinia**: Official Vue 3 library, part of Vue ecosystem
- **Composition API**: Each store is a function, not a class
- **Colocation**: State + actions in same store (not separate reducers/actions)
- **No mutations**: Only state references and actions (mutations are implicit)
- **Auto-imports**: Stores auto-imported by Nuxt (`useAppointmentStore()` without import)

## Konsequenzen

### Positive
- ✅ **Simple API**: Define state as `ref`, actions as functions
- ✅ **TypeScript**: All properties are typed, no `as any` nonsense
- ✅ **Devtools**: Pinia plugin for Vue DevTools, inspect state changes
- ✅ **Hot reload**: Stores update without page reload in dev
- ✅ **Colocation**: State + logic together, easier to understand
- ✅ **Composition API**: Same syntax as components (consistency)
- ✅ **No ORM-like complexity**: Just plain TypeScript, no magic

### Negative
- ❌ **No built-in persistence**: Requires manual `localStorage` save/restore
- ❌ **No built-in caching strategy**: Need to implement cache invalidation manually
- ❌ **Smaller ecosystem**: Fewer third-party extensions than Redux
- ❌ **No devtools defaults**: Must opt-in to devtools plugin

### Trade-offs
- **Simplicity vs. features**: Pinia is simpler, Tanstack Query has better caching
- **Centralized vs …
