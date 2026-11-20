---
id: LZ-001
title: Anmeldung (Authorization Code Flow)
status: entwurf
typ: laufzeitszenario
quellen:
  - frontend/server/api/auth
  - frontend/app/middleware/auth.global.ts
  - frontend/app/composables/useOnboarding.ts
verknuepft: [BB-002, LS-003, RB-002, KT-003]
---

# Anmeldung (Authorization Code Flow)

```mermaid
sequenceDiagram
  participant U as Browser
  participant N as Nuxt-Server
  participant K as Keycloak
  U->>N: GET /api/auth/login
  N-->>U: 302 Keycloak /auth
  U->>K: Anmeldung
  K-->>U: 302 /api/auth/callback?code
  U->>N: GET /api/auth/callback
  N->>K: POST /token (authorization_code)
  K-->>N: access_token, refresh_token
  N-->>U: Set-Cookie kc_access, kc_refresh, kc_expires; 302 /agenda
```

Registrierung läuft analog über `GET /api/auth/register` (Keycloak-Registrierungsseite, Rücksprung zum Callback mit `?registered=true`). Danach beginnt jede Navigation in der SPA mit `auth.global.ts`: `checkSession` fragt `/api/auth/isLoggedIn` (der Service Worker beantwortet das, siehe [LZ-002](LZ-002-token-refresh.md)). Ohne Sitzung führt jede Seite außer `/` und `/public/*` auf `/`. Mit Sitzung lädt die Middleware das Benutzerprofil (`GET /api/v2/user`); fehlt in diesem Browser das `localStorage`-Flag `onboardingShownAt`, folgt `/onboarding`, sonst wird `/` auf `/agenda` umgeleitet. Abmeldung: `POST /api/auth/logout` und Neuladen von `/`.
