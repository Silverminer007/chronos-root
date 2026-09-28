---
title: ADR-2025-09-28-010: Server routes as auth proxy
date: 2025-09-28
status: Accepted
supercedes: (none)
superceded_by: (none)
---

## Context

After deciding on Keycloak OIDC and httpOnly cookies, a question arose: **how does the backend validate that a request is authenticated?**

Requirements:
- Backend needs to know the current user (OIDC subject)
- Tokens must not be accessible to JavaScript (security best practice)
- Tokens must be attached to backend requests (proof of authentication)
- All of this must work without the frontend making CORS calls to backend

Options evaluated:
1. **Frontend sends token in Authorization header** — token accessible to JavaScript (risky)
2. **Backend reads httpOnly cookie directly** — doesn't work (httpOnly prevents JS access, but cookies still sent to server)
3. **Nuxt server routes act as proxy** — transparent proxy, handles auth, removes security complexity

## Decision

Use **Nuxt server routes** (`server/api/v2/[...path].ts`) as a transparent proxy between frontend and backend.

### Key Choices
- **Server routes**: `[...path].catch-all` routes that proxy to Quarkus backend
- **httpOnly cookies**: Tokens stored in httpOnly cookies (inaccessible to JavaScript)
- **Transparent forwarding**: Server routes read cookies, attach `Authorization` header, forward to backend
- **Request interception**: Service Worker intercepts before reaching server routes (for token refresh)

## Consequences

### Positive
- ✅ **Backend never exposes tokens**: Frontend never sees raw tokens
- ✅ **CORS not needed**: Frontend and backend talk through server routes (same-origin)
- ✅ **Centralized auth logic**: All token handling in server routes (not scattered in components)
- ✅ **Token refresh centralized**: Service Worker refreshes before forwarding (one place)
- ✅ **Cookie management centralized**: `Set-Cookie` headers handled by server (not JS)
- ✅ **Request inspection**: Server routes can log, validate, transform requests
- ✅ **Transparent to components**: Frontend never knows about tokens, just calls `/api/v2/*`

### Negative
- ❌ **Latency**: Every request goes through extra Nuxt layer (negligible, ~1-5ms)
- ❌ **Complexity**: Proxy pattern adds conceptual overhead
- ❌ **Debugging**: Request path is longer (client → SW → server routes → backend → DB)
- ❌ **Scaling**: Nuxt server becomes a bottleneck (mitigated by caching, load balancing)

### Trade-offs
- **Security vs. latency**: Small latency cost for major security gain (tokens hidden)
- **Simplicity vs. flexibility**: Proxy pattern adds indirection but enables many security patterns
- **Transparency vs. visibility**: Frontend has no visibility into token lifecycle (by design)

## Architecture

```
Frontend (Nuxt SPA)
    ↓ $fetch('/api/v2/appointments')
Service Worker
    ↓ (check token expiry, attach auth header, refresh if needed)
Nuxt Server Routes (/server/api/v2/[...path].ts)
    ↓ (read cookies, forward to backend with Authorization header)
Quarkus Backend
    ↓
PostgreSQL
```

## Implementation

### Server Routes Setup

```typescript
// server/api/v2/[...path].ts
export default defineEventHandler(async (event) => {
  const method = event.node.req.method
  const path = event.context.params?.path?.join('/') || ''
  const url = new URL(`${QUARKUS_URL}/api/v2/${path}`, QUARKUS_URL)
  
  // Read httpOnly cookies
  const accessToken = getCookie(event, 'kc_access')
  const refreshToken = getCookie(event, 'kc_refresh')
  
  // Build headers with Authorization
  const headers = new Headers()
  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`)
  }
  headers.set('Content-Type', 'application/json')
  
  // Forward request to backend
  const backendResponse = await fetch(url, {
    method,
    headers,
    body: event.node.req.method !== 'GET' ? await readBody(event) : undefined,
    credentials: 'same-origin' // Forward cookies
  })
  
  // Copy response headers (including Set-Cookie)
  backendResponse.headers.forEach((value, key) => {
    setHeader(event, key, value)
  })
  
  // Return response body
  return await backendResponse.json()
})
```

### Auth Cookie Routes

Separate routes handle auth lifecycle:

```typescript
// server/api/auth/login.ts
export default defineEventHandler(() => {
  const redirectUrl = new URL('/auth/realms/chronos/protocol/openid-connect/auth', KEYCLOAK_URL)
  redirectUrl.searchParams.set('client_id', CLIENT_ID)
  redirectUrl.searchParams.set('redirect_uri', REDIRECT_URI)
  redirectUrl.searchParams.set('response_type', 'code')
  redirectUrl.searchParams.set('scope', 'openid profile email')
  
  return sendRedirect(event, redirectUrl.toString(), 302)
})

