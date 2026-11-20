---
id: G-014
title: "Access- und Refresh-Token"
status: entwurf
typ: begriff
quellen:
  - frontend/server/api/auth/callback.get.ts
  - frontend/server/api/auth/refresh.post.ts
verknuepft: [AE-003, AE-008, AE-013, G-027]
---

# G-014: Access- und Refresh-Token

**Code-Name:** Cookies `kc_access`, `kc_refresh` (httpOnly), `kc_expires` (lesbar, Ablaufzeit des Access-Tokens in Sekunden)

Von [Keycloak](G-027-keycloak.md) ausgestellte Tokens. Das kurzlebige Access-Token (JWT) beglaubigt Anfragen an das Backend; mit dem Refresh-Token erneuert der [Service Worker](G-039-service-worker.md) es. Beide liegen nur in Cookies, nie im JavaScript-Speicher ([AE-013](../09-entscheidungen/AE-013-nuxt-serverrouten-als-auth-proxy.md)).
