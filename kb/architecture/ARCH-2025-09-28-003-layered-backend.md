---
title: ARCH-2025-09-28-003: Layered backend structure
date: 2025-09-28
component: Backend
relates_to:
  - ADR-2025-09-28-002: Quarkus 3
  - ADR-2025-09-28-007: Layered architecture
  - ADR-2025-09-28-009: Panache for ORM
---

## Overview

The backend uses a **four-layer architecture** where requests flow downward through presentation → application → infrastructure → domain layers. Each layer has clear responsibilities and dependencies only flow downward.

## Architecture Diagram

```
┌─────────────────────────────────────┐
│  Presentation (JAX-RS Resources)   │
│  @Path /api/v2/appointments        │
│  - Deserialize HTTP                 │
│  - Validate input                   │
│  - Delegate to services            │
└─────────────────┬───────────────────┘
                  │ calls
┌─────────────────▼───────────────────┐
│  Application (Services)             │
│  AppointmentService                │
│  - Business logic                  │
│  - Authorization                   │
│  - Fire events                      │
│  - Transactions                    │
└─────────────────┬───────────────────┘
                  │ queries via
┌─────────────────▼───────────────────┐
│  Infrastructure (Repositories)     │
│  AppointmentRepository             │
│  - Database queries (ORM)          │
│  - External adapters               │
└─────────────────┬───────────────────┘
                  │ ORM maps to
┌─────────────────▼───────────────────┐
│  Domain (Entities)                 │
│  @Entity Appointment               │
│  - Data structure                  │
│  - Relationships                   │
└─────────────────┬───────────────────┘
                  │ persists to
┌─────────────────▼───────────────────┐
│  PostgreSQL                        │
└────────────────────────────────────┘
```

## Layer Responsibilities

### Presentation Layer

**Role**: HTTP entry point, request/response mapping

**Location**: `de.chronos_live.chronos_date_api.presentation`

**Key Components**:
- `AppointmentResource.java` — JAX-RS resource with `@GET`, `@POST`, `@PUT`, `@DELETE`
- `ParticipationResource.java` — separate resource for participation endpoints
- No business logic — only validate, deserialize, call service, return response

**Example**:
```java
@Path("/api/v2/appointments")
@ApplicationScoped
public class AppointmentResource {
  @Inject AppointmentService service;
  @Inject AppointmentMapper mapper;
  @Inject PrincipalContext principal;
  
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response list() {
    var appointments = service.listAll(principal.getSubject());
    return Response.ok(mapper.toDtos(appointments)).build();
  }
  
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public Response create(CreateAppointmentDto dto) {
    // Validate
    if (dto.title == null || dto.title.isEmpty()) {
      return Response.status(400).entity("Title required").build();
    }
    // Delegate
    var appointment = service.create(dto, principal.getSubject());
    // Return
    return Response.status(201).entity(mapper.toDto(appointment)).build();
  }
}
```

**Rules**:
- Never inject repositories directly (only services)
- Never query the database
- Return DTOs, not entities
- Use appropriate HTTP status codes

### Application Layer

**Role**: Business logic, authorization, events, transactions

**Location**: `de.chronos_live.chronos_date_api.application`

**Key Components**:
- `AppointmentService.java` — business logic for appointments
- `AuthorizationService.java` — permission checks
- `event/` — immutable event records (AppointmentCreatedEvent, etc.)

**Example**:
```java
@ApplicationScoped
public class AppointmentService {
  @Inject AppointmentRepository repo;
  @Inject AuthorizationService authz;
  @Inject Event<AppointmentCreatedEvent> events;
  @Inject Clock clock;
  
  @Transactional
  public Appointment create(CreateAppointmentDto dto, String userId) {
    // Authorize
    authz.checkCanCreateAppointment(userId, dto.groupId);
    
    // Create entity
    var appt = new Appointment();
    appt.title = dto.title;
    appt.organizerId = userId;
    appt.startTime = dto.startTime;
    appt.endTime = dto.endTime;
    
    // Persist
    repo.persist(appt);
    
    // Fire event (side effects happen async)
    events.fire(new AppointmentCreatedEvent(appt));
    
    return appt;
  }
  
  public List<Appointment> listByOrganizer(String organizerId) {
    return repo.findByOrganizer(organizerId);
  }
}
```

**Rules**:
- Business logic only (no HTTP concerns)
- Authorization checks before allowing action
- Fire events for side effects (push notifications, etc.)
- Use `@Transactional` for database modifications
- Never return DTOs (return entities, let mapper convert)

### Infrastructure Layer

**Role**: Data access, external service adapters, event observation

**Location**: `de.chronos_live.chronos_date_api.infrastructure`

**Key Components**:
- `AppointmentRepository.java` — ORM queries via Panache
- `ParticipationRepository.java` — queries for participations
- `WebPushAdapter.java` — Web Push integration
- `WebPushService.java` — observes events and sends notifications
- `AppointmentReminderService.java` — observes events and schedules reminders

