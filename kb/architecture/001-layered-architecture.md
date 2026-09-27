---
name: Layered Architecture Pattern
description: Backend follows strict layered architecture for separation of concerns and maintainability
category: architecture
last-updated: 2026-09-27
---

# Layered Architecture Pattern

## Decision
The Chronos backend follows a strict layered architecture within the `de.chronos_live.chronos_date_api` package and mirrors this in the admin package.

## Layers and Responsibilities

### Presentation Layer (`presentation/`)
- JAX-RS resources map HTTP requests/responses
- Validate HTTP input shapes
- Delegate all business logic to services
- Return DTOs, never entities

### Application Layer (`application/`)
- Business logic services implement core functionality
- Fire CDI events for decoupled side-effects
- Coordinate between domain and infrastructure layers
- No HTTP knowledge; framework-agnostic

### Domain Layer (`domain/`)
- Hibernate/Panache entities are the source of truth for data model
- Entities encapsulate business invariants
- No framework-specific code in entities
- Use `@Entity`, `@Column` annotations for ORM mapping

### Infrastructure Layer (`infrastructure/`)
- Panache repositories for data access
- Adapters for external services (`WebPushAdapter`)
- Configuration producers for CDI
- No business logic; only cross-cutting concerns

### Mapper Layer (`mapper/`)
- MapStruct mappers for entity ↔ DTO conversions
- One-way converters only
- Pure functions, no side effects

### Exception Layer (`exception/`)
- Custom exception hierarchy for domain errors
- JAX-RS `ExceptionMapper` implementations
- Proper HTTP status code mapping

### Security Layer (`security/`)
- `PrincipalContext` (request-scoped) holds current user's OIDC ID
- `PrincipalContextFilter` extracts JWT once per request
- `AuthorizationService` enforces role/membership rules

## Rationale

This separation allows:
- **Testability**: Each layer can be tested independently
- **Maintainability**: Clear responsibilities reduce complexity
- **Reusability**: Application logic is decoupled from HTTP concerns
- **Scaling**: New agents can implement business logic by calling services directly

## Implementation Notes

- Services are stateless and annotated with `@ApplicationScoped`
- Repositories extend `PanacheRepository<Entity, ID>` for query DSL
- All cross-cutting concerns (logging, metrics) handled via interceptors, not services
