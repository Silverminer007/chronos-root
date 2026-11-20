---
id: AE-003
title: "Keycloak OIDC für die Authentifizierung"
status: entwurf
typ: architekturentscheidung
datum: 2025-09-28
quellen:
  - backend/src/main/resources/application.properties
  - frontend/server/api/auth/
  - backend/src/main/java/de/chronos_live/chronos_date_api/security/PrincipalContextFilter.java
  - kb/adr/ADR-2025-09-28-003-keycloak.md (Git-Historie, Begründung und Alternativen)
verknuepft: [AE-008, AE-013, AE-049, RB-002, R-004, R-005]
---

# AE-003: Keycloak OIDC für die Authentifizierung

**Entscheidung:** Anmeldung und Benutzerverwaltung übernimmt Keycloak (OIDC, Authorization-Code-Flow).
- Das Frontend leitet zu Keycloak (`/api/auth/login`, `/api/auth/register`), tauscht den Code in `/api/auth/callback` gegen Tokens und legt sie als Cookies ab ([AE-013](AE-013-nuxt-serverrouten-als-auth-proxy.md)). Der Frontend-Client ist öffentlich (ohne Secret, ohne PKCE im Code).
- Das Backend validiert das JWT über `quarkus-oidc` (Rollen aus `realm_access/roles`) und nutzt `sub` als Benutzerkennung (`oidcId`).
- Für Profiländerung, Passwort, verknüpfte Konten und Benutzersuche ruft das Backend die Keycloak-Admin-API (Service-Account, `CLIENT_CREDENTIALS`) hinter dem `IdentityPort` auf ([AE-049](AE-049-lokaler-profil-cache-hinter-identityport.md)).

**Begründung (laut früherem ADR):** Keine eigene Kryptografie oder Benutzerverwaltung, Standardprotokoll, selbst hostbar, Social-Login und Passkeys über Keycloak.

**Alternativen (laut ADR):** Auth0, Firebase Auth, Okta (Anbieterbindung bzw. überdimensioniert), eigene JWT-Lösung (verworfen).

**Konsequenzen:** Keycloak muss betrieben werden und ist für Anmeldung und Benutzersuche kritisch ([R-004](../11-risiken/R-004-keycloak-als-single-point-of-failure.md)); kurze Token-Lebensdauer erzwingt Refresh ([AE-008](AE-008-token-refresh-im-service-worker.md)); die Audience wird nicht geprüft ([R-005](../11-risiken/R-005-token-audience-wird-nicht-geprueft.md)).
