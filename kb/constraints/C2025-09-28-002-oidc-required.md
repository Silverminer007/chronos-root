---
title: C2025-09-28-002: OIDC authentication required
date: 2025-09-28
status: Active
superceded_by: (none)
---

## Description

**All user authentication must use OIDC (OpenID Connect) via Keycloak.** No custom authentication, no basic auth, no JWT-only flows.

Specifically:
- Users authenticate via Keycloak's authorization endpoint
- Tokens are httpOnly cookies (inaccessible to JavaScript)
- Tokens are validated by backend (PrincipalContext)
- All APIs require authentication (except public pages)

## Rationale

- **Industry standard**: OIDC is the modern standard for web app authentication
- **Security**: OIDC handles cryptography correctly (unlike custom JWT)
- **User management**: Keycloak provides registration, password reset, 2FA, audit logs
- **Federated identity**: Future support for corporate LDAP, social login, etc.
- **Compliance**: OIDC flows are auditable and comply with security standards

## Implications

### Code
- All protected APIs require OIDC authentication
- Backend validates `Authorization: Bearer <token>` header (JWT from Keycloak)
- Frontend middleware checks session via `/api/auth/isLoggedIn`
- Service Worker handles token refresh automatically

### API Contract
- **Public endpoints**: None (except landing page)
- **Protected endpoints**: All require valid OIDC token
- **HTTP 401**: Returned if token is invalid/expired (after Service Worker refresh fails)
- **HTTP 403**: Returned if user lacks permission (not authenticated, but not authorized)

### Example

```java
// Backend: validate OIDC token
@GET
@Path("/me")
public Response getCurrentUser() {
  var userId = principal.getSubject(); // Extracted from OIDC token
  if (userId == null) {
    return Response.status(401).build(); // Unauthorized
  }
  // ... return user profile
}
```

```typescript
// Frontend: check session
const isLoggedIn = await $fetch('/api/auth/isLoggedIn')
if (response.status === 401) {
  redirectTo('/')  // Session expired, go to login
}
```

## Migration Path

If ever needed to switch auth providers:
1. Keep OIDC requirement (use different OIDC provider)
2. Keep httpOnly cookie storage
3. Keep server routes as proxy
4. Only implementation details change

This constraint makes future migration easier (not worse).

## References

- [ADR-2025-09-28-003: Keycloak OIDC](../adr/ADR-2025-09-28-003-keycloak.md)
- [ADR-2025-09-28-010: Server routes as proxy](../adr/ADR-2025-09-28-010-server-routes-proxy.md)
- [ARCH-2025-09-28-001: Frontend authentication flow](../architecture/ARCH-2025-09-28-001-auth-flow.md)
- [PATTERN-2025-09-28-001: Principal context pattern](../patterns/PATTERN-2025-09-28-001-principal-context.md)

---

**Last verified**: 2025-09-28
