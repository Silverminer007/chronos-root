---
title: ADR-2026-10-20-018: Groups and friendships endpoints (Java parity)
date: 2026-10-20
status: Accepted
supercedes: (none)
superceded_by: (none)
---

## Context

Issue #97 ports `GroupsResource` and `FriendshipResource` of the Java backend to the Rust backend. The handlers in `rust-backend/src/groups/` existed but were not registered, and their contract differed from Java (paths, status codes, bodies, authorization, events, errors). The ticket asks for the Java contract 1:1: the same random requests are later sent to both backends and the responses diffed. The Java code is the reference ([ADR-2026-10-12-014](ADR-2026-10-12-014-rust-schema-identical-to-java.md) already aligned the schema).

## Decision

The existing Rust code was adapted, not duplicated: `groups/{models,repository,services,handlers,events}` were rewritten to the Java behaviour, the old `GroupServiceError`/`helpers` were removed, and the routes are registered in `app.rs` behind the auth middleware.

### 1. Endpoints

All paths and methods of the ticket under `/api/v2/groups` and `/api/v2/friendships`. Success statuses follow Java: group create/rename `200` with a `GroupDto`, delete/member add/remove/cancel request/remove friend `204`, send request `201` (no body), accept/decline `200` (no body).

### 2. JSON shapes

- `GroupDto {id, name, members}` and `UserDto {id, first_name, last_name}` serialize `null`s (Java has no `NON_NULL` there). Create and rename return `members: null` (Java sets it to `null` / never loads it).
- `FriendDto` and `FriendshipRequestDto` omit `null`s. `FriendshipRequestDto` uses camelCase (`requestId`, `userName`, `incoming`, ...); Lombok's `isIncoming` field serializes as `incoming`.
- Timestamps in `createdAt`, `respondedAt`, `friends_since` use Java's `Instant.toString()` format (`format_instant`: UTC, `Z`, fraction of 3/6/9 digits only if needed).
- Ordering, where Java is unspecified: groups and members by id; friends by name (as Java); requests by `createdAt` string descending (as Java).

### 3. Errors

Java's exception mappers produce `{timestamp, status, error, errorCode, message, path[, fieldErrors]}`. Rust uses the existing `AppError`, extended by:

- `ValidationException(String)` ⇒ `fieldErrors: {}` (empty map, not omitted); `ValidationException(field, msg)` ⇒ message `Validierungsfehler bei Feld: <field>` plus `fieldErrors`.
- `path`: `AppError` cannot know the request path, so `error::fill_error_path` (outermost middleware) rewrites the body of every `AppError` response with `uri().path()` (Java: `uriInfo.getPath()`; the leading slash is assumed). Only `AppError` responses are rewritten; the merged appointment endpoints answer with `AppointmentError` (plain text) and are unchanged, which `test_appointment_error_responses_keep_their_plain_text_body` pins. Today only the groups/friendships endpoints use `AppError`, so the middleware and the skipped empty `errorCode` do not alter any other endpoint.
- Non-numeric path/query numbers: Java answers `404` through `NotFoundExceptionMapper` (no `errorCode`, message `HTTP 404 Not Found`) ⇒ `AppError::RouteNotFound`; `ErrorResponse.error_code` is now skipped when empty.
- Database errors and every other unexpected failure ⇒ `500 INTERNAL_ERROR` "Ein interner Fehler ist aufgetreten" (details only logged), like `GenericExceptionMapper`.
- A missing or unreadable request body for create/edit group is a Java `NullPointerException`/Jackson error caught by `GenericExceptionMapper` ⇒ `500` (the edit authorization check runs first, as in Java). Unverified for malformed JSON: Quarkus may answer `400` there; revisit when the contract diff shows it.
- Order of checks is Java's. Visible quirk kept on purpose: `DELETE /groups/{unknown}` is `403` (ownership is checked before existence), not `404`.

### 4. Identity: `user_profiles` only

Java resolves names via `IdentityPort`: `user_profiles` first, Keycloak Admin API as fallback and for search. The Rust backend has no Keycloak admin client, so `users::identity` reads **only** `user_profiles`:

