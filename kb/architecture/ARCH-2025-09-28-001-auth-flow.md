---
title: ARCH-2025-09-28-001: Frontend authentication flow
date: 2025-09-28
component: Frontend
relates_to:
  - ADR-2025-09-28-001: Use Nuxt 3 + Vue 3
  - ADR-2025-09-28-003: Keycloak OIDC
  - ADR-2025-09-28-005: Service Worker
  - ADR-2025-09-28-010: Server routes as proxy
  - C2025-09-28-002: OIDC required
---

## Overview

The frontend uses **OIDC Authorization Code Flow** with Keycloak, tokens stored in httpOnly cookies, and transparent token refresh via Service Worker. Users authenticate once, then the system transparently keeps them logged in.

## System Context

```
User Browser                Nuxt Server Routes      Keycloak                Quarkus Backend
    |                             |                     |                         |
    |-- 1. Click Login ----------->|                    |                         |
    |                             |                     |                         |
    |       2. Redirect to Keycloak auth ----------->|                         |
    |                             |                     |                         |
    |-- 3. User authenticates ------>|                |                         |
    |                             |                     |                         |
    |       4. Redirect back to callback with code <---|                         |
    |                             |                     |                         |
    |       5. Exchange code for tokens ----------->|                         |
    |                             |<-- access + refresh + expires ---|            |
    |       6. Set httpOnly cookies                    |                         |
    |       Redirect to app ------>|                    |                         |
    |                             |                     |                         |
    |-- 7. Load app ----------->|                    |                         |
    |                             |                     |                         |
    |-- 8. Check session ----->|-- Read kc_expires cookie              |
    |       (is logged in?) <--|-- Response: 204 or 401                 |
    |                             |                     |                         |
    |-- 9. Fetch appointments -->|                    |                         |
    |                             |-- Read kc_access, attach Authorization header
    |                             |-- Forward to backend ------------>|
    |                             |                     |       Query DB
    |                             |                     |<-- Response
    |       <-- Return appointments ---|<--|                         |
    |                             |                     |                         |
```

## Building Blocks

### 1. Server Routes (Auth Layer)

**Location**: `server/api/auth/*.ts`

Handles OIDC flow:
- `/api/auth/login` — redirects to Keycloak
- `/api/auth/callback` — exchanges code for tokens, sets httpOnly cookies
- `/api/auth/isLoggedIn` — checks if session is valid
- `/api/auth/refresh` — refreshes tokens
- `/api/auth/logout` — clears cookies

### 2. Middleware (Session Check)

**Location**: `app/middleware/auth.global.ts`

Runs on every page navigation:
- Calls `/api/auth/isLoggedIn`
- If 401 → redirects to `/` (login page)
- If user profile incomplete → redirects to `/onboarding`

### 3. Service Worker (Token Refresh)

**Location**: `public/push-sw.js`

Intercepts all `/api/*` requests:
- Reads `kc_expires` from cookies
- If expiring in < 5s → calls `/api/auth/refresh` first
- Attaches `Authorization: Bearer <token>` header
- Forwards request to backend
- If 401 → retries once after refresh

### 4. Auth Store

**Location**: `app/stores/auth.ts`

Pinia store managing:
- Current user profile
- Session state
- Login/logout actions

### 5. API Routes (Proxy)

**Location**: `server/api/v2/[...path].ts`

Transparent proxy:
- Reads `kc_access` from httpOnly cookie
- Attaches to request as `Authorization: Bearer ...`
- Forwards to backend
- Returns response (including new cookies if refreshed)

## Runtime Behavior

### Login Flow (First Time)

```
1. User clicks "Login"
   → navigateTo('/api/auth/login')

2. Server route redirects to Keycloak
   → browser navigates to https://keycloak.example.com/auth/authorize?...

3. User enters credentials
   → Keycloak validates

4. Keycloak redirects back
   → browser navigates to /api/auth/callback?code=...

5. Server route exchanges code for tokens
   → Quarkus backend validates code with Keycloak
   → Receives access_token, refresh_token, expires_in
   → Sets three httpOnly cookies:
     - kc_access: <JWT access token>
     - kc_refresh: <JWT refresh token>
     - kc_expires: <Unix timestamp of expiry>
   → Redirects to /

6. Middleware runs
   → Calls /api/auth/isLoggedIn
   → Gets 204 (valid session)
   → Allows navigation

7. User is logged in
```

### Authorization Code Flow (Details)

