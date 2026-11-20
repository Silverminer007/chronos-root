---
id: R-005
title: "Token-Audience wird nicht geprüft"
status: entwurf
typ: risiko
quellen:
  - backend/src/main/resources/application.properties
verknuepft: [RB-002, AE-003]
---

# R-005: Token-Audience wird nicht geprüft

**Risiko:** `quarkus.oidc` ist ohne `token.audience` konfiguriert (nur `client-id`, `auth-server-url`, Rollen-Claim). Das Backend akzeptiert damit jedes gültig signierte Token des Keycloak-Realms, auch solche, die für andere Clients desselben Realms ausgestellt wurden.

**Auswirkung:** Teilen sich weitere Anwendungen den Realm, könnten deren Tokens gegen die Chronos-API verwendbar sein. Mit einem einzigen Client im Realm besteht kein Zusatzrisiko **(Anzahl der Clients unbekannt (?))**.

**Maßnahmen:** `quarkus.oidc.token.audience` setzen und in Keycloak einen Audience-Mapper konfigurieren.
