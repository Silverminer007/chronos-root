---
id: LZ-001
title: Anmeldung (Authorization Code Flow)
status: entwurf
typ: laufzeitszenario
quellen:
  - kb/architecture/ARCH-2025-09-28-001-auth-flow.md
  - frontend/server/api/auth
verknuepft: [BB-002, LS-003, RB-002]
---

# Anmeldung (Authorization Code Flow)

```mermaid
sequenceDiagram
  participant U as Browser
  participant N as Nuxt-Server
  participant K as Keycloak
  U->>N: GET /api/auth/login
  N-->>U: Redirect Keycloak
  U->>K: Anmeldung
  K-->>U: Redirect /api/auth/callback?code
  U->>N: callback
  N->>K: Code gegen Tokens tauschen
  N-->>U: Cookies kc_access, kc_refresh, kc_expires; Redirect /
```
Bei jeder Navigation prüft `auth.global.ts` die Sitzung (`isLoggedIn`); bei unvollständigem Profil folgt `/onboarding`.
