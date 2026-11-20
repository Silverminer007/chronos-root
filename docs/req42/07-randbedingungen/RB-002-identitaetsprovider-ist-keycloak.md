---
id: RB-002
title: Identitätsprovider ist Keycloak
status: entwurf
typ: randbedingung
kategorie: technisch
quellen: [backend/src/main/resources/application.properties, frontend/server/api/auth/]
verknuepft: []
---

# RB-002: Identitätsprovider ist Keycloak

Anmeldung und Identität laufen ausschließlich über das vorhandene Keycloak (OIDC); Chronos hat keine eigene Benutzer- oder Passwortverwaltung und keine Basic-Authentifizierung. Benutzerrollen wie `ADMIN_API` stammen aus Keycloak-Realm-Rollen.