**Example**:
```java
@ApplicationScoped
public class AppointmentRepository implements PanacheRepository<Appointment> {
  // Panache provides: findById, list, count, persist, delete
  
  public List<Appointment> findByOrganizer(String organizerId) {
    return list("organizerId", organizerId);
  }
  
  public List<Appointment> findUpcoming(LocalDateTime now) {
    return list("startTime > ?1", now);
  }
  
  public List<Appointment> search(String titleSearch) {
    return list("title ILIKE ?1", "%" + titleSearch + "%");
  }
}

@ApplicationScoped
public class WebPushService {
  @Inject WebPushAdapter pushAdapter;
  @Inject UserRepository userRepo;
  
  void onAppointmentCreated(@Observes AppointmentCreatedEvent evt) {
    // Send notification to all participants
    for (var participation : evt.appointment().participations) {
      var user = userRepo.findById(participation.userId);
      pushAdapter.send(user.pushSubscription, 
        "New appointment: " + evt.appointment().title);
    }
  }
}
```

**Rules**:
- Query logic only (no business logic)
- Return entities (not DTOs)
- Can observe events (`@Observes`) for side effects
- External service adapters here (webhooks, email, etc.)

### Domain Layer

**Role**: Data model, entity definitions, validation

**Location**: `de.chronos_live.chronos_date_api.domain`

**Key Components**:
- `Appointment.java` — Appointment entity
- `Participation.java` — Participation entity
- `User.java` — User entity
- Relationships between entities

**Example**:
```java
@Entity
public class Appointment extends PanacheEntity {
  @NotNull
  public String title;
  
  public String description;
  
  @NotNull
  public LocalDateTime startTime;
  
  @NotNull
  public LocalDateTime endTime;
  
  @NotNull
  public String organizerId;
  
  @OneToMany(mappedBy = "appointment", cascade = CascadeType.ALL)
  public List<Participation> participations = new ArrayList<>();
  
  @CreationTimestamp
  public LocalDateTime createdAt;
  
  @UpdateTimestamp
  public LocalDateTime updatedAt;
}

@Entity
public class Participation extends PanacheEntity {
  @NotNull
  @ManyToOne
  public Appointment appointment;
  
  @NotNull
  public String userId;
  
  @Enumerated(EnumType.STRING)
  public ParticipationStatus status = ParticipationStatus.PENDING;
  
  @CreationTimestamp
  public LocalDateTime createdAt;
}
```

**Rules**:
- Only entity definitions (no methods, no queries)
- Validation via annotations (`@NotNull`, etc.)
- Relationships defined clearly
- Never contains any business logic

## Data Flow: Create Appointment Example

```
1. Client
   POST /api/v2/appointments
   {"title": "Team Meeting", "startTime": "2025-09-29T14:00:00"}

2. Presentation (AppointmentResource)
   - Deserialize JSON to CreateAppointmentDto
   - Call service.create(dto, principal.getSubject())

3. Application (AppointmentService)
   - Check authorization: authz.checkCanCreateAppointment(userId, groupId)
   - Create entity: new Appointment() + set fields
   - Persist: repo.persist(appt)
   - Fire event: events.fire(new AppointmentCreatedEvent(appt))
   - Return appointment entity

4. Infrastructure (AppointmentRepository + WebPushService)
   - Repository persists to PostgreSQL
   - WebPushService observes AppointmentCreatedEvent
   - Queries participating users
   - Sends push notifications

5. Domain (Database)
   - PostgreSQL saves appointment row

6. Presentation (Response)
   - Map entity to DTO: mapper.toDto(appointment)
   - Return 201 with appointment JSON

7. Client
   Receives appointment with ID
```

## Testing Strategy

### Unit Test (Mock Repositories)

```java
@Test
void testCreateAppointment() {
  // Mock repository
  var mockRepo = mock(AppointmentRepository.class);
  var service = new AppointmentService(mockRepo, mockAuthz, mockEvents);
  
  // Call service
  var dto = new CreateAppointmentDto("Meeting", ...);
  var result = service.create(dto, "user123");
  
  // Verify service logic (not DB)
  assertEquals("Meeting", result.title);
  verify(mockRepo).persist(any());
  verify(mockAuthz).checkCanCreateAppointment("user123", ...);
}
```

### Integration Test (Real Database)

```java
@QuarkusTest
public class AppointmentServiceIntegrationTest {
  @Inject AppointmentService service;
  @Inject AppointmentRepository repo;
  
  @Test
  void testCreateAndFetch() {
    // Create (uses real DB via Panache)
    var dto = new CreateAppointmentDto("Meeting", ...);
    var created = service.create(dto, "user123");
    
    // Fetch (uses real DB)
    var fetched = repo.findById(created.id);
    
    // Verify
    assertEquals("Meeting", fetched.title);
  }
}
```

## References

- [ADR-2025-09-28-007: Layered architecture for backend](../adr/ADR-2025-09-28-007-layered-backend.md)
- [ADR-2025-09-28-009: Panache for ORM](../adr/ADR-2025-09-28-009-panache.md)
- [PATTERN-2025-09-28-002: CDI event decoupling](../patterns/PATTERN-2025-09-28-002-cdi-events.md)
- [PATTERN-2025-09-28-005: Authorization checks in services](../patterns/PATTERN-2025-09-28-005-authorization.md)
- Backend code: `backend/src/main/java/de/chronos_live/chronos_date_api/`

---

**Last updated**: 2025-09-28
