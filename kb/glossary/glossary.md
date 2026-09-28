---
title: Glossary
date: 2025-09-28
---

# Chronos Glossary

## Acronyms & Technical Terms

### A

**AccessToken**
JWT (JSON Web Token) that proves a user is authenticated. Issued by Keycloak, expires quickly (5 mins typical), stored in httpOnly cookie. Used to authenticate requests to the backend.

**ActiveRecord**
ORM pattern where entities have query methods directly (e.g., `Appointment.findById(1)`). Used by Panache. Opposite of Repository pattern.

**OIDC**
OpenID Connect. Authentication protocol built on OAuth 2.0. Chronos uses OIDC for secure user authentication via Keycloak.

**Appointment** (German: *Termin*)
The main domain entity in Chronos. Represents a scheduled event with a title, time, organizer, and list of participants.

### C

**CDI**
Contexts and Dependency Injection. Java framework for dependency injection. Used throughout Quarkus backend.

**Cookie**
Small piece of data stored in browser. Chronos stores auth tokens in httpOnly cookies (inaccessible to JavaScript).

**CORS**
Cross-Origin Resource Sharing. Browser security mechanism. Chronos avoids CORS complexity by routing all API calls through Nuxt server routes (same-origin).

### D

**DTO**
Data Transfer Object. A plain object used to transfer data between layers (e.g., between resource and service). Separate from domain entities.

**Domain Model**
The core business concepts in code (Appointment, Participation, User, etc.). Found in `domain/` package.

### E

**Entity**
Hibernate JPA entity. Represents a row in the database (Appointment, User, etc.). Decorated with `@Entity` and extends `PanacheEntity`.

### F

**Flyway**
Database migration tool. Manages schema versions in PostgreSQL. Migrations are immutable once deployed.

**Frontend**
Nuxt 3 + Vue 3 single-page application (SPA). Runs in browser. All UI text is in German.

### H

**Hibernate**
Java ORM (Object-Relational Mapping) library. Maps Java objects to database tables. Used via Panache in Quarkus.

**httpOnly Cookie**
Cookie inaccessible to JavaScript (set with `HttpOnly` flag). More secure than JavaScript-accessible cookies. Chronos stores auth tokens in httpOnly cookies.

### J

**JWT**
JSON Web Token. Token format used by Keycloak. Contains encoded claims (user ID, expiry, etc.) signed by Keycloak. Validated by backend.

### K

**Keycloak**
Open-source identity and access management server. Handles user authentication, registration, password reset. Chronos integrates via OIDC.

### M

**MapStruct**
Java compile-time code generation library. Automatically generates DTO↔Entity mappers. No reflection, type-safe.

**Middleware**
Nuxt concept. A function that runs on every page navigation. Chronos uses middleware for session checks (`auth.global.ts`).

### N

**Nuxt**
Modern Vue 3 framework. Provides routing, server routes, middleware, auto-imports. Chronos uses Nuxt 3 for the frontend.

### O

**ORM**
Object-Relational Mapping. Technology that maps Java objects to database tables. Hibernate is the ORM used here.

**Organizer**
User who creates an appointment. Has permission to edit/delete the appointment.

### P

**Panache**
Quarkus's ORM wrapper around Hibernate. Provides ActiveRecord pattern (simpler than standard Hibernate).

**Participation**
Domain entity representing a user's involvement in an appointment. Has status (PENDING, ACCEPTED, DECLINED).

**PrincipalContext**
Request-scoped CDI bean holding the current user's OIDC subject (ID). Injected into services to get the logged-in user.

**Push Notification**
Message sent to user's device (Web Push API). Used to notify of appointment updates, invitations, etc. Requires VAPID credentials.

### Q

**Quarkus**
Modern Java framework for building REST APIs. Fast boot time, low memory, supports native compilation.

### R

**Repository**
Data access layer object. Manages database queries for a single entity type. Chronos repositories extend `PanacheRepository<T>`.

**RefreshToken**
Long-lived JWT used to obtain new access tokens. Stored in httpOnly cookie. Never directly used by frontend (Service Worker handles refresh).

### S

**Service**
Business logic layer. Contains authorization checks, transactions, event firing. Services inject repositories and use them to query/persist data.

**Service Worker**
JavaScript worker that runs in browser background. Intercepts fetch requests, handles token refresh, sends/receives messages. Critical for auth in Chronos.

**Session**
User's logged-in state. In Chronos, session is represented by httpOnly cookies (access token, refresh token, expiry timestamp).

**SPA**
Single Page Application. Frontend rendered entirely in browser (no server-side rendering). Chronos is an SPA.

**SSR**
Server-Side Rendering. Rendering pages on the server before sending to browser. Chronos has SSR disabled (SPA mode).

### T

**Token**
Authentication proof. JWT issued by Keycloak, sent with every request to backend.

**Transaction**
Database operation with ACID guarantees. Quarkus services use `@Transactional` to mark transaction boundaries.

### V

**VAPID**
Voluntary Application Server Identification. Public/private key pair used for Web Push. Without VAPID, push notifications won't work.

**Vue 3**
Modern JavaScript framework for building user interfaces. Used by Nuxt. Supports Composition API (`<script setup>`).

### W

**Web Push API**
Browser API for sending notifications. Chronos uses it to notify users of appointment updates.

### Z

**Zustand**
JavaScript state management library (not used in Chronos; considered alternative to Pinia).

---

## German Terms

| Term | Meaning |
|------|---------|
| Termin | Appointment |
| Einladung | Invitation |
| Teilnehmer | Participant |
| Organisator | Organizer |
| Akzeptieren | Accept |
| Ablehnen | Decline |
| Ausstehend | Pending |
| Gespeichert | Saved |
| Fehler | Error |
| Warnung | Warning |
| Erfolgreich | Success |
| Wird geladen | Loading |
| Keine Ergebnisse | No results |
| Benutzer | User |
| Gruppe | Group |
| Einstellungen | Settings |
| Abmelden | Logout |
| Anmelden | Login |

---

**Last updated**: 2025-09-28
