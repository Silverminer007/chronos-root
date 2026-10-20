---
title: ADR-2026-10-20-017: User and settings endpoints with a Keycloak anti-corruption layer (Java parity)
date: 2026-10-20
status: Accepted
supercedes: (none)
superceded_by: (none)
---

## Context

Issue #98 ports `UserResource` and `SettingsResource` to the Rust backend and introduces the Keycloak admin boundary. The Java backend is the reference; the contract harness (#136) diffs both backends. The ticket answers of 2026-10-20 settle the open points (Java is copied 1:1, deviations go on the harness allowlist).

## Decision

### 1. One Keycloak module, behind a trait

- `rust-backend/src/keycloak/` is the only code that knows the Keycloak admin API. `KeycloakAdmin` (trait) exposes `get_user`, `update_user`, `send_verify_email`, `federated_identities`, `remove_federated_identity`, `credentials`, `delete_user`. `HttpKeycloakAdmin` implements it with `reqwest`; tests use `test_utils::FakeKeycloak`.
- Authentication is the client-credentials grant of the confidential client (like Java's `quarkus.keycloak.admin-client`). The access token is cached until 30 s before expiry.
- `KeycloakUrls` builds the browser-facing `…/protocol/openid-connect/auth?…&kc_action=…` links. `java_url_encode` reproduces `URLEncoder.encode` (space → `+`, `~` → `%7E`), so the URLs are byte-identical.
- Config: `AUTH_SERVER_BASE_URL`, `AUTH_SERVER_REALM` (default `master`, as in Java), `AUTH_SERVER_CLIENT_ID`, `AUTH_SERVER_CLIENT_SECRET`. The server refuses to start when one of the required variables is missing. The Helm values of the Rust deployment must provide them (outside this ticket's scope).

### 2. Endpoints and behaviour (Java parity)

- `GET`/`POST /api/v2/user`: the principal from the access token claims (`sub`, `given_name`, `family_name`, `email`) as `{id, first_name, last_name, email}`; missing claims are `null`.
- `DELETE /api/v2/user`: empty `405`, nothing is deleted (Java has no deletion here). `KeycloakAdmin::delete_user` exists for the admin port (#101). The ticket line "user deletion also removes domain data" is void (ticket answer 1a).
- `PATCH /api/v2/user`: same sequence as `KeycloakProfileService#updateUser`: read the current email only if an email is sent, case-insensitive comparison, `400 BAD_REQUEST "Redirect Url must be set to change email address"` for a changed email without `redirectUri`, always call `update` (also when empty), send the verify mail on an email change, answer with the request values plus `verifyEmailUrl`.
- `GET /user/linked`, `DELETE /user/linked/{providerId}` (`204`), `GET /user/link/{provider}`, `/link/passkey`, `/unlink/passkey` (a missing `passkeyId` becomes the literal `null`, as Java's string concatenation does), `/passkeys` (only `webauthn-passwordless`, `created_at` as `Instant.toString()`, `last_used_at` always `null`), `/change-password`.
- `GET`/`PUT /api/v2/settings`: `GET` creates the defaults on first access (the column defaults of Flyway V1.2.0, created with `INSERT … ON CONFLICT DO NOTHING` on the unique `user_oidcid` index); `PUT` answers an empty `200`, upserts all nine values, accepts trimmed/case-insensitive enum names.
- `/api/v2/me` stays as a Rust-only route with its unchanged answer; the harness skips it via an allowlist entry "Rust-only" (ticket answer 3).

### 3. Errors – unhandled Java exceptions are `500`

- Java's `GenericExceptionMapper` turns every unmapped exception into `500` with `{timestamp, status, error: "Internal Server Error", errorCode: "INTERNAL_ERROR", message: "Ein interner Fehler ist aufgetreten", path}`. Rust copies this body (`ApiError::internal`) with the request path (`/api/v2/…`, leading slash).
- Cases that are unhandled exceptions in Java and therefore `500` in Rust (ticket answers 4 and 5): a missing `redirectUri` on the four URL endpoints and on `PATCH` when the email is unchanged (Java's `URLEncoder.encode(null)`; the Keycloak update has already happened), an empty/`null`/malformed JSON body, `PUT /settings` with a missing, blank or unknown value (Java maps it to `null` and fails on the NOT NULL column; nothing is persisted), Keycloak failures including 404 (5xx, network, token errors).
- A Keycloak `404` (user missing in Keycloak) is a `500 INTERNAL_ERROR` with the generic body like every other Keycloak failure, as the ticket owner answered (answer 5: 404, 5xx and network errors all become 500). Java would answer `404` via `NotFoundExceptionMapper` for the admin client's `NotFoundException`; this is a deliberate deviation and gets an allowlist entry with this reason in the contract harness (#136).
- `ErrorResponse.error_code` is omitted from the JSON when empty so the 404 body has no `errorCode`, like Jackson's `NON_NULL`. All existing errors set a code, so they are unchanged.

### 4. Identity cache (`user_profiles`)

Java's `PrincipalContextFilter` upserts the token claims into `user_profiles` on every authenticated request. Rust does the same in a `cache_identity` layer (inside the auth layer); a failing upsert is logged and does not abort the request. `TokenClaims` and `PrincipalContext` therefore carry the profile claims.

## Consequences

- `build_router` takes a `UserState` (db pool, `Arc<dyn KeycloakAdmin>`, `KeycloakUrls`) as an additional argument; `test_utils::user_state_for_tests` serves router tests that do not touch the user endpoints.
- Open assumptions for the contract harness: the `path` field format (leading slash), the exact body of malformed JSON (assumed `500` like every unmapped exception).
