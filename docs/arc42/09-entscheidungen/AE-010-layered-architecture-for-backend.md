---
id: AE-010
title: "Layered architecture for backend"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2025-09-28
quellen:
  - kb/adr/ADR-2025-09-28-007-layered-backend.md
verknuepft: []
---

# AE-010: Layered architecture for backend

Quelle: `kb/adr/ADR-2025-09-28-007-layered-backend.md` (Status im ADR: accepted, Datum: 2025-09-28). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

The backend needed a clear separation of concerns to enable:
- Easy testing (mock at layer boundaries)
- Clear data flow (requests in, responses out)
- Scalability (add new features without spaghetti code)
- Maintainability (understand what each layer does)

Standard layered architectures were evaluated:
1. **Three-layer** (resource → service → repository)
2. **Four-layer** (resource → service → repository → database)
3. **Hexagonal** (domain at center, adapters at edges)
4. **Vertical slices** (feature-oriented instead of layer-oriented)

## Entscheidung

Use a **four-layer architecture** (presentation → application → infrastructure → domain):

Each layer has clear responsibilities and dependencies flow downward only.

## Konsequenzen

### Positive
- ✅ **Clear boundaries**: Each layer has one job
- ✅ **Testable**: Mock repositories to test services, mock services to test resources
- ✅ **Scalable**: Add new features by adding new resources/services (no spaghetti)
- ✅ **Observable**: Data flow is clear and traceable
- ✅ **Enables CDI events**: Services fire events, infrastructure observes them (loose coupling)
- ✅ **Authorization centralized**: All authorization logic in services, not scattered

### Negative
- ❌ **Boilerplate**: Each layer needs DTOs, mappers, repositories
- ❌ **Indirection**: Requests bounce through multiple layers
- ❌ **Duplication**: Similar logic across resources/services/repositories
- ❌ **Latency**: Multiple function calls (negligible in practice)

### Trade-offs
- **Boilerplate vs. clarity**: More classes, but clearer data flow
- **Vertical slices vs …
