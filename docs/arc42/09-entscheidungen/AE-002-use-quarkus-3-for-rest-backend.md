---
id: AE-002
title: "Use Quarkus 3 for REST backend"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2025-09-28
quellen:
  - kb/adr/ADR-2025-09-28-002-quarkus.md
verknuepft: []
---

# AE-002: Use Quarkus 3 for REST backend

Quelle: `kb/adr/ADR-2025-09-28-002-quarkus.md` (Status im ADR: accepted, Datum: 2025-09-28). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

Chronos backend needed a modern Java REST API with:
- Low latency (< 100ms p99 for appointment queries)
- Lightweight container image (for Kubernetes deployment)
- Strong ORM support (object-relational mapping)
- Production-ready observability (metrics, traces)
- Support for async processing (push notifications)
- Java 21 (latest LTS release at time of decision)

Evaluated options:
1. **Quarkus** — native compilation, fast boot, built-in extensions
2. **Spring Boot** — mature, large ecosystem, but heavier container images
3. **Micronaut** — lightweight, but smaller ecosystem
4. **Vert.x** — async-first, but lower-level API
5. **Helidon** — lightweight, but less popular

## Entscheidung

Use **Quarkus 3** with Java 21, Hibernate ORM with Panache, and CDI for dependency injection.

### Key Choices
- **Quarkus**: Native compilation (GraalVM) reduces container size and boot time
- **Hibernate ORM + Panache**: ActiveRecord-style ORM, reduces boilerplate
- **CDI**: Built-in, enables event-driven architecture for side effects
- **JAX-RS**: REST endpoints, part of Jakarta EE standard
- **PostgreSQL**: Relational database (chosen separately, see ADR-2025-09-28-008)

## Konsequenzen

### Positive
- ✅ **Sub-100ms boot time** (native image), critical for serverless/Kubernetes
- ✅ **Small container images** (~150MB native vs. 500MB+ Spring Boot)
- ✅ **Memory efficient** (40-80MB native vs. 200-300MB Spring Boot)
- ✅ **Built-in extensions** for observability, security, ORM — no hunting for libraries
- ✅ **CDI enables event-driven architecture** (natural decoupling of side effects)
- ✅ **Panache reduces ORM boilerplate** (no getters/setters, active record pattern)
- ✅ **Live reload in dev mode** (`./mvnw quarkus:dev`)
- ✅ **Native compilation** reduces image size and boot time for serverless/containers
- ✅ **Jakarta EE standard** ensures portability

### Negative
- ❌ **Smaller ecosystem** than Spring (but Quarkus has most common libraries)
- ❌ **Native compilation adds build complexity** (GraalVM required for native builds) …
