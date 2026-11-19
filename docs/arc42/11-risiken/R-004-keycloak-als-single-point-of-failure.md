---
id: R-004
title: "Keycloak als Single Point of Failure"
status: entwurf
typ: risiko
quellen:
  - kb/adr/ADR-2025-09-28-003-keycloak.md
  - kb/adr/ADR-2026-10-20-017-user-settings-keycloak-acl.md
  - Ticket #296
verknuepft: [RB-002]
---

# R-004: Keycloak als Single Point of Failure

Anmeldung und Benutzerprofile hängen an Keycloak (Friend-Suche, Profil, Passkeys laufen über dessen API). Ticket #296 will Benutzerdaten nur noch in Keycloak halten, was die Abhängigkeit erhöht.
