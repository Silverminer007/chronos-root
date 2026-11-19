---
title: Kontext und Abgrenzung
status: entwurf
---

# Kontext und Abgrenzung

```
Benutzer ─► Frontend (Nuxt SPA) ─► Nuxt-Server-Routen (Proxy, Token aus Cookies) ─► Backend (Quarkus oder Rust) ─► PostgreSQL
                                                                                          │
                                                                                          ├─► Keycloak (OIDC, Benutzerdaten)
                                                                                          └─► Push-Dienste (Web Push, VAPID)
```

| Nachbarsystem | Schnittstelle | Zweck |
|---|---|---|
| Keycloak | OIDC (Authorization Code), Admin-API | Anmeldung, Benutzerprofile |
| Push-Dienste der Browser | Web Push (VAPID) | Benachrichtigungen |
| PostgreSQL | JDBC / sqlx | Persistenz |

Der Browser spricht nie direkt mit dem Backend (RB-004).
