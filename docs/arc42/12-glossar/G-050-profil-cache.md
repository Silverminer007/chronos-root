---
id: G-050
title: "Profil-Cache"
status: entwurf
typ: begriff
quellen:
  - backend/src/main/resources/db/migration/V2.1.0__create_user_profiles.sql
  - backend/src/main/java/de/chronos_live/chronos_date_api/infrastructure/LocalDbIdentityAdapter.java
verknuepft: [G-048, AE-049, R-009]
---

# G-050: Profil-Cache

**Code-Name:** Tabelle `user_profiles`, `IdentityPort`, `LocalDbIdentityAdapter`, `UserIdentity`

Lokale Kopie von Name, E-Mail und Bild der Benutzer aus [Keycloak](G-027-keycloak.md), geführt je [oidcId](G-048-oidcid.md) ([AE-049](../09-entscheidungen/AE-049-lokaler-profil-cache-hinter-identityport.md)). Gelöschte Keycloak-Benutzer erscheinen als „Gelöschter Benutzer".
