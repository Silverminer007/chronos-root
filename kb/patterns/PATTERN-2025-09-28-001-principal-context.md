---
title: PATTERN-2025-09-28-001: Principal context pattern
date: 2025-09-28
language: Java
applies_to:
  - Backend across all layers
relates_to:
  - ADR-2025-09-28-003: Keycloak OIDC
  - C2025-09-28-002: OIDC required
---

## Intent

Provide access to the current user (OIDC subject) throughout a request's lifecycle without passing it through every method parameter.

## Motivation

Every request needs to know "who is making this request?" We could pass it as a parameter through all layers:

```java
// ❌ DON'T: Pass userId everywhere
@GET
public Response getAppointments(String userId) {  // Where did userId come from?
  var result = appointmentService.listForUser(userId);
  return Response.ok(result).build();
}

// Service also needs userId
public List<Appointment> listForUser(String userId) {
  return appointmentRepository.findByOrganizer(userId);
}

// Repository also needs userId
public List<Appointment> findByOrganizer(String userId) {
  return list("organizerId", userId);
}
```

This is boilerplate and error-prone. Instead, use **PrincipalContext** — a request-scoped bean that holds the current user:

```java
// ✓ DO: Inject PrincipalContext
@GET
public Response getAppointments() {
  var userId = principal.getSubject();  // Current user
  var result = appointmentService.listForUser(userId);
  return Response.ok(result).build();
}
```

## Structure

### Request Scoped Bean

```java
@RequestScoped
public class PrincipalContext {
  private String subject;  // OIDC subject (user ID)
  
  public String getSubject() {
    return subject;
  }
  
  public void setSubject(String subject) {
    this.subject = subject;
  }
}
```

### Filter (Extract from JWT)

On every request, extract the OIDC subject from the JWT and store it:

```java
@Provider
@Priority(Priorities.AUTHENTICATION)
public class PrincipalContextFilter implements ContainerRequestFilter {
  @Inject PrincipalContext principal;
  
  @Override
  public void filter(ContainerRequestContext context) {
    var authHeader = context.getHeaderString("Authorization");
    
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      var token = authHeader.substring(7);
      
      // Validate token and extract subject
      var claims = validateJwtAndGetClaims(token);
      var subject = (String) claims.get("sub");
      
      // Store in request-scoped bean
      principal.setSubject(subject);
    }
  }
  
  private Map<String, Object> validateJwtAndGetClaims(String token) {
    // Validate JWT signature against Keycloak's public key
    // Return claims
  }
}
```

### Usage

Now, any service/repository can inject `PrincipalContext` to get the current user:

```java
@ApplicationScoped
public class AppointmentService {
  @Inject PrincipalContext principal;
  @Inject AppointmentRepository repo;
  
  public List<Appointment> listMyAppointments() {
    var userId = principal.getSubject();  // Get current user
    return repo.findByOrganizer(userId);
  }
}

@ApplicationScoped
public class ParticipationService {
  @Inject PrincipalContext principal;
  @Inject AuthorizationService authz;
  
  public void rsvp(Long appointmentId, ParticipationStatus status) {
    var userId = principal.getSubject();  // Get current user
    authz.checkCanRsvp(userId, appointmentId);
    // ... do work
  }
}
```

### In Resources

```java
@Path("/api/v2/appointments")
public class AppointmentResource {
  @Inject AppointmentService service;
  @Inject PrincipalContext principal;
  
  @GET
  public Response list() {
    // Service knows who the user is (via PrincipalContext)
    var appointments = service.listMyAppointments();
    return Response.ok(appointments).build();
  }
  
  @POST
  public Response create(CreateAppointmentDto dto) {
    var appointment = service.create(dto);  // Service gets userId from PrincipalContext
    return Response.ok(appointment).build();
  }
}
```

## Key Properties

### Request-Scoped
Each request gets a fresh `PrincipalContext` instance. Requests are isolated (user A's context doesn't leak to user B).

### Injected
Instead of manually passing userId, just inject `PrincipalContext` and call `getSubject()`.

### Validated
The JWT is validated when set in the filter. By the time code runs, we know the subject is legitimate.

### Always Present
If a request reaches your service layer, it's been authenticated (filter checked it).

## Testing

### Unit Test

```java
@Test
void testServiceUsesCurrentUser() {
  // Create a mock PrincipalContext
  var principal = mock(PrincipalContext.class);
  when(principal.getSubject()).thenReturn("user123");
  
  // Service injects mock
  var repo = mock(AppointmentRepository.class);
  var service = new AppointmentService(principal, repo);
  
  service.listMyAppointments();
  
  // Verify service queried for current user
  verify(repo).findByOrganizer("user123");
}
```

### Integration Test

```java
@QuarkusTest
public class AppointmentResourceIntegrationTest {
  @Inject AppointmentService service;
  @Inject PrincipalContext principal;
  
  @Test
  void testResourceHasCurrentUser() {
    // PrincipalContext is already set by the filter
    var userId = principal.getSubject();
    
    // Service should use this user
    var appointments = service.listMyAppointments();
    
    // All should be mine
    for (var appt : appointments) {
      assertEquals(userId, appt.organizerId);
    }
  }
}
```

## Error Cases

### No Token

If no `Authorization` header is present, the filter should not set a subject:

```java
// In filter
if (authHeader == null || !authHeader.startsWith("Bearer ")) {
  principal.setSubject(null);
  // Let the service handle missing auth
}
```

Service should check for null:

```java
public List<Appointment> listMyAppointments() {
  var userId = principal.getSubject();
  if (userId == null) {
    throw new UnauthorizedException("Not authenticated");
  }
  return repo.findByOrganizer(userId);
}
```

### Invalid Token

If JWT validation fails, throw an exception:

```java
// In filter
try {
  var claims = validateJwtAndGetClaims(token);
  principal.setSubject((String) claims.get("sub"));
} catch (SignatureException e) {
  context.abortWith(
    Response.status(401).entity("Invalid token").build()
  );
}
```

## Alternative Approaches

### Static ThreadLocal
```java
// ❌ DON'T: ThreadLocal makes testing hard
public class PrincipalHolder {
  private static ThreadLocal<String> holder = new ThreadLocal<>();
  
  public static void setSubject(String s) { holder.set(s); }
  public static String getSubject() { return holder.get(); }
}
```

Problems: Hard to test, must remember to clear, not thread-safe in thread pools.

### SecurityContext (JAX-RS)
```java
// JAX-RS provides SecurityContext
@GET
public Response list(@Context SecurityContext securityContext) {
  var userId = securityContext.getUserPrincipal().getName();
  // ...
}
```

Works, but requires passing as parameter. PrincipalContext is cleaner.

## References

- [Quarkus Security](https://quarkus.io/guides/security)
- [ADR-2025-09-28-003: Keycloak OIDC](../adr/ADR-2025-09-28-003-keycloak.md)
- [PATTERN-2025-09-28-005: Authorization checks in services](PATTERN-2025-09-28-005-authorization.md)
- Backend code: `backend/src/main/java/de/chronos_live/chronos_date_api/security/PrincipalContext.java`

---

**Last updated**: 2025-09-28
