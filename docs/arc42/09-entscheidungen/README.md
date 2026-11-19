---
id: AE-index
title: Architekturentscheidungen (Index)
status: entwurf
typ: index
quellen: [kb/adr/]
verknuepft: []
---

# 9 Architekturentscheidungen

Eine Datei pro Entscheidung (`AE-nnn`). Herkunft: `kb/adr/` (47 ADRs, unverändert dort belassen). Welche Entscheidungen verbindlich sind, legt der Mensch bei der Freigabe fest; Detail-ADRs des Rust-Ports sind ggf. besser im Wiki aufgehoben (siehe `wiki/proposals/2026-11-19-migration-kb.md`). AE-001..003 behalten die Nummern der früheren Sammeldatei.

| ID | Entscheidung | ADR-Status | Datum |
|---|---|---|---|
| [AE-001](AE-001-use-nuxt-3-vue-3-for-frontend.md) | Use Nuxt 3 + Vue 3 for frontend | accepted | 2025-09-28 |
| [AE-002](AE-002-use-quarkus-3-for-rest-backend.md) | Use Quarkus 3 for REST backend | accepted | 2025-09-28 |
| [AE-003](AE-003-keycloak-oidc-for-authentication.md) | Keycloak OIDC for authentication | accepted | 2025-09-28 |
| [AE-004](AE-004-split-eventbus-trait-for-dyn-compatibility.md) | Split EventBus Trait for Dyn Compatibility | accepted | 2026-10-03 |
| [AE-005](AE-005-store-user-id-as-uuid-in-principalcontext.md) | Store user_id as UUID in PrincipalContext | accepted | 2026-10-03 |
| [AE-006](AE-006-rust-backend-configuration-and-migration-safety.md) | Rust Backend Configuration and Migration Safety | accepted | 2026-10-03 |
| [AE-007](AE-007-rust-backend-ci-quality-gates.md) | Rust Backend CI Quality Gates | accepted | (?) |
| [AE-008](AE-008-service-worker-for-token-refresh.md) | Service Worker for token refresh | accepted | 2025-09-28 |
| [AE-009](AE-009-pinia-for-frontend-state-management.md) | Pinia for frontend state management | accepted | 2025-09-28 |
| [AE-010](AE-010-layered-architecture-for-backend.md) | Layered architecture for backend | accepted | 2025-09-28 |
| [AE-011](AE-011-postgresql-with-flyway-for-persistence.md) | PostgreSQL with Flyway for persistence | accepted | 2025-09-28 |
| [AE-012](AE-012-panache-for-orm.md) | Panache for ORM | accepted | 2025-09-28 |
| [AE-013](AE-013-server-routes-as-auth-proxy.md) | Server routes as auth proxy | accepted | 2025-09-28 |
| [AE-014](AE-014-pluggable-secret-provider-for-helm-chart.md) | Pluggable secret provider for Helm chart | accepted | 2026-10-03 |
| [AE-015](AE-015-appointment-mutations-create-edit-delete-move.md) | Appointment mutations (create/edit/delete/move) | accepted | 2026-10-03 |
| [AE-016](AE-016-add-participant-endpoint-post-api-v2-appointments-id-partici.md) | Add participant endpoint (POST /api/v2/appointments/{id}/participants/{userId}) | accepted | 2026-10-04 |
| [AE-017](AE-017-rsvp-endpoint-design-post-api-v2-appointments-id-participati.md) | RSVP Endpoint Design (POST /api/v2/appointments/{id}/participation) | accepted | 2026-10-04 |
| [AE-018](AE-018-appointment-groups-add-group-endpoint.md) | Appointment Groups: Add group endpoint | accepted | 2026-10-05 |
| [AE-019](AE-019-rust-database-schema-identical-to-the-java-schema.md) | Rust database schema identical to the Java schema | accepted | 2026-10-12 |
| [AE-020](AE-020-vendored-openssl-for-the-static-musl-rust-image.md) | Vendored OpenSSL for the static musl Rust image | accepted | 2026-10-13 |
| [AE-021](AE-021-test-database-pool-uses-acquire-timeout-instead-of-connect-t.md) | Test database pool uses acquire_timeout instead of connect_timeout | accepted | 2026-10-14 |
| [AE-022](AE-022-participation-status-endpoint-java-parity.md) | Participation status endpoint (Java parity) | accepted | 2026-10-14 |
| [AE-023](AE-023-participant-removal-with-hard-delete.md) | Participant removal with hard delete | accepted | 2026-10-14 |
| [AE-024](AE-024-user-and-settings-endpoints-with-a-keycloak-anti-corruption-.md) | User and settings endpoints with a Keycloak anti-corruption layer (Java parity) | accepted | 2026-10-20 |
| [AE-025](AE-025-rust-backend-deploy-dispatch-is-opt-in-via-repository-variab.md) | Rust backend deploy dispatch is opt-in via repository variable | accepted | 2026-10-20 |
| [AE-026](AE-026-groups-and-friendships-endpoints-java-parity.md) | Groups and friendships endpoints (Java parity) | accepted | 2026-10-20 |
| [AE-027](AE-027-push-endpoints-and-event-driven-notifications-in-the-rust-ba.md) | Push endpoints and event-driven notifications in the Rust backend | accepted | 2026-10-20 |
| [AE-028](AE-028-change-participant-role-endpoint.md) | Change participant role endpoint | accepted | 2026-10-20 |
| [AE-029](AE-029-prometheus-metrics-and-health-endpoint-parity-with-quarkus.md) | Prometheus metrics and health endpoint parity with Quarkus | accepted | 2026-10-20 |
| [AE-030](AE-030-contract-parity-harness-for-the-rust-port.md) | Contract parity harness for the Rust port | accepted | 2026-10-20 |
| [AE-031](AE-031-list-appointment-messages-java-parity.md) | List appointment messages (Java parity) | accepted | 2026-10-20 |
| [AE-032](AE-032-list-participants-endpoint-java-parity.md) | List participants endpoint (Java parity) | accepted | 2026-10-20 |
| [AE-033](AE-033-reminder-engine-scheduler-and-leader-election-in-the-rust-ba.md) | Reminder engine, scheduler and leader election in the Rust backend | accepted | 2026-10-20 |
| [AE-034](AE-034-admin-endpoints-api-v2-admin-in-the-rust-backend.md) | Admin endpoints (`/api/v2/admin/*`) in the Rust backend | accepted | 2026-10-20 |
| [AE-035](AE-035-post-appointment-message-java-parity.md) | Post appointment message (Java parity) | accepted | 2026-10-20 |
| [AE-036](AE-036-remove-group-from-appointment-java-parity.md) | Remove group from appointment (Java parity) | accepted | 2026-10-20 |
| [AE-037](AE-037-one-shared-listen-connection-for-the-postgres-event-bus.md) | One shared LISTEN connection for the Postgres event bus | accepted | 2026-10-20 |
| [AE-038](AE-038-java-and-rust-backend-side-by-side-in-the-local-k3s-cluster-.md) | Java and Rust backend side by side in the local k3s cluster (parity run) | accepted | 2026-10-20 |
| [AE-039](AE-039-appointment-invite-parity-read-access-friendship-check-creat.md) | Appointment invite parity (read access, friendship check, creator status) | accepted | 2026-10-20 |
| [AE-040](AE-040-one-error-mapper-for-all-handler-errors-unknown-appointment-.md) | One error mapper for all handler errors; unknown appointment is a 403 | accepted | 2026-10-20 |
| [AE-041](AE-041-test-databases-are-cloned-from-a-template-and-dropped-by-a-g.md) | Test databases are cloned from a template and dropped by a guard | accepted | 2026-10-20 |
| [AE-042](AE-042-appointment-list-paged-embedded-participants-messages-group-.md) | Appointment list paged, embedded participants/messages/group_participants (Java parity) | accepted | 2026-10-20 |
| [AE-043](AE-043-friend-suggestions-search-keycloak-java-parity.md) | Friend suggestions search Keycloak (Java parity) | accepted | 2026-10-20 |
| [AE-044](AE-044-system-messages-group-members-as-participants-admin-message-.md) | System messages, group members as participants, admin message id (Java parity) | accepted | 2026-10-20 |
| [AE-045](AE-045-small-parity-deviations-health-data-trailing-slash-settings-.md) | Small parity deviations (health data, trailing slash, settings 400) | accepted | 2026-10-20 |
| [AE-046](AE-046-final-parity-run-fixes-names-move-message-patch-messages-bar.md) | Final parity run fixes (names, move message, PATCH messages, bare settings 400) and explorative allowlist | accepted | 2026-10-20 |
| [AE-047](AE-047-access-token-audience-is-not-validated-by-default.md) | Access token audience is not validated by default | accepted | 2026-10-22 |
