# Chronos LLM Wiki

A mental model for understanding the Chronos appointment management system. This wiki is designed to help LLMs (and humans) navigate, understand, and extend the codebase effectively.

---

## System Overview

**Chronos** is a group scheduling and appointment management app for youth organizations. Think of it as "group calendar meets event RSVP meets notification hub."

### The Core Mental Model

```
User → Frontend (Nuxt/Vue) → Server Routes (Proxy) → Backend (Quarkus) → Database
                              ↑ Auth / Token Refresh ↑
                              ← Push Notifications ←
```

Three layers:
1. **Frontend**: Nuxt SPA — renders UI, manages client state (Pinia stores), handles user interactions
2. **Backend**: Quarkus REST API — business logic, database queries, triggers side effects (notifications)
3. **Infrastructure**: PostgreSQL, Keycloak OIDC, Web Push (VAPID)

The frontend **never** calls the backend directly. All requests flow through Nuxt server routes (`/api/v2/*`), which act as a transparent proxy. This design:
- Centralizes token management (server routes read cookies, attach auth headers)
- Enables request interception (the Service Worker can refresh tokens before requests fail)
- Keeps the backend CORS-free

---

## Frontend Architecture

### Auth Flow: The Critical Path

Authentication is the foundation of the entire system. Here's how it works:

1. **User clicks "Login"**
   - `app/middleware/auth.global.ts` → `authStore.login()` → `GET /api/auth/login`
   - Server redirects to Keycloak's `/auth/authorize` endpoint
   - User authenticates at Keycloak

2. **Keycloak redirects back to callback**
   - Browser navigates to `/api/auth/callback?code=...&state=...`
   - `server/api/auth/callback.ts` exchanges the `code` for tokens
   - Sets three httpOnly cookies:
     - `kc_access`: access token (what we use to call the backend)
     - `kc_refresh`: refresh token (what we use to get a new access token)
     - `kc_expires`: Unix timestamp of when `kc_access` expires (client-readable, not httpOnly)
   - Redirects to `/`

3. **On every navigation**
   - Middleware calls `authStore.checkSession()` → `GET /api/auth/isLoggedIn`
   - Server returns 204 (valid) or 401 (expired/invalid)
   - If invalid, redirect to `/`; if user profile incomplete, redirect to `/onboarding`

4. **Token expiry and refresh**
   - This is **not** handled by middleware. Instead, the **Service Worker** (`public/push-sw.js`) intercepts all `/api/*` requests
   - Before forwarding a request, if the token expires in < 5 seconds, it proactively calls `POST /api/auth/refresh`
   - This refresh call exchanges `kc_refresh` for new tokens and updates all three cookies
   - Then the original request proceeds with the fresh token
   - If a request still gets a 401, the SW retries once after refreshing

**Why this design?** The Service Worker runs independently of component lifecycle. It can proactively refresh tokens before they expire, preventing user-facing 401 errors. The middleware only checks *login status*, not token freshness—that's the SW's job.

### Data Fetching: useFetch vs $fetch

Two patterns, two purposes:

```typescript
// useFetch: Reactive, auto-tracks dependencies, cancels on unmount
const { data: appointments } = useFetch('/api/v2/appointments')

// $fetch: Imperative, one-off call, use in store actions/event handlers
const response = await $fetch('/api/v2/appointments', { 
  method: 'POST', 
  body: { ... } 
})
```

**Mental model**: `useFetch` is for "display this on the page" (reactive), `$fetch` is for "do this action" (imperative). All business logic lives in Pinia stores, which call `$fetch` and update state. Components call store actions, never `$fetch` directly.

### Store Pattern

```typescript
// app/stores/appointments.ts
export const useAppointmentStore = defineStore('appointments', () => {
  const appointments = ref<Appointment[]>([])
  
  // Actions always call $fetch, then update state
  const createAppointment = async (dto: CreateAppointmentDto) => {
    const created = await $fetch('/api/v2/appointments', {
      method: 'POST',
      body: dto
    })
    appointments.value.push(created)
    return created
  }
  
  return { appointments, createAppointment }
})
```

