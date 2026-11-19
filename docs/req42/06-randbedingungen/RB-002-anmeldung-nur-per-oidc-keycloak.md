---
id: RB-002
title: Anmeldung nur per OIDC (Keycloak)
status: entwurf
typ: randbedingung
quellen: [kb/constraints/C2025-09-28-002-oidc-required.md]
verknuepft: []
---

# RB-002: Anmeldung nur per OIDC (Keycloak)

Alle geschützten APIs verlangen ein gültiges OIDC-Token von Keycloak; keine eigene Authentifizierung, keine Basic-Auth.

## Herkunft
Bisher: C2025-09-28-002.
