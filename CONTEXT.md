# Chronos Domain Model

Chronos is a group scheduling and appointment management platform for youth organisations. The core problem: coordinating availability across multiple people and tracking participation at events.

## Users & Identity

**User** — An individual who can create appointments, join groups, and form friendships. Identified by an OIDC ID from Keycloak. A local **UserProfile** caches name, email, and picture from Keycloak for quick lookup.

**UserProfile** — Local cache of Keycloak identity data. Written on each authenticated request; used for all profile reads. Stays in sync via write-through, so stale data is not a concern.

## Appointments

**Appointment** — A scheduled event with a name, description, venue, and time window (`startTime` → `endTime`). The creator implicitly becomes responsible. Appointments move through a state machine:

- **PLANNED** — The normal state; the event is scheduled.
- **CANCELLED** — The organizer has cancelled it (communicated to participants).
- **DELETED** — Archived or removed from the system (no longer visible in lists, but data persists for audit).
- **NOT_ENOUGH_ATTENDEES** — The appointment did not reach its `minimalAttendees` threshold by the start time.

**minimal_attendees** — The number of confirmed participants required for the appointment to proceed. If an appointment has not gathered this many APPROVED participants by start time, it moves to NOT_ENOUGH_ATTENDEES.

**Appointment Messages** — Threaded communication tied to a specific appointment, allowing participants to discuss before/during/after the event.

## Participation

An appointment can include participants in two forms: individuals and groups. Both grant a role and a participation status.

**Participation Role** — How a user contributes to the appointment:

- **ATTENDANT** — Expected to be present; core participant.
- **HELPER** — Assists but may or may not attend; supporting role.
- **RESPONSIBLE** — Organizer or primary contact.
- **GUEST** — Visitor with no special role.
- **NONE** — No role assigned (placeholder for opt-out or abstention).

**Participation Status** — The user's yes/no/maybe response:

- **APPROVED** — Confirmed attendance.
- **REJECTED** — Declined the invitation.
- **PENDING** — Has not yet responded.

### Individual Participation

**AppointmentParticipation** — A single user invited directly to an appointment. Has an independent role and status. Users can also join via a group invitation (see below).

### Group Participation

**AppointmentGroupParticipation** — A group is invited to an appointment with a specific role (e.g., all members are HELPERS). Individual group members automatically inherit this invitation and role, and can APPROVE or REJECT individually. A group member's final participation combines their direct invite (if any) with their group invite(s).

## Groups

**Group** — A collection of users managed by an owner. Groups are used to:
- Batch-invite members to appointments.
- Organize users by team, class, or interest (e.g., "Scout Troop A").

**GroupMember** — A membership link between a user and a group. The group owner can add/remove members.

## Friendships

**Friendship** — A relationship between two users, used to constrain who can see appointments and who can be invited (not yet strictly enforced, but part of the data model).

**FriendshipRequest** — A directed invitation from one user (requester) to another (addressee). Moves through a state machine:

- **PENDING** — Awaiting the addressee's response.
- **ACCEPTED** — Both users are now friends; the relationship is bidirectional.
- **DECLINED** — The addressee rejected it; the request is terminal.

## Notifications

**AppointmentNotificationSetting** — User's preference for push notifications on appointment events (e.g., appointment moved, participant added):

- **DISABLED** — No notifications.
- **ALL** — Notify on all events.
- **ATTENDANT**, **HELPER**, **RESPONSIBLE** — Notify only for participants with this role.

**GroupNotificationSetting** — User's preference for group membership changes (DISABLED or ENABLED).

## Key Invariants

1. **Appointment start ≤ end** — Time window is always valid; zero-duration appointments are allowed.
2. **Participation status is personal** — Each user decides independently, even when invited via a group.
3. **Group roles are advisory** — A group is invited with a role, but individual members can approve/reject. Role enforcement is a business rule, not a technical constraint.
4. **OIDC ID is the source of truth** — All user references in the system use OIDC ID; never rely on email or name as a unique key.
5. **Friendship is optional** — Currently not enforced; future work may gate appointment visibility by friendship, but today any two users can interact.

## Language Notes

- **UI language**: All user-facing text is in German.
- **Code terminology**: English throughout the codebase.

## Open Questions

- Should partial group attendance (some members approve, some reject) affect the appointment's status, or only individual counts matter?
- Should group members be able to override a group's default role, or is group role immutable?
