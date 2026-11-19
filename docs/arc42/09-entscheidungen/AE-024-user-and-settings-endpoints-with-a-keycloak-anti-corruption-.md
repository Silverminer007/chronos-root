---
id: AE-024
title: "User and settings endpoints with a Keycloak anti-corruption layer (Java parity)"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2026-10-20
quellen:
  - kb/adr/ADR-2026-10-20-017-user-settings-keycloak-acl.md
verknuepft: []
---

# AE-024: User and settings endpoints with a Keycloak anti-corruption layer (Java parity)

Quelle: `kb/adr/ADR-2026-10-20-017-user-settings-keycloak-acl.md` (Status im ADR: accepted, Datum: 2026-10-20). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

Issue #98 ports `UserResource` and `SettingsResource` to the Rust backend and introduces the Keycloak admin boundary. The Java backend is the reference; the contract harness (#136) diffs both backends. The ticket answers of 2026-10-20 settle the open points (Java is copied 1:1, deviations go on the harness allowlist).

## Entscheidung

### 1. One Keycloak module, behind a trait

- `rust-backend/src/keycloak/` is the only code that knows the Keycloak admin API. `KeycloakAdmin` (trait) exposes `get_user`, `update_user`, `send_verify_email`, `federated_identities`, `remove_federated_identity`, `credentials`, `delete_user`. `HttpKeycloakAdmin` implements it with `reqwest`; tests use `test_utils::FakeKeycloak`.
- Authentication is the client-credentials grant of the confidential client (like Java's `quarkus.keycloak.admin-client`). The access token is cached until 30 s before expiry.
- `KeycloakUrls` builds the browser-facing `…/protocol/openid-connect/auth?…&kc_action=…` links. `java_url_encode` reproduces `URLEncoder.encode` (space → `+`, `~` → `%7E`), so the URLs are byte-identical.
- Config: `AUTH_SERVER_BASE_URL`, `AUTH_SERVER_REALM` (default `master`, as in Java), `AUTH_SERVER_CLIENT_ID`, `AUTH_SERVER_CLIENT_SECRET` …

## Konsequenzen

- **Pattern change (PATTERN-2025-09-28-001 principal context):** `PrincipalContext` and `TokenClaims` no longer carry only the OIDC subject; they also carry the profile claims first name, last name, email and picture. Services still read the current user from `PrincipalContext`, but the Rust principal is now a small profile object instead of a bare id, as in Java's `PrincipalContextFilter`. The pattern document describes the subject-only variant and is not changed by this ADR; readers of the pattern must take the extra fields into account.
- **Two separate Keycloak endpoints:** `KEYCLOAK_URL` (realm URL, used only for validating access tokens) stays unchanged and separate from the new `AUTH_SERVER_*` variables (`AUTH_SERVER_BASE_URL`, `AUTH_SERVER_REALM`, `AUTH_SERVER_CLIENT_ID`, `AUTH_SERVER_CLIENT_SECRET`, used only by the `keycloak` admin client with client-credentials) …
