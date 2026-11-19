---
id: AE-008
title: "Service Worker for token refresh"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2025-09-28
quellen:
  - kb/adr/ADR-2025-09-28-005-service-worker.md
verknuepft: []
---

# AE-008: Service Worker for token refresh

Quelle: `kb/adr/ADR-2025-09-28-005-service-worker.md` (Status im ADR: accepted, Datum: 2025-09-28). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

After deciding on Keycloak OIDC with httpOnly cookie storage, a challenge emerged: **how do we refresh tokens transparently without user interaction?**

Requirements:
- Tokens expire (e.g., 5 minutes for access token, 30 days for refresh token)
- When a token expires, we need a new one *before* the next request fails
- User should never see "session expired" errors (unless truly expired)
- Token refresh must work offline (or partially offline)
- Solution must not require complicated state management

The challenge: middleware runs on page navigation, but not on every API request. We needed to intercept **all** requests and check token freshness.

## Entscheidung

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

## Konsequenzen

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
- ❌ **Latency**: Every request goes through SW (minimal overhead, ~1ms) …
