---
name: Service Layer Pattern
description: Stateless services encapsulate business logic and coordinate between layers
category: architecture
last-updated: 2026-09-27
---

# Service Layer Pattern

## Decision
All business logic lives in stateless services (`@ApplicationScoped` beans in the `application/` package). Services coordinate between the presentation layer (resources) and the domain/infrastructure layers (entities and repositories).

## Service Responsibilities

1. **Business Logic**: Implement use-cases and workflows
2. **Validation**: Check business invariants before persistence
3. **Coordination**: Orchestrate repository calls and side-effects
4. **Event Publishing**: Fire CDI events for side-effects
5. **Authorization**: Delegate to `AuthorizationService` for access control

## Service Anatomy

```java
@ApplicationScoped
public class AppointmentService {
    @Inject
    AppointmentRepository appointmentRepository;
    
    @Inject
    Event<AppointmentCreatedEvent> appointmentCreated;
    
    @Inject
    PrincipalContext principalContext;
    
    @Transactional
    public Long createAppointment(CreateAppointmentRequest req) {
        // Validate input
        if (req.startTime().isBefore(Instant.now())) {
            throw new InvalidAppointmentException("Cannot create past appointments");
        }
        
        // Create entity
        Appointment appointment = new Appointment(
            req.title(),
            req.description(),
            req.startTime(),
            principalContext.getUserId()
        );
        
        // Persist
        Long id = appointmentRepository.persist(appointment).id;
        
        // Fire event
        appointmentCreated.fire(new AppointmentCreatedEvent(
            id,
            appointment.title(),
            appointment.startTime(),
            List.of()
        ));
        
        return id;
    }
}
```

## Scope

Services are always `@ApplicationScoped` and stateless. They must:
- Be thread-safe (no instance fields except injected dependencies)
- Be idempotent (same input always produces same output)
- Not hold references to requests or sessions
- Be testable without HTTP context

## Transaction Boundaries

- Methods performing writes are marked `@Transactional`
- Transactions are demarcated at the service layer, not in resources or repositories
- Resource layer calls service methods within transaction
- Events are fired AFTER transaction commits (handled by Quarkus/CDI)

## Testing Services

Services can be tested without CDI or HTTP:
```java
@Test
public void testCreateAppointment() {
    AppointmentService service = new AppointmentService();
    service.appointmentRepository = mock(AppointmentRepository.class);
    service.appointmentCreated = mock(Event.class);
    service.principalContext = mock(PrincipalContext.class);
    
    // Arrange, Act, Assert...
}
```

## Common Patterns

- **Repository Injection**: Use `@Inject` for repositories
- **Event Publishing**: Use `@Inject Event<EventType>` for side-effects
- **User Context**: Always check `principalContext.getUserId()` for authorization
- **Exception Handling**: Throw domain exceptions; let `ExceptionMapper` handle HTTP
- **No Return of Entities**: Always map to DTOs before returning from resources
