---
name: Entity Validation Pattern
description: Validate business invariants in services before persistence; use Bean Validation in DTOs
category: patterns
last-updated: 2026-09-27
---

# Entity Validation Pattern

## Pattern
- **DTO validation**: Use Jakarta Bean Validation (`@NotNull`, `@Size`, etc.) on request DTOs
- **Business validation**: Check invariants in services (domain logic)
- **Entity invariants**: Enforce at entity level via constructors; never allow invalid state

## DTO-Level Validation

Use `@Valid` in resource methods and Bean Validation annotations on DTOs:

```java
public record CreateAppointmentRequest(
    @NotBlank(message = "Title cannot be empty")
    String title,
    
    @NotNull(message = "Start time required")
    Instant startTime,
    
    @Size(min = 1, max = 100, message = "Invite 1-100 people")
    List<String> invitees
) {}

@POST
@Path("/appointments")
public Long createAppointment(@Valid CreateAppointmentRequest req) {
    // If we reach here, DTO is valid
    return service.createAppointment(req);
}
```

## Business Validation

Check domain rules in services:

```java
@Transactional
public Long createAppointment(CreateAppointmentRequest req) {
    // DTO is valid syntax; check business logic
    if (req.startTime().isBefore(Instant.now())) {
        throw new BusinessException("Cannot schedule past appointments");
    }
    
    if (req.invitees().contains(principalContext.getUserId())) {
        throw new BusinessException("Cannot invite yourself");
    }
    
    // Now create entity
    Appointment appointment = new Appointment(req);
    return appointmentRepository.persist(appointment).id;
}
```

## Entity-Level Invariants

Entities enforce invariants through constructors, never allowing invalid state:

```java
@Entity
public class Appointment {
    @Id
    @GeneratedValue
    public Long id;
    
    @NotBlank
    @Column(nullable = false)
    public String title;
    
    @Column(nullable = false)
    public Instant startTime;
    
    @ManyToOne
    @JoinColumn(nullable = false)
    public User organizer;
    
    // Constructor enforces invariants
    public Appointment(String title, Instant startTime, User organizer) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title required");
        }
        if (startTime == null || startTime.isBefore(Instant.now())) {
            throw new IllegalArgumentException("Future date required");
        }
        if (organizer == null) {
            throw new IllegalArgumentException("Organizer required");
        }
        
        this.title = title;
        this.startTime = startTime;
        this.organizer = organizer;
    }
}
```

## Validation Layers

| Layer | Type | Tool | Example |
|-------|------|------|---------|
| **DTO** | Syntax | Bean Validation | `@NotNull`, `@Size`, `@Email` |
| **Service** | Business Logic | Custom code | "Cannot schedule in the past" |
| **Entity** | Invariants | Constructor | Entities never allow invalid state |

## Error Handling

Throw domain-specific exceptions; let `ExceptionMapper` handle HTTP:

```java
// Custom exception
public class InvalidAppointmentException extends RuntimeException {
    public InvalidAppointmentException(String message) {
        super(message);
    }
}

// Mapper handles conversion to HTTP response
@Provider
public class InvalidAppointmentExceptionMapper 
    implements ExceptionMapper<InvalidAppointmentException> {
    
    @Override
    public Response toResponse(InvalidAppointmentException e) {
        return Response.status(400).entity(new ErrorDto(e.getMessage())).build();
    }
}
```

## Rule Summary

1. **Don't validate in entities**: Trust constructors to enforce invariants
2. **Don't skip DTO validation**: Always use `@Valid` on resources
3. **Validate business rules in services**: This is where domain logic lives
4. **Throw domain exceptions**: Let mappers handle HTTP status codes
5. **Catch validation errors in resources**: Handle `ConstraintViolationException` if needed
