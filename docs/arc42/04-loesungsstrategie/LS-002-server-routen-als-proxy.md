---
id: LS-002
title: Nuxt-Server-Routen als einziger Zugang zum Backend
status: entwurf
typ: strategie
quellen:
  - frontend/server/api/v2/[...path].ts
  - deployment/templates/ingress.yaml
verknuepft: [AE-013, BB-002, KZ-007]
---

# Nuxt-Server-Routen als einziger Zugang zum Backend

Der Catch-all `/api/v2/[...path]` liest das Access-Token aus dem httpOnly-Cookie `kc_access` und leitet die Anfrage mit `Authorization: Bearer` an das Backend weiter. Dadurch sind Tokens für JavaScript unsichtbar, es braucht kein CORS, und das Backend hat keinen Ingress ([KT-002](../03-kontext/KT-002-technischer-kontext.md)). Preis: ein zusätzlicher Hop und eine Server-Rolle des Frontends (Node-Container mit eigenem Cluster-Deployment). Begründung: [AE-013](../09-entscheidungen/AE-013-nuxt-serverrouten-als-auth-proxy.md).
