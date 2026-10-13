# Chronos Domain Glossary & Architecture Context

This document defines the core entities, key concepts, important seams, and architectural patterns in the Chronos backend. It serves as a reference for code reviews, design decisions, and onboarding.

---

## Core Entities

### **Appointment**
A scheduled event with participants, managed via Web/REST API. Appointments can involve individual participants or entire groups.

**Key Fields:**
- `name`, `description`, `venue` — Event details
- `startTime`, `endTime` — Schedule (Instant)
- `status` — One of `PLANNED`, `CANCELLED`, `DELETED`, `NOT_ENOUGH_ATTENDEES`
- `minimalAttendees` — Threshold for cancellation
- `lastUpdate`, `createdAt` — Audit timestamps

**Relationships:**
- `Set<AppointmentParticipation>` — Individual user participation
- `Set<AppointmentGroupParticipation>` — Group participation (users join as group members)
- `Set<Message>` — Chat messages within appointment

### **UserIdentity**
A lightweight record representing a resolved Keycloak user. Never persisted as an entity; always resolved from Keycloak via `IdentityPort`.

```java
record UserIdentity(
    String oidcId,
    String firstName,
    String lastName,
    String email,
    String profilePictureUrl
)
```

**Special Cases:**
- `UserIdentity.deleted(oidcId)` — Sentinel for users removed from Keycloak (404 responses)
- `isDeleted()` — Returns true when user no longer exists in Keycloak
- `getName()` — Returns user's full name or "Gelöschter Benutzer" (deleted user)

### **Participation & Statuses**

**ParticipationStatus** — User's response to an appointment invitation:
- `PENDING` — Invitation sent, awaiting response (Ausstehend)
- `APPROVED` — User accepted (Akzeptiert)
- `REJECTED` — User declined (Abgelehnt)

**UserRole** — Role assigned to a participant within an appointment:
- `NONE` — No role (default)
- `GUEST` — Invited guest
- `ATTENDANT` — Regular attendee
- `HELPER` — Offers assistance
- `RESPONSIBLE` — Event organizer or co-organizer

**AppointmentStatus** — Lifecycle state of the appointment:
- `PLANNED` — Active, upcoming or in-progress
- `CANCELLED` — Explicitly cancelled by organizer
- `DELETED` — Soft-deleted (data retained for audit)
- `NOT_ENOUGH_ATTENDEES` — Cancelled due to low participation

### **Group Membership**

**Group** — A container for users, created by and owned by a single user.

**GroupMember** — Relationship representing a user's membership in a group.

```java
@Entity
public class Group extends PanacheEntity {
    String ownerOidcId;           // Group creator's OIDC ID
    String groupName;              // Display name
    Set<GroupMember> members;      // Members of this group
}

@Entity
public class GroupMember extends PanacheEntity {
    Group group;                   // Parent group (lazy-loaded)
    String userOidcId;             // Member's OIDC ID
}
```

**Usage:** Groups are used to invite multiple users to appointments at once. A participant's `AppointmentGroupParticipation` entity tracks that they joined "via group X".

### **Friend Network**

**FriendshipRequest** — Represents a friend connection request or established friendship.

**FriendshipStatus** — Current state of a friendship:
- `PENDING` — Request sent, awaiting response (Anfrage offen)
- `ACCEPTED` — Request accepted = friends (Anfrage angenommen)
- `DECLINED` — Request declined (Anfrage abgelehnt)

```java
@Entity
public class FriendshipRequest extends PanacheEntity {
    String requesterId;            // Requester's OIDC ID
    String addresseeId;            // Recipient's OIDC ID
    FriendshipStatus status;       // Current status
    Instant createdAt;             // When request was sent
    Instant respondedAt;           // When response was given
}
```

---

## Key Concepts

### **Keycloak Identity Resolution**
The backend does not trust or store Keycloak user data directly. Instead:
1. Every authenticated request supplies a JWT with the user's OIDC subject ID
2. `PrincipalContextFilter` extracts and stores this ID in request-scoped `PrincipalContext`
3. Services inject `PrincipalContext` to learn the current user's OIDC ID
4. When user details (name, email, avatar) are needed, services query `IdentityPort` (see "Important Seams")

**Why:** Keycloak is the source of truth. By keeping a local cache layer (`IdentityPort`), we decouple from Keycloak's API and handle user deletions gracefully.

