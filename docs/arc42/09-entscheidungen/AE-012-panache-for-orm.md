---
id: AE-012
title: "Panache for ORM"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2025-09-28
quellen:
  - kb/adr/ADR-2025-09-28-009-panache.md
verknuepft: []
---

# AE-012: Panache for ORM

Quelle: `kb/adr/ADR-2025-09-28-009-panache.md` (Status im ADR: accepted, Datum: 2025-09-28). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

Hibernate ORM is the standard for Java, but its API is verbose:

Panache provides an **ActiveRecord** wrapper that reduces boilerplate:

## Entscheidung

Use **Panache for ORM**, with Quarkus's built-in Panache extension. This gives us:
- **ActiveRecord pattern**: Entities have query methods directly (cleaner)
- **Fewer lines of code**: No EntityManager injection needed
- **Familiar API**: Similar to Rails ActiveRecord or Django ORM
- **Still uses Hibernate**: All the power of Hibernate, less boilerplate

## Konsequenzen

### Positive
- ✅ **Reduced boilerplate**: No EntityManager, no QueryBuilders
- ✅ **Familiar pattern**: ActiveRecord is common in web frameworks
- ✅ **Still uses Hibernate**: All ORM features still available
- ✅ **Type-safe queries**: No string queries (we use PanacheQuery)
- ✅ **Easy pagination**: Built-in `page()` and `pageCount()`
- ✅ **Simple repository pattern**: Extend `PanacheRepository<T>`

### Negative
- ❌ **Entities tied to Panache**: Must extend `PanacheEntity`
- ❌ **Less explicit**: Magic methods on entities can be confusing for newcomers
- ❌ **Limited to Quarkus**: If we ever switch from Quarkus, we lose Panache
- ❌ **Queries still string-based**: String queries (no compile-time checking)

### Trade-offs
- **Convenience vs. flexibility**: Panache is convenient but less flexible than raw Hibernate
- **Magic vs …
