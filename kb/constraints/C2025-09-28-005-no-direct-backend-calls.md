---
title: C2025-09-28-005: No direct backend calls from frontend
date: 2025-09-28
status: Active
superceded_by: (none)
---

## Description

**Frontend never calls the Quarkus backend directly.** All requests must flow through Nuxt server routes.

- ❌ **Don't**: `fetch('https://api.example.com/api/v2/appointments')`
- ✅ **Do**: `$fetch('/api/v2/appointments')`

## Rationale

### Security
- Tokens never exposed to browser JavaScript
- httpOnly cookies only sent to Nuxt server (not frontend)
- CORS simplified (no cross-origin API calls)
- Request validation centralized in server routes

### Simplicity
- Token refresh transparent (Service Worker handles it)
- Same-origin requests (no CORS complexity)
- Cookies handled by Nuxt, not frontend code

### Architecture
- Nuxt becomes a security boundary
- Backend is internal (not exposed)
- Clear request flow (client → server routes → backend)

## Implications

### Frontend Development
```typescript
// ✓ Correct
const data = await $fetch('/api/v2/appointments')

// ❌ Wrong
const data = await fetch(`${import.meta.env.VITE_BACKEND_URL}/api/v2/appointments`, {
  headers: { 'Authorization': `Bearer ${token}` }  // Don't do this!
})
```

### Environment Variables
- Frontend **never** needs `VITE_QUARKUS_URL` or similar
- Backend URL is internal (only server routes know it)
- Frontend only needs `NUXT_*` config, not backend URLs

### API Contract
- Server routes proxy exactly (no transformation)
- Request path `/api/v2/...` maps to backend `/api/v2/...`
- Response is forwarded transparently

### Testing
- Frontend tests mock server routes (not backend)
- E2E tests can skip backend, run against preview server alone
- Integration tests run backend + frontend

## Implementation

### Nuxt Server Routes

All backend calls go through catch-all proxy:

```typescript
// server/api/v2/[...path].ts
export default defineEventHandler(async (event) => {
  const path = event.context.params?.path?.join('/') || ''
  const backendUrl = `${process.env.NUXT_QUARKUS_URL}/api/v2/${path}`
  
  // Forward request
  const response = await fetch(backendUrl, {
    method: event.node.req.method,
    headers: {
      'Authorization': `Bearer ${getCookie(event, 'kc_access')}`,
      'Content-Type': 'application/json'
    },
    body: await readBody(event)
  })
  
  return await response.json()
})
```

### Frontend Usage

```typescript
// Good: uses server routes
const appointments = await $fetch('/api/v2/appointments')

// This works because server routes handle everything:
// 1. Read httpOnly cookies
// 2. Attach Authorization header
// 3. Forward to backend
// 4. Return response
```

## References

- [ADR-2025-09-28-001: Nuxt 3 + Vue 3](../adr/ADR-2025-09-28-001-nuxt-vue.md)
- [ADR-2025-09-28-010: Server routes as proxy](../adr/ADR-2025-09-28-010-server-routes-proxy.md)
- [ARCH-2025-09-28-001: Frontend authentication flow](../architecture/ARCH-2025-09-28-001-auth-flow.md)

---

**Last verified**: 2025-09-28
