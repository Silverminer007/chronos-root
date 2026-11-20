---
id: R-004
title: "Keycloak als Single Point of Failure"
status: entwurf
typ: risiko
quellen:
  - backend/src/main/resources/application.properties
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/KeycloakProfileService.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/infrastructure/LocalDbIdentityAdapter.java
  - frontend/server/api/auth/
verknuepft: [RB-002, AE-003, AE-049]
---

# R-004: Keycloak als Single Point of Failure

**Risiko:** Ist Keycloak (`auth.chronos-live.de`) gestört, funktionieren Anmeldung, Registrierung und Token-Refresh nicht; ebenso Profiländerung, Passwortwechsel, verknüpfte Konten, Passkeys und die Benutzersuche für Freundesvorschläge (Keycloak-Admin-API).

**Auswirkung:** Neue Sitzungen sind unmöglich; bestehende enden nach Ablauf des Refresh-Tokens. Namen anderer Benutzer liefert weiter der Profil-Cache ([AE-049](../09-entscheidungen/AE-049-lokaler-profil-cache-hinter-identityport.md)), unbekannte Benutzer jedoch nicht.

**Maßnahmen:** nur der Cache; Keycloak-Betrieb (Hochverfügbarkeit, Datenbank) liegt außerhalb dieses Repositories **(?)**.
