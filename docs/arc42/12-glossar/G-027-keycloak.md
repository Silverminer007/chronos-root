---
id: G-027
title: "Keycloak"
status: entwurf
typ: begriff
quellen:
  - backend/src/main/resources/application.properties
  - frontend/nuxt.config.ts
verknuepft: [AE-003, AE-049, R-004]
---

# G-027: Keycloak

**Code-Name:** Realm `chronos` auf `auth.chronos-live.de`; Konfiguration `AUTH_SERVER_*` (Backend), `NUXT_AUTH_*` (Frontend); Realm-Rolle `ADMIN_API`

Identity-Provider für Anmeldung und Benutzerverwaltung ([AE-003](../09-entscheidungen/AE-003-keycloak-oidc-fuer-die-authentifizierung.md)). Das Backend ist zugleich Resource Server (Token-Validierung) und Admin-Client (Profil, Suche).
