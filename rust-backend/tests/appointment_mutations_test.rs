/// Integration tests for appointment mutations (create, update, delete, cancel)
/// Tests verify both positive cases (events fire) and negative cases (events don't fire on auth failure)
#[cfg(test)]
mod appointment_mutation_tests {
    use chronos_date_api::appointments::models::{CreateAppointmentRequest, UpdateAppointmentRequest};
    use chronos_date_api::appointments::services::AppointmentService;
    use chronos_date_api::appointments::repository::AppointmentRepository;
    use chronos_date_api::event_bus::postgres::PostgresEventBus;
    use chronos_date_api::test_utils::{TestDb, TestFixtures};
    use uuid::Uuid;
    use std::sync::Arc;

    /// Helper to count events of a specific type in the events table
    async fn count_events(pool: &sqlx::PgPool, event_type: &str) -> i64 {
        sqlx::query_scalar::<_, i64>(
            "SELECT COUNT(*) FROM events WHERE event_type = $1"
        )
        .bind(event_type)
        .fetch_one(pool)
        .await
        .unwrap_or(0)
    }

    /// POSITIVE: AppointmentCreatedEvent is fired after successful appointment creation
    #[tokio::test]
    #[ignore]
    async fn test_create_appointment_fires_event() {
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        // Create creator user
        let creator_id = fixtures
            .create_user("creator_create_test", "creator@example.com")
            .await
            .expect("Failed to create creator");

        // Verify: No AppointmentCreatedEvent exists yet
        let initial_count = count_events(db.pool(), "AppointmentCreatedEvent").await;
        assert_eq!(initial_count, 0);

        // Create appointment with event publisher
        let repo = AppointmentRepository::new(db.pool().clone());
        let event_bus = Arc::new(PostgresEventBus::new(db.pool().clone()));
        let service = AppointmentService::with_events(repo, event_bus);

        let request = CreateAppointmentRequest {
            name: "Team Meeting".to_string(),
            description: Some("Sync meeting".to_string()),
            venue: Some("Room A".to_string()),
            start: "2025-09-20T10:00:00Z".to_string(),
            end: "2025-09-20T11:00:00Z".to_string(),
            minimal_attendees: Some(3),
        };

        let result = service.create_appointment(request, creator_id.to_string()).await;
        assert!(result.is_ok(), "Failed to create appointment");

        // Verify: AppointmentCreatedEvent was fired and persisted
        let final_count = count_events(db.pool(), "AppointmentCreatedEvent").await;
        assert_eq!(
            final_count, 1,
            "AppointmentCreatedEvent should be fired after creation"
        );

        // Verify event payload contains correct data
        let event: (String, String) = sqlx::query_as(
            "SELECT event_type, payload FROM events WHERE event_type = 'AppointmentCreatedEvent' LIMIT 1"
        )
        .fetch_one(db.pool())
        .await
        .expect("Failed to fetch event");

        assert_eq!(event.0, "AppointmentCreatedEvent");
        let payload = serde_json::from_str::<serde_json::Value>(&event.1)
            .expect("Invalid event payload JSON");
        assert!(payload["appointment_id"].is_string());
        assert!(payload["creator_id"].is_string());
        assert!(payload["timestamp"].is_number());
    }

    /// POSITIVE: AppointmentEditedEvent is fired after successful appointment edit
    #[tokio::test]
    #[ignore]
    async fn test_update_appointment_fires_edited_event() {
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        // Setup: Create creator and appointment
        let creator_id = fixtures
            .create_user("creator_edit_test", "creator@example.com")
            .await
            .expect("Failed to create creator");

        let repo = AppointmentRepository::new(db.pool().clone());
        let event_bus = Arc::new(PostgresEventBus::new(db.pool().clone()));

        let appointment_fixture = chronos_date_api::test_utils::AppointmentFixture::new()
            .with_title("Original Title")
            .with_description(Some("Original desc"))
            .with_location(Some("Room A"));

        let appt_id = fixtures
            .create_appointment(creator_id, &appointment_fixture)
            .await
            .expect("Failed to create appointment");

        // Clear any setup events
        sqlx::query("DELETE FROM events WHERE event_type = 'AppointmentEditedEvent'")
            .execute(db.pool())
            .await
            .ok();

        // Verify: No AppointmentEditedEvent exists yet
        let initial_count = count_events(db.pool(), "AppointmentEditedEvent").await;
        assert_eq!(initial_count, 0);

        // Update appointment
        let service = AppointmentService::with_events(repo, event_bus);
        let request = UpdateAppointmentRequest {
            name: Some("Updated Title".to_string()),
            description: Some("Updated desc".to_string()),
            venue: None,
            start: None,
            end: None,
            minimal_attendees: None,
        };

        let result = service.update_appointment(appt_id, request).await;
        assert!(result.is_ok(), "Failed to update appointment");

        // Verify: AppointmentEditedEvent was fired
        let final_count = count_events(db.pool(), "AppointmentEditedEvent").await;
        assert_eq!(
            final_count, 1,
            "AppointmentEditedEvent should be fired after edit"
        );
    }

