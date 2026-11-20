---
id: G-034
title: "PrincipalContext"
status: entwurf
typ: begriff
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/security/PrincipalContext.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/security/PrincipalContextFilter.java
verknuepft: [G-048, G-050, AE-051]
---

# G-034: PrincipalContext

**Code-Name:** `PrincipalContext` (request-scoped CDI-Bean), `UserIdentity`

Hält pro Anfrage den angemeldeten Benutzer (`UserIdentity` aus den JWT-Claims, [oidcId](G-048-oidcid.md)) und das Kennzeichen `adminRequest` für Pfade `/api/v2/admin/*`. `PrincipalContextFilter` befüllt ihn und aktualisiert den [Profil-Cache](G-050-profil-cache.md).
