---
id: AE-005
title: "Store user_id as UUID in PrincipalContext"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2026-10-03
quellen:
  - kb/adr/0011-user-id-uuid-in-principal.md
verknuepft: []
---

# AE-005: Store user_id as UUID in PrincipalContext

Quelle: `kb/adr/0011-user-id-uuid-in-principal.md` (Status im ADR: accepted, Datum: 2026-10-03). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

`PrincipalContext` stored user_id as `String`, requiring handlers and services to convert strings to UUIDs when needed. JWT token subject (OIDC `sub`) is the user's UUID.

## Entscheidung

Store `user_id: Uuid` in `PrincipalContext`. The auth middleware parses the JWT subject once and returns **401 Unauthorized** if it is not a valid UUID.

## Konsequenzen

- Type-safety: UUID is enforced at the middleware boundary, not scattered across handlers
- Consistency: All downstream code works with `Uuid`, eliminating String-to-Uuid conversions
- Fail-fast: Invalid UUIDs are caught immediately in auth, preventing invalid data from entering services
