# ADR 0011: Store user_id as UUID in PrincipalContext

**Date:** 2026-10-03  
**Status:** Accepted  
**Context:** `PrincipalContext` stored user_id as `String`, requiring handlers and services to convert strings to UUIDs when needed. JWT token subject (OIDC `sub`) is the user's UUID.  
**Decision:** Store `user_id: Uuid` in `PrincipalContext`. The auth middleware parses the JWT subject once and returns **401 Unauthorized** if it is not a valid UUID.

**Rationale:**
- Type-safety: UUID is enforced at the middleware boundary, not scattered across handlers
- Consistency: All downstream code works with `Uuid`, eliminating String-to-Uuid conversions
- Fail-fast: Invalid UUIDs are caught immediately in auth, preventing invalid data from entering services

**Implementation:**
- Auth middleware parses `claims.sub` as `Uuid::parse()` and returns 401 if invalid
- `PrincipalContext { user_id: Uuid }`
- All service methods use `Uuid` directly, no string conversions
