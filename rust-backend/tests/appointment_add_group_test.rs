/// Integration tests for adding groups to appointments
/// Tests verify: authorization checks, group validation, and event publishing
#[cfg(test)]
mod appointment_add_group_tests {
    use chronos_date_api::appointments::repository::AppointmentRepository;
    use chronos_date_api::appointments::services::AppointmentService;
    use chronos_date_api::event_bus::postgres::PostgresEventBus;
    use chronos_date_api::test_utils::{TestDb, TestFixtures};
    use std::sync::Arc;
    use uuid::Uuid;

    /// Helper to count events of a specific type in the events table
    async fn count_events(pool: &sqlx::PgPool, event_type: &str) -> i64 {
        sqlx::query_scalar::<_, i64>("SELECT COUNT(*) FROM events WHERE event_type = $1")
            .bind(event_type)
            .fetch_one(pool)
            .await
            .unwrap_or(0)
    }

    /// Helper to add a group to appointment using the service
    async fn add_group_to_appointment(
        service: &AppointmentService,
        actor_id: Uuid,
        appointment_id: Uuid,
        group_id: Uuid,
        role: &str,
    ) -> Result<(), String> {
        service
            .add_group_to_appointment(actor_id, appointment_id, group_id, role)
            .await
            .map_err(|e| e.to_string())
    }

    /// POSITIVE: Successfully add a group to an appointment as creator
    #[tokio::test]
    async fn test_add_group_to_appointment_success() {
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        // Setup: Create creator, group, and appointment
        let creator_id = fixtures
            .create_user("creator_group_test", "creator@example.com")
            .await
            .expect("Failed to create creator");

        let group_id = fixtures
            .create_group(creator_id, "Test Group")
            .await
            .expect("Failed to create group");

        let appointment_id = fixtures
            .create_appointment(creator_id, &Default::default())
            .await
            .expect("Failed to create appointment");

        // Create service with event publisher
        let repo = AppointmentRepository::new(db.pool().clone());
        let event_bus = Arc::new(PostgresEventBus::new(db.pool().clone()));
        let service = AppointmentService::with_events(repo, event_bus);

        // Verify: No event exists yet
        let initial_count = count_events(db.pool(), "AppointmentGroupAddedEvent").await;
        assert_eq!(initial_count, 0);

        // Action: Add group to appointment
        let result = add_group_to_appointment(&service, creator_id, appointment_id, group_id, "ATTENDANT").await;
        assert!(result.is_ok(), "Failed to add group: {}", result.err().unwrap_or_default());

        // Verify: Appointment group participation was created
        let participation_exists: bool = sqlx::query_scalar(
            "SELECT EXISTS(SELECT 1 FROM appointment_groups WHERE appointment_id = $1 AND group_id = $2)"
        )
        .bind(appointment_id)
        .bind(group_id)
        .fetch_one(db.pool())
        .await
        .expect("Failed to check group participation");

        assert!(participation_exists, "Appointment group participation should exist");

        // Verify: Event was fired
        let final_count = count_events(db.pool(), "AppointmentGroupAddedEvent").await;
        assert_eq!(final_count, 1, "AppointmentGroupAddedEvent should be fired");
    }