**Mental model**: Stores are the single source of truth for data that's relevant to multiple components. If a component needs a piece of data, it reads from the store, not from an inline `useFetch`. This makes the store the "API contract" between components and the backend.

### Layout System

Two layouts, two user types:

- **`layouts/landingpage.vue`**: Public layout, no auth required
  - Used by pages in `app/pages/public/*`
  - Shows marketing content, login/signup buttons
  
- **`layouts/default.vue`**: Authenticated shell, requires valid session
  - Used by all other pages
  - Contains sidebar, top nav, user menu
  - If user navigates here without a valid session, middleware redirects to `/`

**Mental model**: Layouts are security boundaries. If a page doesn't explicitly use `landingpage.vue`, it's protected.

---

## Backend Architecture

### Layered Structure (Inside `de.chronos_live.chronos_date_api`)

Think of the backend as concentric circles of abstraction:

```
JAX-RS Resource (HTTP)
        ↓ (validate, deserialize)
   Service (business logic)
        ↓ (fire events, query repo)
  Panache Repository (ORM)
        ↓
   PostgreSQL
```

#### Presentation Layer (`presentation/`)

**Role**: Map HTTP in/out. Read request body, validate, delegate to service, return response.

```java
@Path("/api/v2/appointments")
public class AppointmentResource {
  @Inject AppointmentService service;
  @Inject PrincipalContext principal;
  
  @POST
  public Response create(CreateAppointmentDto dto) {
    // principal.getSubject() is the OIDC ID of the current user
    var appointment = service.create(dto, principal.getSubject());
    return Response.ok(appointment).build();
  }
}
```

**Rules**:
- Resources **never** query the database directly
- Resources **always** inject `PrincipalContext` to get the current user
- Resources validate input (optional; Hibernate Validator annotations also work)
- Resources never contain business logic beyond "call the service"

#### Application Layer (`application/`)

**Role**: Business logic, transactions, side effects.

```java
@ApplicationScoped
public class AppointmentService {
  @Inject AppointmentRepository repo;
  @Inject Event<AppointmentCreatedEvent> event;
  
  public Appointment create(CreateAppointmentDto dto, String userId) {
    var appt = new Appointment(...);
    appt.persist(); // Panache's activeRecord pattern
    
    // Fire an event; WebPushService observes this asynchronously
    event.fire(new AppointmentCreatedEvent(appt));
    return appt;
  }
}
```

**Mental model**: Services are the "orchestrators." They decide what happens, what data gets queried, what events get fired. Authorization logic lives here (in `AuthorizationService`), not in resources.

**Key pattern: CDI Events**

When something important happens (appointment created, user invited, reminder triggered), the service fires a CDI event:

```java
@Observes
public void onAppointmentCreated(AppointmentCreatedEvent evt) {
  // Send push notifications to all participants
  sendNotifications(evt.appointment());
}
```

This **decouples** side effects. The service doesn't know about (or depend on) push notifications. It just fires an event. The `WebPushService` observes that event asynchronously. Want to add SMS notifications later? Add another observer. The service code never changes.

#### Domain Layer (`domain/`)

**Role**: Hibernate entities. The source of truth for data structure.

```java
@Entity
public class Appointment extends PanacheEntity {
  public String title;
  public String description;
  public LocalDateTime startTime;
  public LocalDateTime endTime;
  public String organizerId;
  @OneToMany(mappedBy = "appointment")
  public List<Participation> participations;
}
```

**Mental model**: Entities are your domain model. They define what data exists and how it relates. Add a field here, run a Flyway migration, and the schema changes. Delete a field, delete it from the entity *and* add a migration to drop the column.

#### Infrastructure Layer (`infrastructure/`)

**Role**: Panache repositories, adapters for external services.

```java
@ApplicationScoped
public class AppointmentRepository implements PanacheRepository<Appointment> {
  // Panache gives us: list(), findById(), persist(), etc. for free
  
  public List<Appointment> findByOrganizerId(String organizerId) {
    return list("organizerId", organizerId);
  }
}
```