// server/api/auth/callback.ts
export default defineEventHandler(async (event) => {
  const code = getQuery(event).code as string
  
  // Exchange code for tokens
  const tokenResponse = await fetch(`${KEYCLOAK_URL}/auth/realms/chronos/protocol/openid-connect/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'authorization_code',
      client_id: CLIENT_ID,
      client_secret: CLIENT_SECRET,
      code,
      redirect_uri: REDIRECT_URI
    })
  })
  
  const tokens = await tokenResponse.json()
  
  // Set httpOnly cookies
  setCookie(event, 'kc_access', tokens.access_token, {
    httpOnly: true,
    secure: true,
    sameSite: 'strict',
    maxAge: tokens.expires_in
  })
  setCookie(event, 'kc_refresh', tokens.refresh_token, {
    httpOnly: true,
    secure: true,
    sameSite: 'strict',
    maxAge: tokens.refresh_expires_in
  })
  setCookie(event, 'kc_expires', new Date(Date.now() + tokens.expires_in * 1000).getTime().toString())
  
  return sendRedirect(event, '/', 302)
})

// server/api/auth/refresh.ts
export default defineEventHandler(async (event) => {
  const refreshToken = getCookie(event, 'kc_refresh')
  
  const tokenResponse = await fetch(`${KEYCLOAK_URL}/auth/realms/chronos/protocol/openid-connect/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'refresh_token',
      client_id: CLIENT_ID,
      client_secret: CLIENT_SECRET,
      refresh_token: refreshToken
    })
  })
  
  const tokens = await tokenResponse.json()
  
  // Update cookies
  setCookie(event, 'kc_access', tokens.access_token, {
    httpOnly: true,
    secure: true,
    sameSite: 'strict'
  })
  // ... update other cookies
  
  return { ok: true }
})

// server/api/auth/isLoggedIn.ts
export default defineEventHandler((event) => {
  const accessToken = getCookie(event, 'kc_access')
  
  if (!accessToken) {
    setResponseStatus(event, 401)
    return
  }
  
  setResponseStatus(event, 204)
})
```

## Frontend Usage

Frontend never knows about auth complexity:

```typescript
// frontend/app/stores/appointments.ts
const fetchAppointments = async () => {
  // Just call /api/v2/..., server routes + SW handle auth
  const data = await $fetch('/api/v2/appointments')
  appointments.value = data
}
```

## References

- [Nuxt Server Routes](https://nuxt.com/docs/guide/directory-structure/server)
- [Fetch API](https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API)
- [ADR-2025-09-28-003: Keycloak OIDC](ADR-2025-09-28-003-keycloak.md)
- [ADR-2025-09-28-005: Service Worker for token refresh](ADR-2025-09-28-005-service-worker.md)
- [C2025-09-28-005: No direct backend calls from frontend](../constraints/C2025-09-28-005-no-direct-backend-calls.md)
- [ARCH-2025-09-28-001: Frontend authentication flow](../architecture/ARCH-2025-09-28-001-auth-flow.md)
- Frontend code: `frontend/server/api/`

## Related Entries

- [ADR-2025-09-28-001: Nuxt 3 + Vue 3](ADR-2025-09-28-001-nuxt-vue.md)
