---
name: Error Handling Pattern
description: Use custom exception hierarchy and ExceptionMappers for consistent error responses
category: patterns
last-updated: 2026-09-27
---

# Error Handling Pattern

## Pattern
Define a custom exception hierarchy for domain errors. Let JAX-RS `ExceptionMapper` implementations convert exceptions to standardized HTTP responses.

## Exception Hierarchy

```
RuntimeException (or Throwable)
├── ApplicationException (base for domain errors)
│   ├── EntityNotFoundException (404)
│   ├── InvalidAppointmentException (400)
│   ├── UnauthorizedAccessException (403)
│   ├── ConflictException (409)
│   └── ValidationException (422)
└── SystemException (infrastructure errors)
    ├── DatabaseException (500)
    └── ExternalServiceException (503)
```

## Exception Definition

```java
public abstract class ApplicationException extends RuntimeException {
    private final int httpStatus;
    
    public ApplicationException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }
    
    public int getHttpStatus() {
        return httpStatus;
    }
}

public class EntityNotFoundException extends ApplicationException {
    public EntityNotFoundException(String entity, Long id) {
        super(entity + " with ID " + id + " not found", 404);
    }
}

public class InvalidAppointmentException extends ApplicationException {
    public InvalidAppointmentException(String reason) {
        super("Invalid appointment: " + reason, 400);
    }
}
```

## Exception Mappers

Implement `ExceptionMapper<T>` for each exception type:

```java
@Provider
public class ApplicationExceptionMapper 
    implements ExceptionMapper<ApplicationException> {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationExceptionMapper.class);
    
    @Override
    public Response toResponse(ApplicationException e) {
        LOGGER.warn("Application error: {}", e.getMessage());
        
        ErrorResponse error = new ErrorResponse(
            e.getHttpStatus(),
            e.getMessage(),
            e.getClass().getSimpleName()
        );
        
        return Response
            .status(e.getHttpStatus())
            .entity(error)
            .build();
    }
}

@Provider
public class ConstraintViolationExceptionMapper 
    implements ExceptionMapper<ConstraintViolationException> {
    
    @Override
    public Response toResponse(ConstraintViolationException e) {
        List<String> violations = e.getConstraintViolations().stream()
            .map(v -> v.getPropertyPath() + ": " + v.getMessage())
            .collect(Collectors.toList());
        
        ErrorResponse error = new ErrorResponse(
            400,
            "Validation failed",
            violations
        );
        
        return Response.status(400).entity(error).build();
    }
}

@Provider
public class GenericExceptionMapper 
    implements ExceptionMapper<Exception> {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(GenericExceptionMapper.class);
    
    @Override
    public Response toResponse(Exception e) {
        LOGGER.error("Unexpected error", e);
        
        ErrorResponse error = new ErrorResponse(
            500,
            "Internal server error",
            "An unexpected error occurred"
        );
        
        return Response.status(500).entity(error).build();
    }
}
```

## Error Response Format

```java
public record ErrorResponse(
    int status,
    String message,
    Object details,
    Instant timestamp
) {
    public ErrorResponse(int status, String message, Object details) {
        this(status, message, details, Instant.now());
    }
}
```

## Usage in Services

```java
@Transactional
public AppointmentDto getAppointment(Long id) {
    Appointment appointment = appointmentRepository.findByIdOptional(id)
        .orElseThrow(() -> new EntityNotFoundException("Appointment", id));
    
    if (!appointment.isOrganizedBy(principalContext.getUserId())) {
        throw new UnauthorizedAccessException("Cannot view this appointment");
    }
    
    return mapper.toDto(appointment);
}
```

## Rules

1. **Throw, don't log**: Throw exceptions; let mappers log and return HTTP
2. **No exception swallowing**: Always propagate or throw a domain exception
3. **Status codes**: Map exceptions to correct HTTP status codes
4. **Message clarity**: Error messages should explain the problem to clients
5. **Logging**: Log at WARN (expected errors) or ERROR (unexpected)
6. **No stack traces to client**: Only internal logs contain stack traces
7. **Mapper ordering**: More specific mappers before generic ones

## HTTP Status Mapping

| Exception | HTTP Status | Meaning |
|-----------|-------------|---------|
| `EntityNotFoundException` | 404 | Resource not found |
| `InvalidAppointmentException` | 400 | Bad request (client error) |
| `UnauthorizedAccessException` | 403 | Forbidden (auth error) |
| `ConflictException` | 409 | Conflict (state error) |
| `ValidationException` | 422 | Unprocessable entity |
| `Exception` (generic) | 500 | Internal server error |