**Mental model**: Repositories are query builders. One repository per entity type. Keep queries here, not in services.

#### Mapper Layer (`mapper/`)

**Role**: Convert entities ↔ DTOs using MapStruct.

```java
@Mapper(componentModel = "cdi")
public interface AppointmentMapper {
  AppointmentDto toDto(Appointment entity);
  Appointment toEntity(CreateAppointmentDto dto);
}
```

**Mental model**: DTOs are API contracts. The DTO shape is what the frontend sees. The entity shape is internal. Mappers translate between them. If you add a field to an entity, decide: does it appear in the API? If yes, add it to the DTO and update the mapper. If no, leave it out.

### Authorization

**Not** in resources. In `AuthorizationService`:

```java
@ApplicationScoped
public class AuthorizationService {
  @Inject ParticipationRepository participationRepo;
  
  public void checkCanEditAppointment(String userId, Long appointmentId) {
    var appt = Appointment.findById(appointmentId);
    if (!appt.organizerId.equals(userId)) {
      throw new ForbiddenException("Not the organizer");
    }
  }
}
```

**Mental model**: Authorization is a cross-cutting concern. Check it in the service before allowing an action. This way, the resource layer doesn't need to know the authorization rules.

### Database Migrations

Flyway manages schema changes. Migrations live in `backend/src/main/resources/db/migration/`:

```sql
-- V1.0.0__Initial_schema.sql
CREATE TABLE appointment (
  id BIGINT PRIMARY KEY,
  title VARCHAR(255) NOT NULL,
  ...
);

-- V1.1.0__Add_description.sql
ALTER TABLE appointment ADD COLUMN description TEXT;
```

**Rules**:
- File names: `V<major>.<minor>.<patch>__description.sql`
- Migrations run **up** on startup; never backwards
- Once a migration is committed, never modify it. Create a new one instead.
- Tests use **testdata fixtures** in `src/test/resources/db/testdata/` to set up initial data

**Mental model**: Your entity shapes drive migrations. When you add/remove/change a field on an entity, you must create a migration. The entity definition and the schema must always be in sync.

### Testing Strategy

```bash
./mvnw test                          # Unit tests + integration (Testcontainers spins up PostgreSQL)
./mvnw -Dtest=AppointmentServiceTest test  # Single test class
./mvnw verify                        # Tests + Checkstyle + SpotBugs + PMD
```

**Mental model**: Tests run against a real (containerized) PostgreSQL, not mocks. This catches bugs that mocks would hide (e.g., SQL issues). Tests load testdata fixtures to bootstrap data.

### Code Quality Gates

Three tools run at `verify`:

1. **Checkstyle** (`checkstyle.xml`): Code style (indentation, naming, etc.)
2. **SpotBugs** (`spotbugs-exclude.xml`): Bug patterns (null dereferences, resource leaks, etc.)
3. **PMD** (`pmd-ruleset.xml`): Design issues (empty catch blocks, unused variables, etc.)

If any gate fails, the build fails. Fix them by:
- Running `./mvnw verify` locally and fixing violations
- Or, if it's a false positive, add an exclusion to the respective config file with a comment explaining why

---

## Data Flow: An Appointment RSVP

Let's trace how data flows when a user clicks "I'm coming" on an appointment:

### Frontend
1. User clicks button in `pages/appointments/[id].vue`
2. Component calls `participationStore.rsvp(appointmentId, ACCEPTED)`
3. Store action calls `$fetch('POST /api/v2/appointments/{id}/rsvp', { body: { status: 'ACCEPTED' } })`
4. Request goes to Service Worker
5. SW checks: does `kc_access` expire in < 5s? If yes, refresh first
6. SW attaches `Authorization: Bearer <token>` header and forwards to server

### Server Routes
1. `server/api/v2/[...path].ts` receives the request
2. Reads `kc_access` from cookies, forwards it as `Authorization: Bearer ...` to backend

