---
title: PATTERN-2025-09-28-002: CDI event decoupling
date: 2025-09-28
language: Java
applies_to:
  - Backend service layer
relates_to:
  - ADR-2025-09-28-002: Quarkus 3
  - ADR-2025-09-28-007: Layered architecture
---

## Intent

Decouple side effects (push notifications, emails, reminders) from business logic. When an appointment is created, we need to send notifications, schedule reminders, update analytics — but the service creating the appointment shouldn't need to know about all these concerns.

## Motivation

Without decoupling, services become god objects:

```java
// ❌ DON'T: Tightly coupled
@ApplicationScoped
public class AppointmentService {
  @Inject WebPushService pushService;
  @Inject ReminderScheduler reminderScheduler;
  @Inject AnalyticsService analyticsService;
  @Inject EmailService emailService;
  
  public Appointment create(CreateAppointmentDto dto, String userId) {
    var appt = new Appointment(...);
    appt.persist();
    
    // Service knows about ALL side effects
    pushService.notifyParticipants(appt);
    reminderScheduler.scheduleReminders(appt);
    analyticsService.recordAppointmentCreated(appt);
    emailService.sendConfirmation(appt);
    
    return appt;
  }
}
```

Problems:
- Service is responsible for too many things
- Adding a new notification type requires changing service
- Testing service requires mocking all dependencies
- Performance: all side effects run synchronously

With CDI events, side effects are **observed** separately:

```java
// ✓ DO: Loosely coupled via events
@ApplicationScoped
public class AppointmentService {
  @Inject Event<AppointmentCreatedEvent> events;
  
  public Appointment create(CreateAppointmentDto dto, String userId) {
    var appt = new Appointment(...);
    appt.persist();
    
    // Fire event, let observers handle side effects
    events.fire(new AppointmentCreatedEvent(appt));
    
    return appt;
  }
}

// Observers (in different services)
@ApplicationScoped
public class WebPushService {
  void onAppointmentCreated(@Observes AppointmentCreatedEvent evt) {
    notifyParticipants(evt.appointment());
  }
}

@ApplicationScoped
public class ReminderService {
  void onAppointmentCreated(@Observes AppointmentCreatedEvent evt) {
    scheduleReminders(evt.appointment());
  }
}

@ApplicationScoped
public class AnalyticsService {
  void onAppointmentCreated(@Observes AppointmentCreatedEvent evt) {
    recordAppointmentCreated(evt.appointment());
  }
}
```

Benefits:
- Service has single responsibility (create appointment)
- Easy to add new observers (no changes to service)
- Easy to test (no mocks needed, fire event directly)
- Observers can run asynchronously (future optimization)

## Structure

### Event (Immutable Record)

Events are **immutable records** containing data about what happened:

```java
// app/events/AppointmentCreatedEvent.java
public record AppointmentCreatedEvent(Appointment appointment) {}

public record ParticipationUpdatedEvent(Participation participation) {}

public record AppointmentDeletedEvent(Long appointmentId) {}

public record ReminderTriggeredEvent(Reminder reminder) {}
```

**Rules**:
- Events are records (immutable)
- Contain only the data needed by observers
- Named in past tense (event already happened)
- Placed in `application/events/` package

### Publisher (Service)

Service fires event after doing its work:

```java
// application/AppointmentService.java
@ApplicationScoped
public class AppointmentService {
  @Inject Event<AppointmentCreatedEvent> appointmentCreatedEvent;
  @Inject Event<AppointmentDeletedEvent> appointmentDeletedEvent;
  
  public Appointment create(CreateAppointmentDto dto, String userId) {
    // Do work
    var appt = new Appointment(...);
    appt.persist();
    
    // Fire event (observers will be notified)
    appointmentCreatedEvent.fire(new AppointmentCreatedEvent(appt));
    
    return appt;
  }
  
  @Transactional
  public void delete(Long appointmentId, String userId) {
    var appt = Appointment.findById(appointmentId);
    authz.checkCanDelete(userId, appt);
    appt.delete();
    
    appointmentDeletedEvent.fire(new AppointmentDeletedEvent(appointmentId));
  }
}
```

**Rules**:
- Inject `Event<YourEventType>` (specific type)
- Fire event after transaction commits (if using `@Transactional`)
- Never catch exceptions from event.fire() (observers should be robust)

### Observer (Infrastructure Service)

Infrastructure services observe events and handle side effects:

