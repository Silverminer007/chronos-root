---
id: AE-013
title: "Nuxt-Serverrouten als Auth-Proxy"
status: entwurf
typ: architekturentscheidung
datum: 2025-09-28
quellen:
  - frontend/server/api/v2/[...path].ts
  - frontend/server/api/auth/
  - deployment/values.yaml
  - kb/adr/ADR-2025-09-28-010-server-routes-proxy.md (Git-Historie, Begründung und Alternativen)
verknuepft: [AE-003, AE-008, R-008]
---

# AE-013: Nuxt-Serverrouten als Auth-Proxy

**Entscheidung:** Der Browser ruft das Backend nie direkt auf. Die Catch-all-Route `server/api/v2/[...path].ts` liest das Cookie `kc_access` und leitet jede Anfrage als `Authorization: Bearer …` an `${NUXT_QUARKUS_URL}/api/v2/<path>` weiter (Methode, Query und Body unverändert). Die Routen unter `server/api/auth/` (`login`, `register`, `callback`, `refresh`, `logout`, `isLoggedIn`) sprechen mit Keycloak und setzen die Cookies `kc_access` und `kc_refresh` (httpOnly, `secure`, `SameSite=None`) sowie das lesbare `kc_expires`.

**Begründung (laut früherem ADR):** Tokens bleiben für JavaScript unsichtbar (httpOnly); Browser und Backend sind same-origin über die Frontend-URL, daher ist kein CORS nötig; Token-Behandlung liegt an einer Stelle.

**Alternativen (laut ADR):** Token im Browser halten und als Header senden (für JavaScript lesbar); Backend liest das Cookie selbst (laut ADR verworfen).

**Konsequenzen:** Jede Anfrage nimmt einen zusätzlichen Hop (Browser, Service Worker, Nuxt, Backend). Cookies mit `SameSite=None` und fehlender Herkunftsprüfung im Proxy sind ein offenes Sicherheitsrisiko ([R-008](../11-risiken/R-008-proxy-ohne-csrf-schutz.md)).
