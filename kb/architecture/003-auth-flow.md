---
name: Keycloak OIDC Authentication Flow
description: Server-side token refresh and httpOnly cookie management for secure OAuth2 flow
category: architecture
last-updated: 2026-09-27
---

# Keycloak OIDC Authentication Flow

## Decision
Chronos uses Keycloak as the OIDC provider with server-side token refresh and httpOnly cookies to prevent XSS token theft.

## High-Level Flow

```
User Browser
    ↓ (1. navigateTo('/api/auth/login'))
Frontend (Nuxt 3)
    ↓ (2. redirect to Keycloak)
Keycloak (Authorization Code flow)
    ↓ (3. callback with auth code)
Backend (Nuxt Server Routes)
    ↓ (4. exchange code for tokens)
    ↓ (5. set httpOnly cookies)
Browser
    ↓ (6. stored cookies, validated on each request)
Service Worker
    ↓ (7. proactive token refresh if needed)
API Calls
```

## Authentication Routes

| Route | Purpose | Response |
|-------|---------|----------|
| `GET /api/auth/login` | Start OAuth2 flow | 302 redirect to Keycloak |
| `GET /api/auth/callback` | Exchange auth code for tokens | 302 redirect to `/`; sets cookies |
| `GET /api/auth/isLoggedIn` | Check session validity | 204 (valid) or 401 (expired) |
| `POST /api/auth/refresh` | Refresh access token | New cookies |
| `POST /api/auth/logout` | Clear session | Clear cookies |

## Cookie Management

Three cookies are set after successful login:

| Cookie | Content | HttpOnly | Expires | Use |
|--------|---------|----------|---------|-----|
| `kc_access` | Access token (JWT) | ✅ Yes | Token expiry | API calls |
| `kc_refresh` | Refresh token | ✅ Yes | Refresh expiry | Token refresh |
| `kc_expires` | Unix timestamp (token expiry) | ❌ No (client-readable) | Token expiry | SW decides when to refresh |

## Service Worker Token Refresh

The service worker (`public/push-sw.js`) intercepts all `/api/*` requests:

1. **Before request**: If token expires in < 5s, call `POST /api/auth/refresh` first
2. **On 401**: Retry once after refreshing tokens
3. **Proactive refresh**: 30s before expiry, schedule a refresh via `setTimeout`
4. **Deduplication**: Shared `inflightRefresh` promise prevents concurrent refreshes

## Authorization

- `PrincipalContext` (request-scoped) holds current user's OIDC subject ID
- `PrincipalContextFilter` extracts JWT once per request
- `AuthorizationService` checks role/group membership for resource access
- No authorization logic in JAX-RS layer; all in services

## Constraints

- Access tokens are short-lived (typically 5-15 minutes)
- Refresh tokens are long-lived (typically days)
- httpOnly cookies prevent XSS token theft
- CSRF protection required for logout (use SameSite=Strict)
- Backend must validate JWT signature on every request