    /// NEGATIVE: Cannot add group if not the appointment creator
    #[tokio::test]
    async fn test_add_group_unauthorized() {
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        // Setup: Create users
        let creator_id = fixtures
            .create_user("creator_for_auth", "creator@example.com")
            .await
            .expect("Failed to create creator");

        let other_user_id = fixtures
            .create_user("other_user_auth", "other@example.com")
            .await
            .expect("Failed to create other user");

        let group_id = fixtures
            .create_group(creator_id, "Test Group Auth")
            .await
            .expect("Failed to create group");

        let appointment_id = fixtures
            .create_appointment(creator_id, &Default::default())
            .await
            .expect("Failed to create appointment");

        // Create service
        let repo = AppointmentRepository::new(db.pool().clone());
        let event_bus = Arc::new(PostgresEventBus::new(db.pool().clone()));
        let service = AppointmentService::with_events(repo, event_bus);

        // Action: Try to add group as non-creator
        let result = add_group_to_appointment(&service, other_user_id, appointment_id, group_id, "ATTENDANT").await;

        // Verify: Should fail with authorization error
        assert!(result.is_err(), "Should not allow non-creator to add group");

        // Verify: No participation was created
        let participation_exists: bool = sqlx::query_scalar(
            "SELECT EXISTS(SELECT 1 FROM appointment_groups WHERE appointment_id = $1 AND group_id = $2)"
        )
        .bind(appointment_id)
        .bind(group_id)
        .fetch_one(db.pool())
        .await
        .expect("Failed to check group participation");

        assert!(!participation_exists, "No group participation should be created");

        // Verify: No event was fired
        let event_count = count_events(db.pool(), "AppointmentGroupAddedEvent").await;
        assert_eq!(event_count, 0, "No event should be fired on authorization failure");
    }

    /// NEGATIVE: Cannot add a deleted group
    #[tokio::test]
    async fn test_add_deleted_group_fails() {
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        // Setup: Create creator, group, and appointment
        let creator_id = fixtures
            .create_user("creator_deleted_group", "creator@example.com")
            .await
            .expect("Failed to create creator");

        let group_id = fixtures
            .create_group(creator_id, "Deleted Group")
            .await
            .expect("Failed to create group");

        let appointment_id = fixtures
            .create_appointment(creator_id, &Default::default())
            .await
            .expect("Failed to create appointment");

        // Soft-delete the group
        sqlx::query("UPDATE groups SET deleted_at = NOW() WHERE id = $1")
            .bind(group_id)
            .execute(db.pool())
            .await
            .expect("Failed to delete group");

        // Create service
        let repo = AppointmentRepository::new(db.pool().clone());
        let event_bus = Arc::new(PostgresEventBus::new(db.pool().clone()));
        let service = AppointmentService::with_events(repo, event_bus);

        // Action: Try to add deleted group
        let result = add_group_to_appointment(&service, creator_id, appointment_id, group_id, "ATTENDANT").await;

        // Verify: Should fail
        assert!(result.is_err(), "Should not allow adding deleted group");

        // Verify: No participation was created
        let participation_exists: bool = sqlx::query_scalar(
            "SELECT EXISTS(SELECT 1 FROM appointment_groups WHERE appointment_id = $1 AND group_id = $2)"
        )
        .bind(appointment_id)
        .bind(group_id)
        .fetch_one(db.pool())
        .await
        .expect("Failed to check group participation");

        assert!(!participation_exists, "No group participation should be created for deleted group");
    }

    /// NEGATIVE: Cannot add the same group twice
    #[tokio::test]
    async fn test_add_group_duplicate_fails() {
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        // Setup: Create creator, group, and appointment
        let creator_id = fixtures
            .create_user("creator_duplicate", "creator@example.com")
            .await
            .expect("Failed to create creator");

        let group_id = fixtures
            .create_group(creator_id, "Duplicate Test Group")
            .await
            .expect("Failed to create group");

        let appointment_id = fixtures
            .create_appointment(creator_id, &Default::default())
            .await
            .expect("Failed to create appointment");

        // Create service
        let repo = AppointmentRepository::new(db.pool().clone());
        let event_bus = Arc::new(PostgresEventBus::new(db.pool().clone()));
        let service = AppointmentService::with_events(repo, event_bus);

        // Action: Add group first time (should succeed)
        let result1 = add_group_to_appointment(&service, creator_id, appointment_id, group_id, "ATTENDANT").await;
        assert!(result1.is_ok(), "First add should succeed");

        // Action: Try to add same group again
        let result2 = add_group_to_appointment(&service, creator_id, appointment_id, group_id, "ATTENDANT").await;

        // Verify: Should fail with duplicate error
        assert!(result2.is_err(), "Should not allow duplicate group addition");
        assert!(result2.unwrap_err().to_lowercase().contains("already"),
                "Error should mention group already exists");
    }
}
