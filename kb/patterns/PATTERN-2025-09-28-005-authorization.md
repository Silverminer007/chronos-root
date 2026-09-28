---
title: PATTERN-2025-09-28-005: Authorization checks in services
date: 2025-09-28
language: Java
applies_to:
  - Backend service layer
relates_to:
  - ADR-2025-09-28-007: Layered architecture
  - PATTERN-2025-09-28-001: Principal context pattern
---

## Intent

Centralize authorization (permission) checks in services. A user can only perform actions they're allowed to (e.g., edit own appointments, accept invitations). Never put authorization logic in resources or repositories.

## Motivation

Authorization is a **cross-cutting concern** — every modification needs permission checks. Placing authorization in the right layer is critical:

```java
// ❌ DON'T: Authorization in resource
@PUT
@Path("/{appointmentId}")
public Response update(@PathParam("appointmentId") Long id, UpdateAppointmentDto dto) {
  // Who checks if user can edit this appointment?
  var appointment = appointmentService.getById(id);
  if (!appointment.organizerId.equals(principal.getSubject())) {
    return Response.status(403).build();  // WRONG PLACE
  }
  // ...
}

// ❌ DON'T: Authorization in repository
public Appointment findByIdForUser(Long id, String userId) {
  return find("id = ?1 AND organizerId = ?2", id, userId).firstResult();  // Query-based authz
}

// ✓ DO: Authorization in service
@ApplicationScoped
public class AppointmentService {
  @Inject AuthorizationService authz;
  
  public Appointment update(Long id, UpdateAppointmentDto dto, String userId) {
    // Check permission FIRST
    authz.checkCanEditAppointment(userId, id);
    
    // Then do work
    var appointment = appointmentRepository.findById(id);
    appointment.title = dto.title;
    appointment.persist();
    
    return appointment;
  }
}
```

Benefits of service-level authorization:
- Single place where permission logic lives
- Catches bugs (all entry points checked)
- Easy to audit (permissions in one place)
- Works for all callers (API, scheduled jobs, etc.)

## Principles

### 1. Fail Fast

Check permissions **before** doing work:

```java
public void delete(Long appointmentId, String userId) {
  // Check FIRST (might throw exception)
  authz.checkCanDeleteAppointment(userId, appointmentId);
  
  // Then do work
  var appt = appointmentRepository.findById(appointmentId);
  appt.delete();
  events.fire(new AppointmentDeletedEvent(appointmentId));
}
```

### 2. Explicit Permissions

Each action needs explicit permission check:

```java
public Appointment update(Long id, UpdateAppointmentDto dto, String userId) {
  // ✓ Explicit: we're about to edit this appointment
  authz.checkCanEditAppointment(userId, id);
  // ...
}

public void rsvp(Long appointmentId, ParticipationStatus status, String userId) {
  // ✓ Explicit: user is RSVPing to this appointment
  authz.checkCanRsvp(userId, appointmentId);
  // ...
}
```

### 3. Principle of Least Privilege

Only grant permissions actually needed:

```java
// ORGANIZER can edit appointment
// PARTICIPANT cannot edit (only organizer can)
// DECLINED participant can re-invite? NO (explicit permission)
authz.checkCanEditAppointment(userId, appointmentId);  // Only organizer
```

## Permission Types in Chronos

| Permission | Who Has | Example |
|------------|---------|---------|
| `CAN_CREATE_APPOINTMENT` | Any authenticated user | Create own appointment |
| `CAN_EDIT_APPOINTMENT` | Organizer only | Edit title/time |
| `CAN_DELETE_APPOINTMENT` | Organizer only | Cancel appointment |
| `CAN_INVITE_TO_APPOINTMENT` | Organizer only | Add participants |
| `CAN_RSVP_APPOINTMENT` | Invited participant | Accept/decline invitation |
| `CAN_VIEW_APPOINTMENT` | Organizer + participants | See appointment details |

## Implementation

### AuthorizationService

Central service with all permission checks:

```java
@ApplicationScoped
public class AuthorizationService {
  @Inject AppointmentRepository appointmentRepository;
  @Inject ParticipationRepository participationRepository;
  
  public void checkCanEditAppointment(String userId, Long appointmentId) {
    var appointment = appointmentRepository.findById(appointmentId);
    if (appointment == null) {
      throw new NotFoundException("Appointment not found");
    }
    if (!appointment.organizerId.equals(userId)) {
      throw new ForbiddenException("Only organizer can edit this appointment");
    }
  }
  
  public void checkCanDeleteAppointment(String userId, Long appointmentId) {
    var appointment = appointmentRepository.findById(appointmentId);
    if (appointment == null) {
      throw new NotFoundException("Appointment not found");
    }
    if (!appointment.organizerId.equals(userId)) {
      throw new ForbiddenException("Only organizer can delete this appointment");
    }
  }
  
  public void checkCanRsvp(String userId, Long appointmentId) {
    var appointment = appointmentRepository.findById(appointmentId);
    if (appointment == null) {
      throw new NotFoundException("Appointment not found");
    }
    
    var participation = participationRepository.find(
      "appointmentId = ?1 AND userId = ?2", appointmentId, userId
    ).firstResult();
    
    if (participation == null) {
      throw new ForbiddenException("You're not invited to this appointment");
    }
  }
  
  public void checkCanViewAppointment(String userId, Long appointmentId) {
    var appointment = appointmentRepository.findById(appointmentId);
    if (appointment == null) {
      throw new NotFoundException("Appointment not found");
    }
    
    // Can view if organizer or participant
    if (appointment.organizerId.equals(userId)) {
      return;  // Is organizer
    }
    
    var participation = participationRepository.find(
      "appointmentId = ?1 AND userId = ?2", appointmentId, userId
    ).firstResult();
    
    if (participation == null) {
      throw new ForbiddenException("You don't have access to this appointment");
    }
  }
}
```