- A user without a profile row behaves like a Keycloak-deleted user: name `Gelöschter Benutzer`, `null` names in `UserDto`, the existence check of `POST /friendships/requests` is not performed: Java's `existsById` falls back to Keycloak, which answers unknown ids with the deleted sentinel (never `null`), so Java accepts any addressee id (`201`). Rust does the same instead of answering `404 Kein Benutzer mit der ID ... gefunden`; `IdentityRepository::exists` is therefore unused by this flow.
- Suggestions and email lookup search `user_profiles` (first name, last name, email; every whitespace separated term must be contained, case-insensitive, like Keycloak's search), ordered by last name, first name, id. Keycloak orders by username, which the cache does not hold, so the order of "recent" suggestions can differ from Java.
- Semantics of `GET /friendships/suggestions` (`FriendshipService.find_non_friends`, Java: `findNonFriends` / `findRecentNonFriends`):
  - Branching as in `FriendshipResource`: `search` null or shorter than 2 characters after trimming ⇒ "recent" variant with search `""` and fixed limit `5` (the `limit` parameter is ignored); otherwise trimmed `search` with `limit` (default `20`). A non-numeric `limit` is rejected before the branching in both backends (`404`).
  - Oversampling as in Java: `limit * 3` candidates are loaded from the identity search (`saturating_mul` instead of Java's overflowing `int` multiplication), then the requesting user and all friends are removed and the result is cut to `limit`. If many of the first `limit * 3` candidates are friends, fewer than `limit` suggestions are returned; there is no refill, same as in Java.
  - Only `ACCEPTED` friendships are excluded (`friend_ids` ↔ `getFriendOidcIds`). Users with a pending request (incoming or outgoing) or a declined one are still suggested.
  - Edge case negative `limit`: Rust clamps it to `0` and answers `200 []`. Java passes it on unchanged, and `Stream.limit(negative)` throws an `IllegalArgumentException`, i.e. most likely a `500`. This is a known, deliberate difference (not verified against a running Java backend); revisit if the contract diff shows it. `limit=0` gives `[]` in both.
  - The order of the result is the identity search order (see above), so it can differ from Java for the same reason.
- Profiles are filled by the Java backend's per-request upsert today; the Rust auth middleware does not upsert JWT claims yet (out of scope for this ticket). A user who never authenticated against a backend that upserts is not findable.

### 5. Authorization

The rules of `AuthorizationService` for groups are ported in `groups/authorization.rs` (messages included). The Java admin bypass (`isAdminRequest`) is not ported because the Rust principal has no admin concept.

### 6. Events

Events go through the existing `EventPublisher` (events table + NOTIFY) with the fields of the Java records in snake_case (`acting_user_id`, `new_member_id`, `request_id`, `requester_name`, ...): `GroupCreated/NameChanged/Deleted/MemberAdded/MemberRemovedEvent`, `FriendshipRequestSent/Accepted/Declined/RemovedEvent`. Cancelling a request fires no event (as in Java). Events are fired after the change is stored; a failing event bus is logged and does not fail the request (Java's observers run after the change as well).

The Java `GroupCreatedEvent` observer adds the creator as member in a second transaction after commit. Rust inserts group and creator membership in one transaction instead; the observable result (creator is member when the response is returned) is the same, without the window in which the creator is not yet a member.

### 7. Tests

`tests/groups_integration_test.rs` and `tests/friendships_integration_test.rs` call every endpoint through `build_router` (auth middleware, mock JWKS from `tests/common`) against a PostgreSQL test database and assert status, body, error body (incl. `path`, `fieldErrors`) and fired events. The former placeholder tests were replaced. Unit tests cover the DTO shapes, `format_instant` and `UserIdentity`.

## Consequences

### Positive

- ✅ Frontend/contract tests can switch between both backends for groups and friendships.
- ✅ One error pipeline (`AppError` + `fill_error_path`) that other endpoints can adopt to get Java-shaped errors.
- ✅ Repository functions take any `PgExecutor`, so multi-step writes (create group, replace declined request) run in one transaction.

### Negative

- ❌ Search and name resolution depend on the `user_profiles` cache; users unknown to it are invisible (see 4).
- ❌ No admin override and unverified malformed-JSON status (see 3, 5).
