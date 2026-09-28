---
title: ADR-2025-09-28-007: Layered architecture for backend
date: 2025-09-28
status: Accepted
supercedes: (none)
superceded_by: (none)
---

## Context

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

## Decision

Use a **four-layer architecture** (presentation → application → infrastructure → domain):

```
JAX-RS Resources (HTTP)
    ↓ (validate, deserialize)
Services (business logic)
    ↓ (fire events, authorize, query)
Repositories (ORM)
    ↓
Entities (domain model)
    ↓
PostgreSQL
```

Each layer has clear responsibilities and dependencies flow downward only.

## Consequences

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
- **Vertical slices vs. horizontal layers**: Layers work better for small/medium projects; slices better for large monoliths

## Layer Responsibilities

### Presentation (`presentation/`)
- JAX-RS `@Resource` classes
- HTTP status codes, request/response mapping
- Input validation (using Hibernate Validator or JAX-RS constraints)
- Never contains business logic

**Example**:
```java
@Path("/api/v2/appointments")
public class AppointmentResource {
  @Inject AppointmentService service;
  @Inject PrincipalContext principal;

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response create(CreateAppointmentDto dto) {
    var appointment = service.create(dto, principal.getSubject());
    return Response.ok(appointmentMapper.toDto(appointment)).build();
  }
}
```

### Application (`application/`)
- Business logic services
- Transactions (via `@Transactional`)
- Authorization checks
- CDI event firing (for side effects)
- Database queries via injected repositories
- Never queries database directly (uses repositories)

**Example**:
```java
@ApplicationScoped
public class AppointmentService {
  @Inject AppointmentRepository repo;
  @Inject AuthorizationService authz;
  @Inject Event<AppointmentCreatedEvent> events;

  @Transactional
  public Appointment create(CreateAppointmentDto dto, String userId) {
    authz.checkCanCreateAppointment(userId, dto.groupId);
    var appt = new Appointment(dto.title, userId);
    repo.persist(appt);
    events.fire(new AppointmentCreatedEvent(appt));
    return appt;
  }
}
```

### Infrastructure (`infrastructure/`)
- Panache repositories (ORM layer)
- External service adapters (WebPushAdapter, etc.)
- Cache layer (if implemented)
- Event observers (for side effects)

**Example**:
```java
@ApplicationScoped
public class AppointmentRepository implements PanacheRepository<Appointment> {
  public List<Appointment> findByOrganizer(String organizerId) {
    return list("organizerId", organizerId);
  }
}

@ApplicationScoped
public class WebPushService {
  @Inject Event<AppointmentCreatedEvent> events;

  void onAppointmentCreated(@Observes AppointmentCreatedEvent evt) {
    // Send push notifications
    sendNotifications(evt.appointment());
  }
}
```

### Domain (`domain/`)
- Hibernate JPA entities
- Entity relationships
- Validation (via `@NotNull`, etc.)
- Value objects (if using advanced patterns)
- Never contains queries or logic

**Example**:
```java
@Entity
public class Appointment extends PanacheEntity {
  @NotNull
  public String title;
  @NotNull
  public String organizerId;
  @OneToMany(mappedBy = "appointment")
  public List<Participation> participations;
}
```

## Data Flow Example: Create Appointment

1. **Client** sends `POST /api/v2/appointments` with JSON body
2. **Resource** receives request, deserializes DTO, calls `service.create(dto, principal.getSubject())`
3. **Service** validates authorization, creates entity, persists to repository, fires event
4. **Repository** saves to PostgreSQL via Hibernate
5. **Event observer** (WebPushService) picks up event, sends notifications
6. **Resource** receives appointment, maps to DTO, returns 200 with entity

## Mapping between Layers

- **Resource ↔ Service**: DTOs (CreateAppointmentDto, AppointmentResponseDto)
- **Service ↔ Repository**: Entities (Appointment)
- **Repository ↔ Database**: Hibernate ORM (transparent)

Use MapStruct for DTO mapping:
```java
@Mapper(componentModel = "cdi")
public interface AppointmentMapper {
  AppointmentResponseDto toDto(Appointment entity);
  Appointment toEntity(CreateAppointmentDto dto);
}
```

## Testing Each Layer

```bash
# Unit test a service (mock repositories)
class AppointmentServiceTest {
  @Mock AppointmentRepository repo;
  @InjectMocks AppointmentService service;

  @Test
  void testCreate() {
    // Arrange
    var dto = new CreateAppointmentDto(...);
    when(repo.persist(any())).then(invocation -> {
      Appointment appt = invocation.getArgument(0);
      appt.id = 1L; // Simulate DB insert
      return appt;
    });

    // Act
    var result = service.create(dto, "user123");

    // Assert
    assertEquals("title", result.title);
  }
}
```

## References

- [ADR-2025-09-28-002: Quarkus 3](ADR-2025-09-28-002-quarkus.md)
- [ADR-2025-09-28-009: Panache for ORM](ADR-2025-09-28-009-panache.md)
- [ARCH-2025-09-28-003: Layered backend structure](../architecture/ARCH-2025-09-28-003-layered-backend.md)
- [PATTERN-2025-09-28-002: CDI event decoupling](../patterns/PATTERN-2025-09-28-002-cdi-events.md)
- [PATTERN-2025-09-28-005: Authorization checks in services](../patterns/PATTERN-2025-09-28-005-authorization.md)
- Backend code: `backend/src/main/java/de/chronos_live/chronos_date_api/`

## Related Entries

- [ADR-2025-09-28-003: DTO mapping pattern](../patterns/PATTERN-2025-09-28-003-dto-mapping.md)
