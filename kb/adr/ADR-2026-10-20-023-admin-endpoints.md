# ADR-2026-10-20-023: Admin endpoints (`/api/v2/admin/*`) in the Rust backend

- Status: accepted
- Ticket: #101 (map #21: Rust re-implementation, Java is the reference)

## Context

Java has six `Admin*Resource` classes under `/api/v2/admin/*`, all `@RolesAllowed("ADMIN_API")` with
`quarkus.oidc.roles.role-claim-path=realm_access/roles`. `PrincipalContextFilter` marks every request below
`/api/v2/admin/` as an *admin request*, and `AuthorizationService` returns early for such requests, so the
admin endpoints reuse the normal services without their participant checks. User data comes from Keycloak, the
rest from the database.

## Decisions

1. **Role check as an extractor, not a router layer.** The token claim `realm_access.roles` is read into
   `TokenClaims`/`PrincipalContext.roles`; `security::AdminPrincipal` is an axum extractor that every admin
   handler takes as first argument. Without a principal it answers 401, without `ADMIN_API` an **empty 403**
   (Quarkus' answer to a `@RolesAllowed` denial has no body, unlike the `ForbiddenException` JSON of the
   business layer). Because the extractor runs before the body is read, a normal token never reaches a service.
   A forgotten extractor would be an unprotected route; `admin_endpoints_test` therefore walks all 14 routes
   with an anonymous, a normal and an admin token.
2. **The "admin request" bypass is explicit.** Java's `isAdminRequest()` becomes `AppointmentService::as_admin()`
   (skips the RESPONSIBLE check when adding a group) and `GroupService::add_group_member_as_admin` (skips the
   membership and friendship check; the acting user of the event is empty, Java passes `null`). Nothing else
   changes in those services, so the normal endpoints keep their rules.
3. **Reuse instead of duplication.** Appointment creation, group creation, participation status change and
   adding a group call the existing services; `send_message`, `list_page` (appointments of *any* status, ordered
   by id, as Panache's `findAll().page(..)`) and `FriendshipService::befriend` are new methods of the owning
   features. Only the statistics and the push-log query live in `admin/repository.rs`.
4. **Java quirks kept:**
   - `POST /appointments` answers **200** (not 201) with the appointment, the admin becomes RESPONSIBLE.
   - `POST /groups` answers 201 with the bare group id; missing body or `ownerId` is an empty 400.
   - `POST /groups/{id}/users/{userId}` swallows the `ValidationException` (already a member) and answers 200;
     an unknown group is the usual 404 JSON.
   - `POST /friendship/befriend`: all pairs `(i, j >= i)` of `userIds`; identical ids, existing PENDING/ACCEPTED
     requests are skipped, a DECLINED one is replaced by an ACCEPTED one; no event. Missing body is an empty
     400, missing `userIds` a 500.
   - Unreadable bodies, missing required fields, unknown enum names (`UserRole.valueOf`), bad `Instant.parse`
     input and `Page.of` with `page < 0` or `size < 1` are Java NPE/`IllegalArgumentException`s and therefore the
     generic 500 JSON; a non-numeric `Long`/`int` path or query parameter is the 404 of Jakarta REST.
   - `POST /push/test/{userId}` answers 204; without a body it sends `{"title": "\"Testbenachrichtigung\"\n",
     "body": "\"Push\" funktioniert!\n"}` (the Java text blocks keep quotes and the trailing newline).
   - `GET /push/log` is a bare JSON array (not paged envelope), newest first (`id` breaks ties, which Java leaves
     undefined), `success` follows `Boolean.valueOf` (everything but `true` is false).
   - `GET /stats` counts rows with `status = ordinal` for appointments, strings for the other tables, and takes
     the user total from Keycloak.
   - Every collection route is also served with a trailing slash (`@Path("/")`).
5. **Keycloak.** `KeycloakAdmin` gets `list_users(first, max)` and `count_users()`, and `KeycloakUser` carries
   id, names, email and `createdTimestamp` (it only had the email). `GET /users/{id}` maps a Keycloak 404 to an
   empty 404; **`DELETE /users/{id}` answers 204 also for an unknown id**, because Java's admin client returns
   the `Response` of `UsersResource.delete` without throwing, so the `catch (NotFoundException)` in the resource
   never fires. Other failures are the generic 500. `FakeKeycloak` serves a scripted realm for tests.
6. **Push.** `PushState` now carries the `NotificationDispatcher` (default: no sender, notifications are omitted
   like without VAPID keys); `main` hands it the same dispatcher as the event handler, so the test endpoint logs
   to `push_notification_log` and removes dead subscriptions like any other send.
7. **Appointment error bodies are not touched.** Errors raised by the appointment services keep the plain-text
   mapping of `/api/v2/appointments` (`AppointmentError`); aligning those with Java's JSON `ErrorResponse` is
   outside this ticket and will show up in the parity harness (ADR-2026-10-20-021) for both the normal and the
   admin routes at once.

## Consequences

- The role claim is available to future endpoints (`PrincipalContext::has_role`).
- `TestApp::grant_admin` in `tests/common` creates tokens with the admin role for integration tests.
- Known gap: adding a group to an appointment does not create participations for the group members in Rust
  (Java does); it is a property of the existing appointment service and affects the normal endpoint as well.