    /// POSITIVE: AppointmentMovedEvent is fired when start/end times change
    #[tokio::test]
    #[ignore]
    async fn test_update_appointment_fires_moved_event() {
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        // Setup: Create creator and appointment
        let creator_id = fixtures
            .create_user("creator_move_test", "creator@example.com")
            .await
            .expect("Failed to create creator");

        let repo = AppointmentRepository::new(db.pool().clone());
        let event_bus = Arc::new(PostgresEventBus::new(db.pool().clone()));

        let appointment_fixture = chronos_date_api::test_utils::AppointmentFixture::new()
            .with_title("Meeting");

        let appt_id = fixtures
            .create_appointment(creator_id, &appointment_fixture)
            .await
            .expect("Failed to create appointment");

        // Clear setup events
        sqlx::query("DELETE FROM events WHERE event_type = 'AppointmentMovedEvent'")
            .execute(db.pool())
            .await
            .ok();

        // Verify: No AppointmentMovedEvent exists yet
        let initial_count = count_events(db.pool(), "AppointmentMovedEvent").await;
        assert_eq!(initial_count, 0);

        // Update appointment with new times
        let service = AppointmentService::with_events(repo, event_bus);
        let request = UpdateAppointmentRequest {
            name: None,
            description: None,
            venue: None,
            start: Some("2025-09-21T14:00:00Z".to_string()),
            end: Some("2025-09-21T15:00:00Z".to_string()),
            minimal_attendees: None,
        };

        let result = service.update_appointment(appt_id, request).await;
        assert!(result.is_ok(), "Failed to update appointment");

        // Verify: AppointmentMovedEvent was fired
        let final_count = count_events(db.pool(), "AppointmentMovedEvent").await;
        assert_eq!(
            final_count, 1,
            "AppointmentMovedEvent should be fired when times change"
        );
    }

    /// POSITIVE: AppointmentDeletedEvent is fired after soft delete
    #[tokio::test]
    #[ignore]
    async fn test_delete_appointment_fires_event() {
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        // Setup: Create creator and appointment
        let creator_id = fixtures
            .create_user("creator_delete_test", "creator@example.com")
            .await
            .expect("Failed to create creator");

        let repo = AppointmentRepository::new(db.pool().clone());
        let event_bus = Arc::new(PostgresEventBus::new(db.pool().clone()));

        let appointment_fixture = chronos_date_api::test_utils::AppointmentFixture::new()
            .with_title("To Delete");

        let appt_id = fixtures
            .create_appointment(creator_id, &appointment_fixture)
            .await
            .expect("Failed to create appointment");

        // Clear setup events
        sqlx::query("DELETE FROM events WHERE event_type = 'AppointmentDeletedEvent'")
            .execute(db.pool())
            .await
            .ok();

        // Verify: No AppointmentDeletedEvent exists yet
        let initial_count = count_events(db.pool(), "AppointmentDeletedEvent").await;
        assert_eq!(initial_count, 0);

        // Delete appointment
        let service = AppointmentService::with_events(repo, event_bus);
        let result = service.delete_appointment(appt_id).await;
        assert!(result.is_ok(), "Failed to delete appointment");

        // Verify: AppointmentDeletedEvent was fired
        let final_count = count_events(db.pool(), "AppointmentDeletedEvent").await;
        assert_eq!(
            final_count, 1,
            "AppointmentDeletedEvent should be fired after delete"
        );
    }

    /// POSITIVE: AppointmentCancelledEvent is fired after soft cancel
    #[tokio::test]
    #[ignore]
    async fn test_cancel_appointment_fires_event() {
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        // Setup: Create creator and appointment
        let creator_id = fixtures
            .create_user("creator_cancel_test", "creator@example.com")
            .await
            .expect("Failed to create creator");

        let repo = AppointmentRepository::new(db.pool().clone());
        let event_bus = Arc::new(PostgresEventBus::new(db.pool().clone()));

        let appointment_fixture = chronos_date_api::test_utils::AppointmentFixture::new()
            .with_title("To Cancel");

        let appt_id = fixtures
            .create_appointment(creator_id, &appointment_fixture)
            .await
            .expect("Failed to create appointment");

        // Clear setup events
        sqlx::query("DELETE FROM events WHERE event_type = 'AppointmentCancelledEvent'")
            .execute(db.pool())
            .await
            .ok();

        // Verify: No AppointmentCancelledEvent exists yet
        let initial_count = count_events(db.pool(), "AppointmentCancelledEvent").await;
        assert_eq!(initial_count, 0);

        // Cancel appointment
        let service = AppointmentService::with_events(repo, event_bus);
        let result = service.cancel_appointment(appt_id).await;
        assert!(result.is_ok(), "Failed to cancel appointment");

        // Verify: AppointmentCancelledEvent was fired
        let final_count = count_events(db.pool(), "AppointmentCancelledEvent").await;
        assert_eq!(
            final_count, 1,
            "AppointmentCancelledEvent should be fired after cancel"
        );
    }

