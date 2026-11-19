---
id: KT-003
title: Nachbarsystem Keycloak
status: entwurf
typ: nachbarsystem
quellen:
  - frontend/server/api/auth
  - rust-backend/src/keycloak
  - backend/.../KeycloakProfileService.java
  - kb/adr/ADR-2025-09-28-003-keycloak.md
verknuepft: [RB-002, KZ-007]
---

# Nachbarsystem Keycloak

- **Rolle:** Identity Provider (OIDC Authorization Code Flow). Benutzerprofile und Anmeldedaten liegen in Keycloak, nicht in der Chronos-Datenbank (Migration `V2.0.0__remove_users_table.sql`).
- **Frontend → Keycloak:** Login, Token-Tausch, Refresh über `server/api/auth/*`.
- **Backend → Keycloak:** Token-Validierung (JWT), Admin-/Profil-API für Namen, Benutzersuche (Freundesvorschläge), verknüpfte Konten, Passkeys, Passwortänderung (`UserResource`, `KeycloakProfileService`; Rust: Modul `keycloak`, Anti-Corruption-Layer laut `ADR-2026-10-20-017`).
- **Rollen:** `ADMIN_API` aus `realm_access/roles` schützt Admin-Endpunkte.
- **Konfiguration:** `AUTH_SERVER_*` (Backend), `NUXT_AUTH_*` (Frontend).
- **Parity-Umgebung:** Keycloak 26.0.7 im Dev-Modus (`deployment/parity/keycloak.yaml`).
