---
id: LS-003
title: Anmeldung ausschließlich per OIDC/Keycloak
status: entwurf
typ: strategie
quellen:
  - kb/adr/ADR-2025-09-28-003-keycloak.md
verknuepft: [RB-002, KT-003]
---

# Anmeldung ausschließlich per OIDC/Keycloak

Keine eigene Benutzerverwaltung. Keycloak liefert Registrierung, Passwort-Reset und Passkeys; das Backend kennt nur die OIDC-Subject-ID (`oidcId`) und speichert Profildaten höchstens als `UserProfile`. Authorization Code Flow, Tokens in httpOnly-Cookies.