```java
// infrastructure/WebPushService.java
@ApplicationScoped
public class WebPushService {
  @Inject WebPushAdapter adapter;
  @Inject ParticipationRepository participationRepo;
  @Inject UserRepository userRepo;
  
  void onAppointmentCreated(@Observes AppointmentCreatedEvent evt) {
    var appt = evt.appointment();
    
    // Send push to all participants
    for (var participation : appt.participations) {
      var user = userRepo.findById(participation.userId);
      if (user.pushSubscription != null) {
        adapter.send(user.pushSubscription,
          "New appointment: " + appt.title);
      }
    }
  }
  
  void onParticipationUpdated(@Observes ParticipationUpdatedEvent evt) {
    var participation = evt.participation();
    
    // Notify organizer of RSVP
    var appt = participation.appointment;
    var organizer = userRepo.findById(appt.organizerId);
    
    var status = participation.status.equals("ACCEPTED") ? "accepted" : "declined";
    adapter.send(organizer.pushSubscription,
      "Participant " + status + " your invitation");
  }
}

// infrastructure/ReminderService.java
@ApplicationScoped
public class ReminderService {
  @Inject ReminderRepository reminderRepo;
  
  void onAppointmentCreated(@Observes AppointmentCreatedEvent evt) {
    var appt = evt.appointment();
    
    // Schedule reminders (15min, 1day before)
    reminderRepo.createReminder(appt, Duration.ofMinutes(15));
    reminderRepo.createReminder(appt, Duration.ofDays(1));
  }
}
```

**Rules**:
- Method signature: `void method(@Observes EventType event)`
- One observer method per event type (readable)
- Can have multiple observers for same event (all are called)
- Should not throw exceptions (handle gracefully)
- Should not take too long (fire-and-forget, add async later if needed)

## Event Types in Chronos

| Event | Fired By | Observed By | Purpose |
|-------|----------|-------------|---------|
| `AppointmentCreatedEvent` | AppointmentService | WebPushService, ReminderService | Notify participants, schedule reminders |
| `AppointmentUpdatedEvent` | AppointmentService | WebPushService | Notify of time/details change |
| `AppointmentDeletedEvent` | AppointmentService | WebPushService, ReminderService | Notify cancellation, cancel reminders |
| `ParticipationUpdatedEvent` | ParticipationService | WebPushService | Notify organizer of RSVP |
| `ReminderTriggeredEvent` | ReminderScheduler | WebPushService | Send reminder notification |

## Testing

### Testing Publishers

```java
// Test that service fires event
@Test
void testCreateFiresEvent() {
  var captor = ArgumentCaptor.forClass(AppointmentCreatedEvent.class);
  var eventMock = mock(Event.class);
  
  var service = new AppointmentService(repo, authz, eventMock);
  var dto = new CreateAppointmentDto("Meeting", ...);
  
  service.create(dto, "user123");
  
  verify(eventMock).fire(captor.capture());
  assertEquals("Meeting", captor.getValue().appointment().title);
}
```

### Testing Observers

```java
// Test that observer handles event
@Test
void testPushServiceNotifiesOnAppointmentCreated() {
  var mockAdapter = mock(WebPushAdapter.class);
  var mockUserRepo = mock(UserRepository.class);
  var service = new WebPushService(mockAdapter, mockUserRepo);
  
  var appt = new Appointment("Meeting", "organizer1", ...);
  var evt = new AppointmentCreatedEvent(appt);
  
  service.onAppointmentCreated(evt);
  
  verify(mockAdapter, times(2)).send(any(), contains("Meeting"));
}
```

### Integration Testing

```java
// Test event fire + observation together
@QuarkusTest
public class AppointmentEventIntegrationTest {
  @Inject AppointmentService appointmentService;
  @Inject WebPushService webPushService;
  @Inject WebPushAdapter pushAdapter;
  
  @Test
  void testEventFiredAndObserved() {
    // Spy on push adapter to verify it was called
    var captor = ArgumentCaptor.forClass(String.class);
    
    var dto = new CreateAppointmentDto("Meeting", ...);
    appointmentService.create(dto, "user123");
    
    // Verify push notifications were sent (observer was called)
    verify(pushAdapter).send(any(), captor.capture());
    assertTrue(captor.getValue().contains("Meeting"));
  }
}
```

## Async Events (Future)

For long-running observers (email, analytics), use `@Observes(notifyObserver = Reception.IF_EXISTS)`:

```java
// Not used yet, but future pattern:
@ApplicationScoped
public class EmailService {
  @Async
  void onAppointmentCreated(@Observes(notifyObserver = Reception.IF_EXISTS) AppointmentCreatedEvent evt) {
    // Runs asynchronously (doesn't block original request)
    sendConfirmationEmail(evt.appointment());
  }
}
```

## References

- [Quarkus Events and CDI](https://quarkus.io/guides/cdi)
- [ADR-2025-09-28-007: Layered architecture](../adr/ADR-2025-09-28-007-layered-backend.md)
- Backend code: `backend/src/main/java/de/chronos_live/chronos_date_api/`

---

**Last updated**: 2025-09-28
