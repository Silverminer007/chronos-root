---
id: AE-003
title: "Keycloak OIDC for authentication"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2025-09-28
quellen:
  - kb/adr/ADR-2025-09-28-003-keycloak.md
verknuepft: []
---

# AE-003: Keycloak OIDC for authentication

Quelle: `kb/adr/ADR-2025-09-28-003-keycloak.md` (Status im ADR: accepted, Datum: 2025-09-28). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

Authentication is critical for a youth organization app. Requirements:
- Secure authentication (not rolling our own crypto)
- Support for multiple identity providers (federated identity)
- User management (registration, password reset, profile)
- Optional: SSO for organizational accounts
- OIDC/OAuth2 standard (industry standard)

Alternatives evaluated:
1. **Keycloak** — open-source, OIDC-compliant, rich feature set
2. **Auth0** — SaaS, enterprise-grade, but vendor lock-in
3. **Firebase Auth** — SaaS, simpler, but limited for enterprise needs
4. **Okta** — enterprise IAM, but overkill for youth org
5. **Custom JWT** — never do this (cryptography is hard)

## Entscheidung

Use **Keycloak** for OIDC authentication. All users authenticate via Keycloak's authorization endpoint; tokens are exchanged and stored as httpOnly cookies by Nuxt server routes.

### Key Choices
- **Keycloak**: Open-source, deployable on premise, full feature set
- **OIDC Authorization Code Flow**: Most secure for web apps (avoids exposing tokens to browser)
- **httpOnly cookies**: Tokens stored securely, not accessible to JavaScript
- **Server routes as proxy**: Backend validates JWTs, frontend never sees tokens

## Konsequenzen

### Positive
- ✅ **Industry standard** (OIDC) ensures compatibility and future-proofing
- ✅ **User management** (registration, password reset, 2FA) handled by Keycloak
- ✅ **Open-source** — no vendor lock-in, can self-host
- ✅ **Support for federated identity** — integrate with corporate LDAP, Google, social media
- ✅ **Fine-grained authorization** — roles, groups, permissions
- ✅ **Audit logs** — all auth events tracked
- ✅ **Session management** — token expiry, refresh, revocation all handled

### Negative
- ❌ **Operational overhead** — need to run and maintain Keycloak instance
- ❌ **Network latency** — every login requires round-trip to Keycloak
- ❌ **Token expiry adds complexity** — need refresh token handling (solved by Service Worker)
- ❌ **Storage requirement** — Keycloak needs database, adds infrastructure complexity

### Trade-offs
- **Convenience vs …
