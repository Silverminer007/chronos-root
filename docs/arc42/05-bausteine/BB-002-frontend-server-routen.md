---
id: BB-002
title: "Frontend: Auth- und Proxy-Server-Routen (Ebene 2)"
status: entwurf
typ: baustein-ebene-2
quellen:
  - frontend/server/api/auth
  - frontend/server/api/v2/[...path].ts
  - frontend/server/utils
verknuepft: [BB-001, LS-002, LZ-001, LZ-002, KZ-007]
---

# Frontend: Auth- und Proxy-Server-Routen (Ebene 2)

| Route | Aufgabe |
|---|---|
| `GET /api/auth/login` | Weiterleitung zum Keycloak-Authorization-Endpunkt (`scope=openid profile email`) |
| `GET /api/auth/register` | Weiterleitung zur Keycloak-Registrierung, Rücksprung mit `?registered=true` |
| `GET /api/auth/callback` | Code gegen Tokens tauschen, Cookies setzen, Weiterleitung auf `/agenda`; fehlen Tokens in der Antwort, endet die Route ohne Weiterleitung |
| `GET /api/auth/isLoggedIn` | 401, wenn `kc_expires` fehlt oder abgelaufen ist, sonst 204 (der Service Worker beantwortet den Aufruf meist selbst) |
| `POST /api/auth/refresh` | Refresh-Token einlösen, Cookies neu setzen; ohne Refresh-Cookie 401, bei ungültiger Keycloak-Antwort 502, bei Fehler beim Setzen 401 und Cookies löschen |
| `POST /api/auth/logout` | Logout am Keycloak (Fehler ignoriert), alle drei Cookies löschen |
| `/api/v2/[...path]` | Proxy auf `${quarkusUrl}/api/v2/<path>` mit Methode, Query und (bei POST, PUT, PATCH) Body; setzt `Authorization: Bearer <kc_access>`, auch wenn das Cookie fehlt; Fehlerantworten des Backends werden unverändert weitergereicht |

Cookies: `kc_access` und `kc_refresh` (`httpOnly`), `kc_expires` (lesbar, Ablaufzeit des Access-Tokens als Unix-Sekunden); alle `secure`, `sameSite=none`, `path=/`. Die Lebensdauer von `kc_access` und `kc_refresh` entspricht dem `exp` des jeweiligen Tokens, `kc_expires` lebt so lang wie das Refresh-Token. Hilfen: `server/utils/decodeJwt.ts` (liest den Payload ohne Signaturprüfung), `types.ts`.