### Usage in Services

```java
@ApplicationScoped
public class AppointmentService {
  @Inject AppointmentRepository appointmentRepository;
  @Inject AuthorizationService authz;
  @Inject Event<AppointmentCreatedEvent> events;
  
  public Appointment create(CreateAppointmentDto dto, String userId) {
    // No explicit check needed — user creating is the organizer by definition
    var appt = new Appointment();
    appt.title = dto.title;
    appt.organizerId = userId;  // Current user is organizer
    appt.persist();
    
    events.fire(new AppointmentCreatedEvent(appt));
    return appt;
  }
  
  @Transactional
  public Appointment update(Long id, UpdateAppointmentDto dto, String userId) {
    // ✓ Check permission first
    authz.checkCanEditAppointment(userId, id);
    
    // Then do work
    var appointment = appointmentRepository.findById(id);
    appointment.title = dto.title;
    appointment.startTime = dto.startTime;
    appointment.endTime = dto.endTime;
    appointment.persist();
    
    events.fire(new AppointmentUpdatedEvent(appointment));
    return appointment;
  }
  
  @Transactional
  public void delete(Long id, String userId) {
    // ✓ Check permission first
    authz.checkCanDeleteAppointment(userId, id);
    
    // Then do work
    var appointment = appointmentRepository.findById(id);
    appointment.delete();
    
    events.fire(new AppointmentDeletedEvent(id));
  }
}
```

### Usage in Resources

Resources simply call services (no auth logic in resource):

```java
@Path("/api/v2/appointments")
@ApplicationScoped
public class AppointmentResource {
  @Inject AppointmentService service;
  @Inject PrincipalContext principal;
  
  @PUT
  @Path("/{appointmentId}")
  public Response update(@PathParam("appointmentId") Long id, UpdateAppointmentDto dto) {
    // Service handles authorization (throws exception if not allowed)
    var appointment = service.update(id, dto, principal.getSubject());
    return Response.ok(appointment).build();
  }
}
```

## Exception Handling

When permission check fails, throw appropriate exceptions:

```java
// ForbiddenException → HTTP 403
// NotFoundException → HTTP 404
// UnauthorizedException → HTTP 401 (shouldn't happen in services, caught at filter level)

if (!appointment.organizerId.equals(userId)) {
  throw new ForbiddenException("Only organizer can edit");  // HTTP 403
}

if (appointment == null) {
  throw new NotFoundException("Appointment not found");  // HTTP 404
}
```

Exception mapper converts to HTTP responses (in `exception/` package):

```java
@Provider
public class ForbiddenExceptionMapper implements ExceptionMapper<ForbiddenException> {
  @Override
  public Response toResponse(ForbiddenException exception) {
    return Response.status(403)
      .entity(new ErrorResponse(exception.getMessage()))
      .build();
  }
}
```

## Testing

### Unit Test

```java
@Test
void testCanOnlyEditOwnAppointment() {
  var mockRepo = mock(AppointmentRepository.class);
  var authz = new AuthorizationService(mockRepo);
  
  var appointment = new Appointment();
  appointment.organizerId = "user123";
  
  when(mockRepo.findById(1L)).thenReturn(appointment);
  
  // Should succeed
  authz.checkCanEditAppointment("user123", 1L);
  
  // Should fail
  assertThrows(ForbiddenException.class, () ->
    authz.checkCanEditAppointment("user456", 1L)
  );
}
```

### Integration Test

```java
@QuarkusTest
public class AppointmentAuthorizationTest {
  @Inject AppointmentService service;
  
  @Test
  void testCannotEditOthersAppointment() {
    // Create appointment as user1
    var dto = new CreateAppointmentDto("Meeting", ...);
    var appt = service.create(dto, "user1");
    
    // Try to edit as user2 (should fail)
    var updateDto = new UpdateAppointmentDto("New title");
    assertThrows(ForbiddenException.class, () ->
      service.update(appt.id, updateDto, "user2")
    );
  }
}
```

## References

- [ADR-2025-09-28-007: Layered architecture](../adr/ADR-2025-09-28-007-layered-backend.md)
- [PATTERN-2025-09-28-001: Principal context pattern](PATTERN-2025-09-28-001-principal-context.md)
- Backend code: `backend/src/main/java/de/chronos_live/chronos_date_api/application/AuthorizationService.java`

---

**Last updated**: 2025-09-28