### Backend (Quarkus)
1. `PrincipalContextFilter` intercepts request, extracts OIDC subject from JWT, stores in request-scoped `PrincipalContext`
2. Request reaches `ParticipationResource.rsvp(appointmentId, dto)`
3. Resource calls `participationService.rsvp(...)`
4. Service queries `participationRepo.find("appointmentId and userId", ...)` (ORM query)
5. Service checks authorization: `authorizationService.checkCanParticipate(...)`
6. Service updates participation status: `participation.status = ParticipationStatus.ACCEPTED; participation.persist();`
7. Service fires event: `event.fire(new ParticipationUpdatedEvent(participation))`
8. Returns updated participation DTO to frontend

### Side Effects (Async)
1. `WebPushService` observes `ParticipationUpdatedEvent`
2. Queries all other participants in the appointment
3. Sends push notification to each: "Alice is coming!"
4. `AppointmentReminderService` observes the event, updates reminder schedule if needed

### Frontend (Response)
1. Server responds with updated participation
2. `$fetch` call resolves in store action
3. Store updates `participations[]` with the response
4. Component reactivity triggers, UI re-renders
5. User sees "You're coming!" instead of the button

---

## Key Patterns & Conventions

### The PrincipalContext Pattern

Every request has a `PrincipalContext` injected. It holds the current user's OIDC subject:

```java
@Inject PrincipalContext principal;

// In a service:
var userId = principal.getSubject();
```

**Never** extract the user from a request header manually. Always use `PrincipalContext`.

### The Service Locator Anti-Pattern (Avoid)

```java
// ❌ DON'T: This makes testing hard
@Inject Event<SomeEvent> event;
event.fire(...);

// ✓ DO: Inject Event<T> once and use it
@Inject Event<AppointmentCreatedEvent> appointmentCreatedEvent;
appointmentCreatedEvent.fire(...);
```

### CDI Event Naming

Events are **past tense** records:

```java
public record AppointmentCreatedEvent(Appointment appointment) {}
public record ParticipationUpdatedEvent(Participation participation) {}
```

Observers **don't** need `@Observes` annotations if they're on the same instance; put them on separate services for loose coupling.

### DTOs for Every API Endpoint

Never return an entity directly (except for very simple read operations):

```java
// ❌ DON'T: Exposes all entity fields, including internal ones
return Response.ok(appointment).build();

// ✓ DO: Return a DTO, let the mapper handle it
var dto = appointmentMapper.toDto(appointment);
return Response.ok(dto).build();
```

---

## Frontend Conventions

### German UI Text

All user-facing text is in German. Store translations in component templates or use a localization library if needed:

```vue
<button @click="handleRsvp">Ich komme mit!</button>
<span class="text-red-500">Fehler: Termin konnte nicht erstellt werden</span>
```

### Dark Mode Colors

Always pair light and dark:

```vue
<div class="bg-white dark:bg-neutral-800">
  <span class="text-neutral-900 dark:text-neutral-100">Text</span>
</div>
```

### TypeScript Types

All interfaces live in `app/types/index.ts`. This is the API contract:

```typescript
export interface Appointment {
  id: number
  title: string
  startTime: string  // ISO 8601
  participations: Participation[]
}

export enum ParticipationStatus {
  PENDING = 'PENDING',
  ACCEPTED = 'ACCEPTED',
  DECLINED = 'DECLINED'
}
```

### Icon Naming

Icons come from Lucide or Simple Icons:

```vue
<Icon name="lucide:calendar" />
<Icon name="lucide:user-plus" />
<Icon name="simple-icons:github" />
```