```
Authorization Code Flow (RFC 6749 + OIDC):

1. Frontend redirects user to:
   https://keycloak.example.com/auth/authorize?
     client_id=<CLIENT_ID>
     redirect_uri=https://app.example.com/api/auth/callback
     response_type=code
     scope=openid profile email
     state=<random state>
     code_challenge=<PKCE code challenge>

2. User authenticates at Keycloak

3. Keycloak redirects back:
   https://app.example.com/api/auth/callback?
     code=<authorization code>
     state=<must match step 1>

4. Server route exchanges code for tokens (backend call):
   POST https://keycloak.example.com/auth/token
   Body:
     grant_type: authorization_code
     code: <code from step 3>
     client_id: <CLIENT_ID>
     client_secret: <CLIENT_SECRET>
     code_verifier: <PKCE code verifier>

5. Keycloak responds with tokens (private, server-to-server):
   {
     access_token: "eyJhbGc...",
     refresh_token: "eyJhbGc...",
     expires_in: 300,
     token_type: "Bearer"
   }

6. Server sets httpOnly cookies with tokens

7. Frontend never sees tokens (only httpOnly cookies)
```

### Token Refresh Flow (Service Worker)

```
1. User makes request:
   $fetch('/api/v2/appointments')

2. Service Worker intercepts fetch
   → Reads kc_expires from cookieStore
   → Current time: 14:30:00
   → kc_expires: 14:30:03 (expires in 3 seconds)
   → Since < 5s, need to refresh

3. Service Worker calls refresh:
   POST /api/auth/refresh
   (uses kc_refresh cookie automatically)

4. Server route refreshes tokens:
   → Sends kc_refresh to Keycloak
   → Keycloak validates and returns new access_token
   → Server sets new cookies (kc_access, kc_expires)

5. Service Worker retries original request
   → Attaches new kc_access as Authorization header
   → Request succeeds

6. Frontend receives data
   → Never knew refresh happened (transparent)
```

### Logout Flow

```
1. User clicks "Logout"
   → authStore.logout()

2. Frontend calls:
   POST /api/auth/logout

3. Server route:
   → Clears all auth cookies
   → Optionally calls Keycloak's logout endpoint

4. Redirects to /
   → Middleware runs, checks session
   → Gets 401 (no cookies)
   → Redirects to login page
```

## Data Flow

### Request with Token

```
Frontend
  ↓ $fetch('/api/v2/appointments')
Service Worker
  ↓ (reads kc_expires, attached Authorization header)
Nuxt Server Route
  ↓ (reads kc_access cookie from request)
Quarkus
  ↓ (reads JWT from Authorization header, validates signature)
Business Logic
  ↓
Database
```

### Response

```
Database
  ↓
Quarkus
  ↓
Nuxt Server Route
  ↓ (may include Set-Cookie headers if tokens refreshed)
Service Worker
  ↓ (preserves Set-Cookie headers)
Frontend
  ↓
Component (renders data)
```

## Design Decisions

### Why httpOnly Cookies?

- **Security**: JavaScript can't access tokens (prevents XSS theft)
- **Automatic**: Cookies sent automatically with every request (no manual header attachment)
- **Server-controlled**: Server controls cookie lifespan, sameSite policy, etc.

### Why Service Worker for Refresh?

- **Transparent**: Frontend never sees token expiry (SW handles automatically)
- **Efficient**: Refreshes *before* token expires (no 401 errors)
- **Independent**: Runs outside component lifecycle (works across page navigations)
- **Deduplication**: Only one refresh in-flight at a time

### Why Server Routes as Proxy?

- **Simplicity**: Frontend doesn't need to know about tokens
- **Security boundary**: Nuxt server is the auth gateway
- **CORS avoidance**: Same-origin requests (no CORS headers needed)
- **Flexibility**: Can add request/response logging, rate limiting, etc.

## Implications

### For Frontend Development
- Components never deal with tokens
- All auth is transparent (unless testing auth flows)
- Session checks happen automatically (middleware)
- Token refresh is automatic (Service Worker)

### For Backend Development
- All requests have valid JWT tokens (validated by PrincipalContext)
- No need for custom auth logic (OIDC is standard)
- Can trust `principal.getSubject()` is the logged-in user

### For Operations
- Keycloak must be running and healthy
- httpOnly cookies require HTTPS in production
- Token expiry times must be tuned (5m access, 30d refresh typical)

## References

- [ADR-2025-09-28-003: Keycloak OIDC](../adr/ADR-2025-09-28-003-keycloak.md)
- [ADR-2025-09-28-005: Service Worker token refresh](../adr/ADR-2025-09-28-005-service-worker.md)
- [ADR-2025-09-28-010: Server routes as proxy](../adr/ADR-2025-09-28-010-server-routes-proxy.md)
- [ARCH-2025-09-28-002: Service Worker token refresh](ARCH-2025-09-28-002-service-worker.md)
- Implementation: `frontend/server/api/auth/`, `frontend/app/middleware/auth.global.ts`, `frontend/public/push-sw.js`

---

**Last updated**: 2025-09-28
