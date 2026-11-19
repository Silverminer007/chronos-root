---
id: AE-013
title: "Server routes as auth proxy"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2025-09-28
quellen:
  - kb/adr/ADR-2025-09-28-010-server-routes-proxy.md
verknuepft: []
---

# AE-013: Server routes as auth proxy

Quelle: `kb/adr/ADR-2025-09-28-010-server-routes-proxy.md` (Status im ADR: accepted, Datum: 2025-09-28). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

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

## Entscheidung

Use **Nuxt server routes** (`server/api/v2/[...path].ts`) as a transparent proxy between frontend and backend.

### Key Choices
- **Server routes**: `[...path].catch-all` routes that proxy to Quarkus backend
- **httpOnly cookies**: Tokens stored in httpOnly cookies (inaccessible to JavaScript)
- **Transparent forwarding**: Server routes read cookies, attach `Authorization` header, forward to backend
- **Request interception**: Service Worker intercepts before reaching server routes (for token refresh)

## Konsequenzen

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
- ❌ **Debugging**: Request path is longer (client → SW → server routes → backend → DB) …
