---
title: C2025-09-28-004: Database migrations are forward-only
date: 2025-09-28
status: Active
superceded_by: (none)
---

## Description

**Database migrations can only move forward.** Once a migration is deployed to production, it is immutable. Never:
- Modify a deployed migration
- Delete a deployed migration
- Roll back a migration (unless in emergency, with full recovery plan)

If a migration contains an error, create a new migration that fixes it.

## Rationale

- **Auditability**: Every schema change is recorded in git and production
- **Reproducibility**: Fresh database can be built from migrations alone
- **Multi-environment**: Dev, staging, prod all run same migrations in order
- **Team safety**: Prevents conflicts when multiple people work on migrations
- **Production safety**: Never modifying prod migrations prevents accidental data loss

## Implications

### Workflow
1. Create migration `V1.1.0__add_description.sql`
2. Test locally
3. Commit to git
4. Deploy to staging
5. Test in staging
6. Deploy to production
7. **Migration is now immutable**
8. If error found, create `V1.1.1__fix_description_index.sql`

### Failed Deployments
If a migration fails during deployment:

**Option 1** (preferred): Fix the migration SQL, increment version, re-deploy
```
V1.1.0__add_description.sql (remove from prod, keep in repo)
V1.1.1__add_description_fixed.sql (correct version, deploy again)
```

**Option 2** (emergency): Full database recovery from backup, replay migrations

### Naming Conventions
```
V<major>.<minor>.<patch>__<description>.sql
```

Examples:
```
V1.0.0__Initial_schema.sql
V1.1.0__Add_appointment_description.sql
V1.1.1__Add_description_index.sql          (fixing V1.1.0)
V1.2.0__Create_reminder_table.sql
V2.0.0__Refactor_user_table.sql
```

### Tools
- **Flyway**: Enforces forward-only migrations (won't run if a migration was removed)
- **Git**: Prevents deletion (history is permanent)
- **Review process**: Code review catches mistakes before deployment

## Example: How to Fix a Migration Error

### Scenario
Migration `V1.1.0__add_description.sql` adds a column but forgets an index:

```sql
-- V1.1.0__add_description.sql (WRONG)
ALTER TABLE appointment ADD COLUMN description TEXT;
-- Oops, forgot to add index for search
```

### Fix
Create a new migration that adds the missing index:

```sql
-- V1.1.1__add_description_index.sql (FIX)
CREATE INDEX idx_appointment_description 
  ON appointment (description);
```

Don't try to modify V1.1.0 — it's already in production!

## References

- [ADR-2025-09-28-008: PostgreSQL with Flyway](../adr/ADR-2025-09-28-008-postgres-flyway.md)
- [ARCH-2025-09-28-004: Database schema management](../architecture/ARCH-2025-09-28-004-schema-management.md)

---

**Last verified**: 2025-09-28
