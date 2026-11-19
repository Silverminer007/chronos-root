---
id: AE-026
title: "Groups and friendships endpoints (Java parity)"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2026-10-20
quellen:
  - kb/adr/ADR-2026-10-20-019-groups-friendships-endpoints.md
verknuepft: []
---

# AE-026: Groups and friendships endpoints (Java parity)

Quelle: `kb/adr/ADR-2026-10-20-019-groups-friendships-endpoints.md` (Status im ADR: accepted, Datum: 2026-10-20). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

Issue #97 ports `GroupsResource` and `FriendshipResource` of the Java backend to the Rust backend. The handlers in `rust-backend/src/groups/` existed but were not registered, and their contract differed from Java (paths, status codes, bodies, authorization, events, errors). The ticket asks for the Java contract 1:1: the same random requests are later sent to both backends and the responses diffed. The Java code is the reference ([ADR-2026-10-12-014](ADR-2026-10-12-014-rust-schema-identical-to-java.md) already aligned the schema).

## Entscheidung

The existing Rust code was adapted, not duplicated: `groups/{models,repository,services,handlers,events}` were rewritten to the Java behaviour, the old `GroupServiceError`/`helpers` were removed, and the routes are registered in `app.rs` behind the auth middleware.

### 1. Endpoints

All paths and methods of the ticket under `/api/v2/groups` and `/api/v2/friendships`. Success statuses follow Java: group create/rename `200` with a `GroupDto`, delete/member add/remove/cancel request/remove friend `204`, send request `201` (no body), accept/decline `200` (no body).

### 2. JSON shapes

- `GroupDto {id, name, members}` and `UserDto {id, first_name, last_name}` serialize `null`s (Java has no `NON_NULL` there). Create and rename return `members: null` (Java sets it to `null` / never loads it).
- `FriendDto` and `FriendshipRequestDto` omit `null`s …

## Konsequenzen

### Positive

- ✅ Frontend/contract tests can switch between both backends for groups and friendships.
- ✅ One error pipeline (`AppError` + `fill_error_path`) that other endpoints can adopt to get Java-shaped errors.
- ✅ Repository functions take any `PgExecutor`, so multi-step writes (create group, replace declined request) run in one transaction.

### Negative

- ❌ Name resolution and the email lookup depend on the `user_profiles` cache; users unknown to it appear as deleted users or are not found by email (see 4). Suggestions search Keycloak (ADR-027).
- ❌ No admin override and unverified malformed-JSON status (see 3, 5).
