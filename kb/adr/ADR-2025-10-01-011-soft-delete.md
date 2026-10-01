---
title: ADR-2025-10-01-011: Soft-delete pattern for appointment lifecycle
date: 2025-10-01
status: Accepted
supercedes: (none)
superceded_by: (none)
---

## Context

Chronos needed a way to handle appointment deletion while preserving data for:
- Audit trails (track who deleted what and when)
- Historical reporting (analytics on cancelled/deleted appointments)
- Cascade cleanup (messages, participations, reminders tied to appointment)
- User expectations (user views "appointment deleted" instead of 404)

Evaluated options:
1. **Soft-delete** — Mark status as DELETED/CANCELLED, keep row, preserve history
2. **Hard delete** — Remove row entirely, cannot recover data, breaks audit logs
3. **Archive table** — Move deleted rows to separate archive table (operational complexity)
4. **Event sourcing** — Store immutable events for every state change (overkill for this feature)

## Decision

Use **soft-delete via status flag** for all appointment state changes (delete, cancel).

### Key Choices
- **Status enum**: `AppointmentStatus.PLANNED`, `.CANCELLED`, `.DELETED` (mutually exclusive states)
- **No cascade deletes**: Messages, participations, group participations stay in database
- **Query filtering**: All read queries exclude DELETED/CANCELLED unless explicitly requested
- **Immutable rows**: Once deleted/cancelled, row is not updated again (only status changed)

## Consequences

### Positive
- ✅ **Audit trail**: Complete history of all state changes (created → planned → cancelled/deleted)
- ✅ **Recovery**: Can "undelete" by changing status back (if UI supports it)
- ✅ **Analytics**: Reporting can query DELETED/CANCELLED appointments separately
- ✅ **Referential integrity**: Foreign keys to deleted appointments still valid (no orphaned data)
- ✅ **Backwards compatible**: Existing reads work unchanged (queries filter by status)
- ✅ **No operational risk**: No `DROP TABLE` or cascading deletes

### Negative
- ❌ **Storage**: Database grows with deleted rows (mitigated by archival after N months)
- ❌ **Query complexity**: Every read must exclude soft-deleted rows
- ❌ **Potential confusion**: Developers must remember to filter status in WHERE clause
- ❌ **Performance**: Deleting does not reclaim space immediately (mitigated by VACUUM)

### Trade-offs
- **Audit vs. simplicity**: Soft-delete adds complexity but enables audit logs
- **Storage vs. recovery**: Keeping deleted rows costs space but enables recovery
- **Application logic vs. database**: Filtering logic lives in application, not database triggers

## Implementation Details

### Status Enum
```java
public enum AppointmentStatus {
    PLANNED,    // Created, not yet cancelled or deleted
    CANCELLED,  // User called cancel() — soft-cancelled status
    DELETED     // User called delete() — soft-deleted status
}
```

### Deletion Behavior

**DELETE endpoint** (soft-delete):
- Sets `status = DELETED`
- Fires `AppointmentDeletedEvent`
- Does NOT remove messages, participations, group participations
- Result: 404 on subsequent GET (because query filters `status != DELETED`)

**CANCEL endpoint** (soft-cancel):
- Sets `status = CANCELLED`
- Fires `AppointmentCancelledEvent`
- Same filtering as DELETE — subsequent GETs return 404
- Distinct from DELETE for business logic: "cancelled" vs "deleted" are different states

### Read Query Filtering

All repository methods that read appointments must filter by status:

```java
// Repository
public Appointment getAppointment(Long id, boolean fetchMessages, ...) {
    return find("status != ?1 AND id = ?2", AppointmentStatus.DELETED, id)
        // .fetch(messages, participants, groupParticipants)
        .singleResult();
}

public List<Appointment> search(String oidcId, ...) {
    return find("status NOT IN (?1, ?2) AND ...", 
                AppointmentStatus.DELETED, AppointmentStatus.CANCELLED)
        .list();
}
```

### Migration

Flyway migration adds status column with default value:

```sql
-- V2.2.0__Add_appointment_status_and_soft_delete.sql
ALTER TABLE appointment ADD COLUMN status VARCHAR(50) NOT NULL DEFAULT 'PLANNED';
CREATE INDEX idx_appointment_status ON appointment(status);

-- Backfill existing rows (all existing appointments are PLANNED)
UPDATE appointment SET status = 'PLANNED' WHERE status = 'PLANNED' OR status IS NULL;
```

## Testing

### Unit Tests
- Soft-delete sets status to DELETED and fires event
- Soft-cancel sets status to CANCELLED and fires event
- No cascade delete occurs (participations remain)

### Integration Tests
- After delete, subsequent GET returns 404 (because query filters status)
- After cancel, subsequent GET returns 404
- Archive/admin queries can explicitly include DELETED to see history

## Alternatives Considered

### Hard Delete
```
Pros: Simple, reclaims space immediately
Cons: Loses audit trail, breaks referential integrity (messages orphaned), cannot recover
Decision: Rejected — audit trail is critical for debugging and user support
```

### Event Sourcing
```
Pros: Complete immutable history, can replay state
Cons: Significant complexity, overkill for this use case
Decision: Rejected — soft-delete achieves 90% of the value with 10% of the complexity
```

## Related Entries

- [ADR-2025-09-28-008: PostgreSQL with Flyway](ADR-2025-09-28-008-postgres-flyway.md)
- [ADR-2025-09-28-009: Panache for ORM](ADR-2025-09-28-009-panache.md)
- Backend code: `backend/src/main/java/de/chronos_live/chronos_date_api/application/AppointmentService.java`
- Domain model: `backend/src/main/java/de/chronos_live/chronos_date_api/domain/Appointment.java`
- Events: `backend/src/main/java/de/chronos_live/chronos_date_api/application/events/`
