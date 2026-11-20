---
id: LS-003
title: Anmeldung ausschließlich per OIDC/Keycloak
status: entwurf
typ: strategie
quellen:
  - frontend/server/api/auth
  - backend/src/main/resources/db/migration/V2.0.0__remove_users_table.sql
  - backend/src/main/resources/db/migration/V2.1.0__create_user_profiles.sql
verknuepft: [AE-003, KT-003, KZ-012, RB-002]
---

# Anmeldung ausschließlich per OIDC/Keycloak

Es gibt keine eigene Benutzerverwaltung. Keycloak liefert Registrierung, Passwort, verknüpfte Konten und Passkeys; das Backend kennt Benutzer nur über die Subject-ID (`oidcId`) und hält Profildaten höchstens als Cache ([KZ-012](../08-konzepte/KZ-012-identitaet-und-profil-cache.md)). Anmeldung per Authorization Code Flow, Tokens in httpOnly-Cookies, Token-Erneuerung durch den Service Worker. Die Migration `V2.0.0` hat die frühere Tabelle `users` entfernt. Begründung: [AE-003](../09-entscheidungen/AE-003-keycloak-oidc-fuer-die-authentifizierung.md).
