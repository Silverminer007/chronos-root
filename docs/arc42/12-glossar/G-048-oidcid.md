---
id: G-048
title: "oidcId"
status: entwurf
typ: begriff
quellen:
  - backend/src/main/resources/db/migration/V2.0.0__remove_users_table.sql
  - backend/src/main/java/de/chronos_live/chronos_date_api/domain/UserIdentity.java
verknuepft: [G-027, G-050, AE-049]
---

# G-048: oidcId

**Code-Name:** `oidcId` / `…OidcId` (Java), Spalten `user_oidcid`, `owner_oidcid` u. a.; API-Felder `user_id`, `sender_id`

Benutzerkennung in Chronos: der Claim `sub` (UUID als String) des [Keycloak](G-027-keycloak.md)-Tokens. Es gibt keine eigene Benutzertabelle; alle Verweise auf Benutzer tragen diese Kennung.
