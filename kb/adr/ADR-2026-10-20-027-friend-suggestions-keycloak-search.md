---
title: ADR-2026-10-20-027: Friend suggestions search Keycloak (Java parity)
date: 2026-10-20
status: Accepted
supercedes: ADR-2026-10-20-019 §4 (only the "suggestions search `user_profiles`" decision)
superceded_by: (none)
---

## Context

Ticket #160: `GET /api/v2/friendships/suggestions` returned `[]` in Rust while Java returned users known to the realm (e.g. `parity-admin`). ADR-2026-10-20-019 §4 let Rust search only the `user_profiles` cache. Java's `LocalDbIdentityAdapter#search` asks Keycloak (`users().search(query, 0, limit)`), so users who never authenticated against a backend with the profile upsert are suggested in Java and invisible in Rust. Ticket answer (map #21: Java is the reference, copy 1:1): search Keycloak in Rust as well.

## Decision

- `KeycloakAdmin` (ADR-2026-10-20-017) gets one more method, `search_users(search, max)` = `GET /admin/realms/{realm}/users?search=…&first=0&max=…`. No new access path and no new configuration: the confidential client and `AUTH_SERVER_*` are reused. `KeycloakUser` carries the first `picture` attribute value (Java `repToIdentity`).
- `FriendshipService::find_non_friends` takes the Keycloak client, loads `limit * 3` candidates from Keycloak (oversampling, no refill, as before), removes the requesting user and the accepted friends and cuts to `limit`. The branching in the handler (search shorter than 2 characters ⇒ search `""`, limit 5) is unchanged.
- **Order:** the result keeps the order Keycloak returns (sorted by username), exactly as Java. The earlier remark that "recent" suggestions may be ordered differently from Java (cache order by last name, first name, id) no longer applies.
- **Cache path:** like Java, every hit of the search is upserted into `user_profiles` (`UserRepository::upsert_identity`, shared with the per-request upsert). Hits without an id are skipped (Java `upsert` ignores a `null` id).
- **Errors:** a failing Keycloak call (any status, network, token) is not handled in Java and becomes the generic `500 INTERNAL_ERROR` (as decided in #98).
- `IdentityRepository::search` (cache search) is removed, nothing uses it any more. The email lookup of `POST /friendships/requests` still reads `user_profiles` (unchanged, out of scope).
- Wiring: the suggestions route carries its own state (`SuggestionsState`: `AppState` + the Keycloak client from `UserState`) so `AppState` and its many constructions stay unchanged.

## Consequences

- Keycloak-only users such as `parity-admin` are suggested; the contract harness step `friend suggestions` runs without difference. The `#160` entry in `contract-tests/allowlist.json` and its row in the parity report are removed.
- Tests use `FakeKeycloak::search_users` (realm users in their list order, every whitespace separated term contained in first name, last name or email, case-insensitive). The fake does not sort by username; its list order stands for Keycloak's order.
- Known remaining difference: Keycloak's `search` also matches the username; the fake and the integration tests cover names and email only.
