---
title: ADR-2025-09-28-005: Service Worker for token refresh
date: 2025-09-28
status: Accepted
supercedes: (none)
superceded_by: (none)
---

## Context

After deciding on Keycloak OIDC with httpOnly cookie storage, a challenge emerged: **how do we refresh tokens transparently without user interaction?**

Requirements:
- Tokens expire (e.g., 5 minutes for access token, 30 days for refresh token)
- When a token expires, we need a new one *before* the next request fails
- User should never see "session expired" errors (unless truly expired)
- Token refresh must work offline (or partially offline)
- Solution must not require complicated state management

The challenge: middleware runs on page navigation, but not on every API request. We needed to intercept **all** requests and check token freshness.

## Decision

Use the **Service Worker** (Web Worker + Fetch API) to:
1. **Intercept all `/api/*` requests**
2. **Check token expiry** (read `kc_expires` cookie)
3. **Proactively refresh** if expiring soon (< 5 seconds)
4. **Attach auth headers** to forwarded requests
5. **Notify clients on session expiry** (post message to all tabs)

### Key Choices
- **Service Worker** (not middleware): Runs independently of component lifecycle
- **Proactive refresh**: Refresh *before* token expires, not *after* a 401
- **Cookie-based expiry check**: Read `kc_expires` directly from `cookieStore` (browser API)
- **Deduplication**: Only one refresh in-flight at a time (avoid concurrent refresh calls)
- **Fallback retry**: If request returns 401 despite having a fresh token, retry once more

## Consequences

### Positive
- ✅ **Transparent**: User never sees token-expiry errors (unless truly expired)
- ✅ **Efficient**: Proactive refresh prevents failed requests
- ✅ **Works offline**: SW can read cookies without network (for auth checks)
- ✅ **Centralized**: All token logic in one place (SW), not scattered across components
- ✅ **Deduplicates refresh**: Multiple concurrent requests don't trigger multiple refreshes
- ✅ **Survives tab switching**: Each tab has its own page state, but shares cookies
- ✅ **Supports multi-tab sync**: Posts message to all tabs when session expires

### Negative
- ❌ **Complex logic**: Service Workers are notoriously tricky to debug
- ❌ **Browser compatibility**: Some older browsers don't support Service Workers (mitigated by fallback)
- ❌ **Latency**: Every request goes through SW (minimal overhead, ~1ms)
- ❌ **Scope limitations**: Can't intercept requests outside its scope (`/api/*` only)
- ❌ **Cache complexity**: Service Worker caching strategy can conflict with API freshness

### Trade-offs
- **Transparency vs. complexity**: Transparent token refresh requires sophisticated Service Worker
- **Proactive vs. reactive**: Proactive refresh is better UX but requires polling token expiry
- **Single-origin vs. cross-origin**: Current design only works for same-origin backend

## Alternatives Considered

### Middleware-only refresh
```
Pros: Simpler (no Service Worker)
Cons: Only runs on page navigation, not on every request; user could hit stale-token errors
Decision: Rejected — not sufficient for seamless UX
```

### Axios/Fetch interceptor
```
Pros: Simple to implement
Cons: Only works in browser; breaks with redirect chains; doesn't survive page reload
Decision: Rejected — Service Worker is more robust
```

### Backend-driven refresh (send new token on every 401)
```
Pros: Simpler backend logic
Cons: Still results in failed request (user sees error); slower
Decision: Rejected — proactive refresh is better UX
```

## Implementation

### Service Worker Setup
```typescript
// public/push-sw.js
self.addEventListener('fetch', (event) => {
  // Intercept all /api/* requests
  if (event.request.url.includes('/api/')) {
    // Check token expiry from cookieStore
    const kc_expires = getCookieExpiry('kc_expires')
    if (shouldRefresh(kc_expires)) {
      // Proactively refresh before forwarding request
      event.respondWith(refreshAndRetry(event.request))
    } else {
      // Forward request with Authorization header
      event.respondWith(attachAuthAndForward(event.request))
    }
  }
})
```

### Registration (in app)
```typescript
// app/plugins/auth.client.ts
if ('serviceWorker' in navigator) {
  await navigator.serviceWorker.register('/push-sw.js', { scope: '/' })
}
```

### Token Refresh Endpoint
```
POST /api/auth/refresh
Body: (empty, relies on httpOnly cookies)
Response: Sets new cookies (kc_access, kc_refresh, kc_expires)
```

### Cookie-based Expiry Check
```typescript
// In SW, using Cookie Store API
const cookieStore = await self.registration.scope // Get cookieStore
const expires = await cookieStore.get('kc_expires')
if (expires && new Date(expires.value) < Date.now() + 5000) {
  // Expires in < 5 seconds, refresh now
}
```

## References

- [Service Worker API](https://developer.mozilla.org/en-US/docs/Web/API/Service_Worker_API)
- [Cookie Store API](https://developer.mozilla.org/en-US/docs/Web/API/Cookie_Store_API)
- [ADR-2025-09-28-003: Keycloak OIDC](ADR-2025-09-28-003-keycloak.md)
- [ADR-2025-09-28-010: Server routes as auth proxy](ADR-2025-09-28-010-server-routes-proxy.md)
- [ARCH-2025-09-28-002: Service Worker token refresh](../architecture/ARCH-2025-09-28-002-service-worker.md)
- Implementation: `frontend/public/push-sw.js`

## Related Entries

- [ARCH-2025-09-28-001: Frontend authentication flow](../architecture/ARCH-2025-09-28-001-auth-flow.md)
