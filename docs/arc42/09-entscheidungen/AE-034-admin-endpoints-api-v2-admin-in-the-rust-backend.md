---
id: AE-034
title: "Admin endpoints (`/api/v2/admin/*`) in the Rust backend"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2026-10-20
quellen:
  - kb/adr/ADR-2026-10-20-023-admin-endpoints.md
verknuepft: []
---

# AE-034: Admin endpoints (`/api/v2/admin/*`) in the Rust backend

Quelle: `kb/adr/ADR-2026-10-20-023-admin-endpoints.md` (Status im ADR: accepted, Datum: 2026-10-20). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

Java has six `Admin*Resource` classes under `/api/v2/admin/*`, all `@RolesAllowed("ADMIN_API")` with
`quarkus.oidc.roles.role-claim-path=realm_access/roles`. `PrincipalContextFilter` marks every request below
`/api/v2/admin/` as an *admin request*, and `AuthorizationService` returns early for such requests, so the
admin endpoints reuse the normal services without their participant checks. User data comes from Keycloak, the
rest from the database.

## Entscheidung

1. **Role check as an extractor, not a router layer.** The token claim `realm_access.roles` is read into
   `TokenClaims`/`PrincipalContext.roles`; `security::AdminPrincipal` is an axum extractor that every admin
   handler takes as first argument. Without a principal it answers 401, without `ADMIN_API` an **empty 403**
   (Quarkus' answer to a `@RolesAllowed` denial has no body, unlike the `ForbiddenException` JSON of the
   business layer). Because the extractor runs before the body is read, a normal token never reaches a service.
   A forgotten extractor would be an unprotected route; `admin_endpoints_test` therefore walks all 14 routes
   with an anonymous, a normal and an admin token.
2. **The "admin request" bypass is explicit.** Java's `isAdminRequest()` becomes `AppointmentService::as_admin()` …

## Konsequenzen

- The role claim is available to future endpoints (`PrincipalContext::has_role`).
- `TestApp::grant_admin` in `tests/common` creates tokens with the admin role for integration tests.
- Known gap: adding a group to an appointment does not create participations for the group members in Rust
  (Java does); it is a property of the existing appointment service and affects the normal endpoint as well.