### **Notification Rules**
User notification preferences are stored in `Settings` entity. Rules determine:
- Whether a user receives push notifications (enabled/disabled)
- Which types of events trigger notifications (appointment changes, messages, reminders)
- Quiet hours and mute schedules

The `WebPushAdapter` is the execution layer; the reminder engine and event observers decide *when* to push.

### **Event-Driven Architecture**
Services fire CDI `Event<T>` objects to decouple side-effects (push notifications, reminders, logging) from business logic:

```java
@Inject Event<AppointmentCreatedEvent> appointmentCreated;
appointmentCreated.fire(new AppointmentCreatedEvent(apptId, creatorOidcId));
```

Other beans observe these events and act independently:

```java
public void onAppointmentCreated(@Observes(during = AFTER_SUCCESS) AppointmentCreatedEvent event) {
    // Send push notifications, schedule reminders, etc.
}
```

**Benefit:** Loose coupling; services don't know about or depend on notification logic.

### **Query Batching & N+1 Prevention**
Chronos avoids N+1 query patterns by batching lookups:

- `IdentityPort.findByIds(Collection<String> oidcIds)` — Single IN query to resolve multiple users
- `GroupRepository.findByIds(Set<Long> ids)` — Batch group lookup
- Enrichment services (see "Patterns" below) collect all needed IDs first, then query once

**Why:** Appointments may have dozens of participants; fetching each user separately would explode query count. Batch queries amortize the cost.

---

## Important Seams

### **IdentityPort**
**File:** `application/ports/IdentityPort.java`

Anti-corruption layer between domain and Keycloak. All identity lookups go through this interface.

**Public API:**
- `UserIdentity findById(String oidcId)` — Single user lookup with caching
- `Map<String, UserIdentity> findByIds(Collection<String> oidcIds)` — Batch lookup (no N+1)
- `void upsert(UserIdentity identity)` — Cache/refresh user profile
- `boolean existsById(String oidcId)` — Existence check
- `List<UserIdentity> search(String query, int limit)` — Search Keycloak by name/email

**Implementation:** `LocalDbIdentityAdapter` — wraps Keycloak Admin Client with intelligent caching via `UserProfile` entity. Handles 404 responses (deleted users) gracefully via `UserIdentity.deleted()` sentinel.

**Usage:** Injected into services that need to enrich DTOs with user details (name, email, profile picture).

### **WebPushAdapter**
**File:** `infrastructure/WebPushAdapter.java`

Implements push notification delivery via Web Push Protocol (VAPID). Wraps third-party `nl.martijndwars.webpush.PushService`.

**Public API:**
- `String getVapidPublicKey()` — Returns VAPID public key for client subscription registration
- `void send(String userOidcId, String payload)` — Sends JSON payload to all of user's active subscriptions; handles HTTP 410/404 (expired subscriptions) gracefully

**Called By:** `WebPushService` (event observer) when appointments change, messages arrive, or reminders fire.

**Why:** Isolates Web Push details (VAPID key rotation, subscription management, HTTP error handling) from business logic.

### **SettingsRepository**
**File:** `infrastructure/SettingsRepository.java`

Panache repository for `Settings` entity — stores per-user notification preferences.

**Key Methods:**
- `Optional<Settings> findByUserOidcId(String userOidcId)` — Retrieves user's notification settings (push enabled, reminder preferences, quiet hours)

**Usage:** Queried by `AppointmentReminderService` and `WebPushService` to decide whether to send notifications for a user.

---

## Key Patterns

### **Event Firing**
Services emit domain events to trigger side-effects without coupling:

```java
@ApplicationScoped
public class FriendshipService {
    @Inject Event<FriendshipRequestSentEvent> friendshipRequestEvent;

    public void sendRequest(String requesterOidcId, String addresseeOidcId) {
        FriendshipRequest request = new FriendshipRequest();
        request.persist();
        
        UserIdentity requester = identityPort.findById(requesterOidcId);
        friendshipRequestEvent.fire(new FriendshipRequestSentEvent(
            request.id, requesterOidcId, addresseeOidcId, requester.getName()
        ));
    }
}
```

Observers react independently:

```java
@ApplicationScoped
public class WebPushService {
    public void onFriendshipRequestSent(
        @Observes(during = TransactionPhase.AFTER_SUCCESS) FriendshipRequestSentEvent event
    ) {
        // Send push notification to addressee: "Alice sent you a friend request"
    }
}
```

**Event Records** live in `application/events/` and capture immutable facts about what happened (e.g., `AppointmentCreatedEvent`, `MessageSentEvent`, `FriendshipAcceptedEvent`).

