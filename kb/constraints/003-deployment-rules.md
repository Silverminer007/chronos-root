---
name: Deployment Rules and Practices
description: Rules for merging to main/develop and deploying to production/staging
category: constraints
last-updated: 2026-09-27
---

# Deployment Rules and Practices

## Branch Protection Rules

### `main` Branch
- **Minimum requirement**: 1 approval from code review
- **CI status**: All checks must pass (tests, code quality)
- **Force push**: Forbidden (prevents accidental overwrites)
- **Default deployment**: `latest` Docker image → Production
- **Stability**: Always production-ready

### `develop` Branch
- **Minimum requirement**: 1 approval
- **CI status**: All checks must pass
- **Force push**: Forbidden
- **Default deployment**: `develop` Docker image → Staging
- **Stability**: Candidate for production; stage for testing

## PR Workflow

1. **Create branch**: `feature/<ticket-id>-<description>`
2. **Implement changes**: Push commits, run `./mvnw verify` locally
3. **Open PR**: Against `main` (or `develop` for experimental work)
4. **Code review**: Address reviewer feedback
5. **CI passes**: All checks must pass before merge
6. **Merge**: Squash/rebase to keep history clean (one commit per feature)
7. **Auto-deploy**: GitHub Actions automatically deploys based on branch

## Deployment Pipeline

```
Feature PR → main
    ↓
Backend CI: compile + test + code quality
    ↓
Frontend CI: lint + test + build
    ↓
All green → merge to main
    ↓
Docker build + push image:latest to GHCR
    ↓
Repository dispatch → deployment workflow
    ↓
Helm upgrade → Kubernetes production
```

**Timeline**: Merge to live production ~ 5-10 minutes

## Database Migrations

**Rule**: All schema changes must be Flyway migrations (`db/migration/V*.sql`)

**Format**:
```
V<major>.<minor>.<patch>__<description>.sql

Example:
V2.1.3__add_reminder_status_column.sql
```

**Constraints**:
- Never modify existing migrations (breaks deployed systems)
- Always add NEW migrations for changes
- Migrations must be idempotent (safe to run twice)
- Test migrations locally before merge

**Example**:
```sql
-- V2.1.0__add_appointment_reminders.sql
ALTER TABLE appointments 
ADD COLUMN reminder_status VARCHAR(20) DEFAULT 'PENDING';

CREATE INDEX idx_reminders_status 
ON appointments(reminder_status, start_time);
```

## Rollback Strategy

**If production breaks**:
1. **Revert PR**: Create revert PR against `main`
2. **Emergency merge**: Skip code review if critical
3. **Alert team**: Post in #incidents channel
4. **Rollback deployment**: Re-deploy previous tag

**Example**:
```bash
# Revert the problematic commit
git revert <commit-sha>
git push origin <branch>

# Create PR (will be auto-merged to main)
gh pr create --title "Revert: Appointment service broken in #45"
```

## Zero-Downtime Deployment

Kubernetes ensures zero-downtime:
- **Rolling updates**: Old pods run while new ones start
- **Health checks**: New pods must be healthy before old ones die
- **Readiness probes**: Liveness probes detect crashed services

**Requirement**: Services must be backward-compatible (no breaking API changes).

## Secrets Management

**Never commit secrets** to git:
- Database passwords
- API keys
- OAuth client secrets
- VAPID push keys

**Store in**:
- GitHub Secrets (CI/CD)
- Kubernetes Secrets (runtime)
- `.env.local` (development, never committed)

**Example `.env.local`**:
```
DB_PASSWORD=secret123
AUTH_SERVER_CLIENT_SECRET=xxxx
```

## Feature Flags

**For risky features**:
1. Deploy behind feature flag (disabled by default)
2. Enable for internal testing
3. Gradually enable for users
4. Monitor metrics
5. Remove flag when stable

**Example**:
```java
@Inject
Config config;

public void process() {
    if (config.getOptionalValue("feature.new-reminder-engine", Boolean.class)
        .orElse(false)) {
        // New logic
    } else {
        // Old logic
    }
}
```

## Deployment Checklist

Before merging to `main`:

- [ ] All CI checks pass
- [ ] Code reviewed and approved
- [ ] No new warnings (SpotBugs, PMD, Checkstyle)
- [ ] Test coverage adequate (70%+)
- [ ] Database migrations tested locally
- [ ] No secrets in commits (`git log -p` check)
- [ ] Commit messages are clear
- [ ] No breaking API changes (or feature flagged)
- [ ] Documentation updated (README, CLAUDE.md, KB)

## Monitoring Post-Deploy

After deployment to production:

1. **Check deployment status**: `kubectl get deployments -n chronos-prod`
2. **View logs**: `kubectl logs -n chronos-prod -l app=chronos`
3. **Monitor metrics**: Prometheus/Grafana dashboard
4. **Check uptime**: Ensure no 5xx errors
5. **Verify features**: Manual testing of affected endpoints

**Alert thresholds**:
- Error rate > 1% → page oncall
- Latency p95 > 500ms → investigate
- Pod restart loop → rollback

## Rules

1. **Always use feature branches**: Never push directly to main/develop
2. **One feature per PR**: Makes review easier and rollback safer
3. **CI must pass**: No exceptions; all checks required
4. **Code review required**: Peer review catches bugs early
5. **Migrations cannot change**: Only add new ones
6. **No secrets in code**: Always use environment variables
7. **Database backward-compatible**: Support old client code during rollout
8. **Monitor after deploy**: Watch metrics for 30 minutes post-deployment
9. **Rollback fast**: If broken, revert immediately
10. **Document changes**: Update KB if patterns/constraints change
