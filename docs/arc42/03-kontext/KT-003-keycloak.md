---
id: KT-003
title: Nachbarsystem Keycloak
status: entwurf
typ: nachbarsystem
quellen:
  - frontend/server/api/auth
  - backend/src/main/resources/application.properties
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/KeycloakProfileService.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/infrastructure/LocalDbIdentityAdapter.java
  - deployment/values-prod.yaml
verknuepft: [LS-003, KZ-007, KZ-012, RB-002]
---

# Nachbarsystem Keycloak

- **Rolle:** Identity Provider für OIDC (Authorization Code Flow). Er hält Benutzerprofile und Anmeldedaten; die Chronos-Datenbank kennt nur die Subject-ID (`oidcId`) und einen Profil-Cache ([KZ-012](../08-konzepte/KZ-012-identitaet-und-profil-cache.md)). Produktiver Realm `chronos` unter `https://auth.chronos-live.de` (`values-prod.yaml`).
- **Frontend → Keycloak** (`server/api/auth/*`): Weiterleitung zu `…/auth` und `…/registrations` mit `scope=openid profile email`, Code-Tausch und Refresh am Token-Endpunkt (nur `client_id`, kein Client-Secret), Logout mit Refresh-Token.
- **Backend → Keycloak:** JWT-Validierung über `quarkus-oidc` (Client mit Secret); Admin-REST-Client mit Client-Credentials für Benutzersuche und -abfrage (`LocalDbIdentityAdapter`), Profilpflege, verknüpfte Konten und Passkeys (`KeycloakProfileService`). Für Konto-, Passkey- und Passwortaktionen erzeugt das Backend Keycloak-Aktions-URLs mit Rücksprung-URI, zu denen das Frontend weiterleitet.
- **Verwendete Claims:** `sub`, `given_name`, `family_name`, `email`, `picture`; Rollen aus `realm_access/roles`, `ADMIN_API` schützt die Admin-Endpunkte (`quarkus.oidc.roles.role-claim-path`).
- **Konfiguration:** `AUTH_SERVER_*` (Backend) und `NUXT_AUTH_*` (Frontend), siehe [KZ-013](../08-konzepte/KZ-013-konfiguration.md); Backend und Frontend-Konfiguration beziehen sich auf denselben Realm.
- **Ausfall:** Anmeldung, Token-Refresh und Profilauflösung für nicht gecachte Benutzer hängen an Keycloak ([KZ-012](../08-konzepte/KZ-012-identitaet-und-profil-cache.md)).