### **Enrichment**
Services enrich DTOs with user/group details using batch queries to avoid N+1:

```java
public void enrichAppointmentDtos(List<AppointmentDto> dtos) {
    // 1. Collect all unique user OIDs across participants
    Set<String> allOidcIds = dtos.stream()
        .flatMap(d -> d.getParticipants().stream())
        .map(UserParticipantDto::getUserId)
        .collect(Collectors.toSet());

    // 2. Single batch query (avoids N+1)
    Map<String, UserIdentity> userMap = identityPort.findByIds(allOidcIds);

    // 3. Enrich each DTO in-place with name, email, avatar
    for (AppointmentDto dto : dtos) {
        for (UserParticipantDto p : dto.getParticipants()) {
            UserIdentity u = userMap.get(p.getUserId());
            if (u != null) {
                p.setName(u.getName());
                p.setProfilePictureUrl(u.profilePictureUrl());
            }
        }
    }
}
```

**Why:** Returns user-facing DTOs with all necessary display data, reducing client-side lookups.

### **Query Batching**
Repositories and services batch queries to prevent N+1 patterns:

```java
// IdentityPort.findByIds() — Single IN query
public Map<String, UserIdentity> findByIds(Collection<String> oidcIds) {
    return UserProfile.find("oidcId IN ?1", oidcIds.stream().distinct().toList())
        .map(/* convert to UserIdentity */)
        .collect(Collectors.toMap(UserIdentity::oidcId, identity -> identity));
}

// GroupRepository.findByIds() — Batch group lookup
public List<Group> findByIds(Set<Long> ids) {
    return Group.find("id in ?1", ids).list();
}

// AppointmentParticipationRepository — Batch-fetch group participants
public List<AppointmentGroupParticipation> listGroupParticipationsForUser(
    Long appointmentId, String userOidcId
) {
    return AppointmentGroupParticipation.find(
        "appointment.id = ?1 AND group.members.userOidcId = ?2",
        appointmentId, userOidcId
    ).list();
}
```

**Rule of Thumb:** Before looping over results, ask: "Do I need to fetch more data per item?" If yes, batch-fetch first.

### **Reminder Rule Engine**
Extensible reminder system via `ReminderRule` interface:

```java
public interface ReminderRule {
    boolean appliesTo(Appointment appointment);           // Does this rule handle this appointment?
    List<Instant> computeTriggerTimes(Appointment appointment); // When should reminder fire?
    void execute(Appointment appointment, ReminderEventPort port);
}
```

**Rule Implementations:**
- `AppointmentReminderRule` — Generic reminders (default: 15 min before start)
- `ShortWeekdayRSVPRule` — For short weekday appointments: remind 3 hours before
- `ShortWeekendRSVPRule` — For short weekend appointments: remind 2 hours before
- `LongAppointmentRSVPRule` — For long appointments: remind 1 day before

**Scheduler (`AppointmentReminderService`):** Runs every ~5 minutes, evaluates all rules, fires `AppointmentReminderEvent` when a trigger time is due.

**Why:** New reminder strategies can be added by implementing `ReminderRule` without modifying existing code.

---

## Authorization & Security

- **Authentication:** Keycloak OIDC (Authorization Code flow). JWT in Bearer token.
- **Principal Extraction:** `PrincipalContextFilter` stores current user's OIDC ID in request-scoped `PrincipalContext`.
- **Authorization:** `AuthorizationService` enforces role/membership checks (e.g., "Is this user an organizer of this appointment?"). **Not in JAX-RS layer** — centralized in service methods.
- **Cross-User Access:** Appointments are visible only to:
  - Organizer
  - Individual participants
  - Members of invited groups

---

## Testing Patterns

- **Unit Tests:** Use `Testcontainers` to spin up PostgreSQL for integration-style testing
- **Fixtures:** `src/test/resources/db/testdata/` contains SQL fixtures for seeding test data
- **Mock Keycloak:** Tests mock `IdentityPort` to avoid real Keycloak calls
- **Entity Testing:** Directly instantiate and persist entities via Panache; verify relationships

---

## Related Files

- `CLAUDE.md` — High-level architecture overview, build commands, stack info
- `backend/README.md` — Project setup and native compilation
- `backend/src/main/resources/db/migration/` — Flyway schema migrations (versioned)
- `backend/src/main/java/de/chronos_live/chronos_date_api/` — Full domain model and services