Check icon names at [lucide.dev](https://lucide.dev) before using.

---

## Development Workflow

### Setting Up Locally

```bash
# Backend
cd backend
./mvnw quarkus:dev  # Starts at :8080, spins up PostgreSQL via Docker

# Frontend (new terminal)
cd frontend
npm install
npm run dev         # Starts at :3000
```

Then:
- Open `http://localhost:3000`
- Login with Keycloak (configured in `.env`)
- Backend Swagger UI at `http://localhost:8080/q/swagger-ui`

### Creating a Feature

1. **Create a branch**: `git checkout -b feat/my-feature`
2. **Implement backend**:
   - Add entity fields if needed
   - Create Flyway migration (`V1.x.x__description.sql`)
   - Add/modify service logic
   - Update DTOs and mappers
   - Add JAX-RS resource endpoints
   - Test with `./mvnw test`
   - Run `./mvnw verify` to check code quality
3. **Implement frontend**:
   - Add store actions to fetch/mutate data
   - Create/modify components
   - Run `npm run lint:fix` to auto-fix style issues
4. **Push & create a draft PR**
   - `git push origin feat/my-feature`
   - Open PR against `main`
   - CI runs tests + code quality checks
5. **Iterate** based on CI feedback

### Code Quality

**Backend**:
```bash
./mvnw verify  # Runs tests, Checkstyle, SpotBugs, PMD
```

**Frontend**:
```bash
npm run lint:fix   # ESLint with auto-fix
npm run typecheck  # TypeScript checking
npm run test:e2e   # Playwright E2E tests (requires running preview server)
```

---

## Deployment

Two environments:

| Env | Branch | Values File | Image Tag |
|-----|--------|-------------|-----------|
| Staging | `develop` | `values-staging.yaml` | `develop` |
| Prod | `main` | `values-prod.yaml` | `latest` |

When you push to `main`, CI:
1. Builds backend image, tags it `latest`, pushes to registry
2. Builds frontend image, tags it `latest`, pushes to registry
3. Triggers deployment workflow
4. Helm upgrades the Kubernetes deployment

---

## Mental Models for LLM Navigation

### "How do I add a new field to appointments?"

1. Add field to `Appointment` entity (`backend/src/main/java/.../domain/Appointment.java`)
2. Create Flyway migration to alter the table (`backend/src/main/resources/db/migration/V*.sql`)
3. Update `AppointmentDto` and `CreateAppointmentDto` if it's API-facing
4. Update `AppointmentMapper` to include the new field
5. Update the JAX-RS resource if the field is user-settable
6. Test locally: `./mvnw test`
7. If frontend needs to display it, update component templates and store actions

### "How do I add a new API endpoint?"

1. Create a JAX-RS resource method (or modify an existing one)
2. Define request/response DTOs
3. Write service logic
4. Add integration test
5. Run `./mvnw verify`
6. On frontend, create store action that calls the endpoint
7. Create component(s) that use the store action
8. Test in browser

### "How do I send a push notification?"

1. Fire a CDI event in the service (e.g., `AppointmentCreatedEvent`)
2. Create an observer in `WebPushService` (e.g., `@Observes AppointmentCreatedEvent`)
3. Query the relevant users from the database
4. Call `webPushAdapter.send(userId, title, body)`
5. Test: deploy to staging or check logs locally

### "Why is my request getting a 401?"

1. **In the browser**: Open DevTools → Network → check request headers. Is `Authorization: Bearer ...` present?
2. **In the Service Worker**: Check `public/push-sw.js`. Is it attaching the token? Is the token expired?
3. **In the backend**: Check `PrincipalContextFilter`. Is it extracting the OIDC subject from the JWT?
4. **In tests**: Are you setting up a valid token? Use Testcontainers fixtures.

---

## Summary

Chronos is a **three-tier system**:
- **Frontend** (Nuxt SPA): Renders UI, manages state, handles auth via Service Worker
- **Server routes** (Nuxt): Proxy layer, centralizes token management
- **Backend** (Quarkus): REST API, business logic, side effects via CDI events

**Key insights**:
- The Service Worker is the auth engine; it refreshes tokens before they expire
- The backend is layered: resources → services → repositories → entities
- Authorization lives in services, not resources
- Side effects (push notifications) decouple via CDI events
- DTOs separate API contracts from internal entities
- All user data flows through typed interfaces (TypeScript frontend, mapped DTOs backend)

Navigate the codebase by understanding these layers and patterns. When adding a feature, follow the same paths previous features took.