    /// NEGATIVE: Event is NOT fired when update fails (non-existent appointment)
    #[tokio::test]
    #[ignore]
    async fn test_update_nonexistent_appointment_does_not_fire_event() {
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");

        let repo = AppointmentRepository::new(db.pool().clone());
        let event_bus = Arc::new(PostgresEventBus::new(db.pool().clone()));
        let service = AppointmentService::with_events(repo, event_bus);

        // Attempt to update non-existent appointment
        let fake_id = Uuid::new_v4();
        let request = UpdateAppointmentRequest {
            name: Some("Won't work".to_string()),
            description: None,
            venue: None,
            start: None,
            end: None,
            minimal_attendees: None,
        };

        let result = service.update_appointment(fake_id, request).await;
        assert!(result.is_err(), "Update should fail for non-existent appointment");

        // Verify: No event was fired
        let count = count_events(db.pool(), "AppointmentEditedEvent").await;
        assert_eq!(count, 0, "AppointmentEditedEvent should NOT fire on failed update");
    }

    /// NEGATIVE: Event is NOT fired when delete fails (non-existent appointment)
    #[tokio::test]
    #[ignore]
    async fn test_delete_nonexistent_appointment_does_not_fire_event() {
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");

        let repo = AppointmentRepository::new(db.pool().clone());
        let event_bus = Arc::new(PostgresEventBus::new(db.pool().clone()));
        let service = AppointmentService::with_events(repo, event_bus);

        // Attempt to delete non-existent appointment
        let fake_id = Uuid::new_v4();
        let result = service.delete_appointment(fake_id).await;
        assert!(result.is_err(), "Delete should fail for non-existent appointment");

        // Verify: No event was fired
        let count = count_events(db.pool(), "AppointmentDeletedEvent").await;
        assert_eq!(
            count, 0,
            "AppointmentDeletedEvent should NOT fire on failed delete"
        );
    }

    /// NEGATIVE: Event is NOT fired when cancel fails (non-existent appointment)
    #[tokio::test]
    #[ignore]
    async fn test_cancel_nonexistent_appointment_does_not_fire_event() {
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");

        let repo = AppointmentRepository::new(db.pool().clone());
        let event_bus = Arc::new(PostgresEventBus::new(db.pool().clone()));
        let service = AppointmentService::with_events(repo, event_bus);

        // Attempt to cancel non-existent appointment
        let fake_id = Uuid::new_v4();
        let result = service.cancel_appointment(fake_id).await;
        assert!(result.is_err(), "Cancel should fail for non-existent appointment");

        // Verify: No event was fired
        let count = count_events(db.pool(), "AppointmentCancelledEvent").await;
        assert_eq!(
            count, 0,
            "AppointmentCancelledEvent should NOT fire on failed cancel"
        );
    }

    /// NEGATIVE: Event is NOT fired when creation fails (validation: blank name)
    #[tokio::test]
    #[ignore]
    async fn test_create_appointment_with_blank_name_does_not_fire_event() {
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        let creator_id = fixtures
            .create_user("creator_validation_test", "creator@example.com")
            .await
            .expect("Failed to create creator");

        let repo = AppointmentRepository::new(db.pool().clone());
        let event_bus = Arc::new(PostgresEventBus::new(db.pool().clone()));
        let service = AppointmentService::with_events(repo, event_bus);

        // Attempt to create with blank name
        let request = CreateAppointmentRequest {
            name: "   ".to_string(), // Blank after trim
            description: None,
            venue: None,
            start: "2025-09-20T10:00:00Z".to_string(),
            end: "2025-09-20T11:00:00Z".to_string(),
            minimal_attendees: None,
        };

        let result = service.create_appointment(request, creator_id.to_string()).await;
        assert!(result.is_err(), "Creation should fail with blank name");

        // Verify: No event was fired
        let count = count_events(db.pool(), "AppointmentCreatedEvent").await;
        assert_eq!(
            count, 0,
            "AppointmentCreatedEvent should NOT fire on validation failure"
        );
    }

    /// NEGATIVE: Event is NOT fired when creation fails (validation: invalid timestamp)
    #[tokio::test]
    #[ignore]
    async fn test_create_appointment_with_invalid_timestamp_does_not_fire_event() {
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        let creator_id = fixtures
            .create_user("creator_ts_test", "creator@example.com")
            .await
            .expect("Failed to create creator");

        let repo = AppointmentRepository::new(db.pool().clone());
        let event_bus = Arc::new(PostgresEventBus::new(db.pool().clone()));
        let service = AppointmentService::with_events(repo, event_bus);

        // Attempt to create with invalid timestamp
        let request = CreateAppointmentRequest {
            name: "Meeting".to_string(),
            description: None,
            venue: None,
            start: "not-a-timestamp".to_string(),
            end: "2025-09-20T11:00:00Z".to_string(),
            minimal_attendees: None,
        };

        let result = service.create_appointment(request, creator_id.to_string()).await;
        assert!(result.is_err(), "Creation should fail with invalid timestamp");

        // Verify: No event was fired
        let count = count_events(db.pool(), "AppointmentCreatedEvent").await;
        assert_eq!(
            count, 0,
            "AppointmentCreatedEvent should NOT fire on validation failure"
        );
    }
}
