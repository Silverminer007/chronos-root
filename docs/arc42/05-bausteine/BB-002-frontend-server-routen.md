---
id: BB-002
title: Frontend: Auth- und Proxy-Server-Routen (Ebene 2)
status: entwurf
typ: baustein-ebene-2
quellen:
  - frontend/server/api/auth
  - frontend/server/api/v2/[...path].ts
verknuepft: [BB-001, LS-002, LZ-001, LZ-002]
---

# Frontend: Auth- und Proxy-Server-Routen (Ebene 2)

| Route | Aufgabe |
|---|---|
| `GET /api/auth/login` | Weiterleitung zu Keycloak |
| `GET /api/auth/callback` | Code gegen Tokens tauschen, Cookies setzen |
| `GET /api/auth/register` | Registrierungsweiterleitung **(?)** |
| `GET /api/auth/isLoggedIn` | `kc_expires` prüfen (204/401) |
| `POST /api/auth/refresh` | Tokens erneuern, Cookies aktualisieren |
| `POST /api/auth/logout` | Cookies löschen |
| `/api/v2/[...path]` | Proxy zum Backend mit Bearer-Token |

Cookies: `kc_access`, `kc_refresh` (httpOnly), `kc_expires` (lesbar).
