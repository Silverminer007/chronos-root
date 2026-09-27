---
name: Event-Driven Asynchronous Architecture
description: Services fire CDI events for decoupled side-effects like push notifications and reminders
category: architecture
last-updated: 2026-09-27
---

# Event-Driven Asynchronous Architecture

## Decision
Business services fire CDI events to enable decoupled, asynchronous handling of side-effects like push notifications and appointment reminders.

## Event Flow

### Event Definition
Events are immutable Java `record` types defined in `application/events/`:
```java
public record AppointmentCreatedEvent(
    Long appointmentId,
    String title,
    Instant startTime,
    List<String> inviteeIds
) {}
```

### Event Publishing
Services publish events via CDI:
```java
@Inject
Event<AppointmentCreatedEvent> appointmentCreated;

// In business logic:
appointmentCreated.fire(new AppointmentCreatedEvent(id, title, start, invitees));
```

### Event Observation
Side-effect handlers observe events and act asynchronously:
```java
@ApplicationScoped
public class WebPushService {
    public void onAppointmentCreated(@Observes AppointmentCreatedEvent event) {
        // Send push notification to invitees
    }
}

@ApplicationScoped
public class AppointmentReminderService {
    public void onAppointmentCreated(@Observes AppointmentCreatedEvent event) {
        // Schedule reminder rules
    }
}
```

## Benefits

- **Decoupling**: Services don't depend on notification/reminder logic
- **Testability**: Fire events without side-effects during tests
- **Extensibility**: New observers can be added without modifying services
- **Asynchrony**: Side-effects happen after request returns

## Constraints

- Events must be immutable (use `record` types)
- Events should be domain-focused, not infrastructure-focused
- Observers must not fail the transaction (observe but ignore errors)
- Event flow must complete before response is sent (no scheduling across requests)

## Current Events

- `AppointmentCreatedEvent` — appointment added
- `AppointmentUpdatedEvent` — appointment modified
- `UserInvitedEvent` — user added to appointment
- `ParticipationStatusChangedEvent` — RSVP status changed
