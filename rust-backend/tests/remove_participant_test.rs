/// Integration tests for removing appointment participants
/// Tests the DELETE /api/v2/appointments/:id/participants/:userId endpoint
#[cfg(test)]
mod remove_participant_tests {
    use chronos_date_api::appointments::repository::AppointmentRepository;
    use chronos_date_api::appointments::services::AppointmentService;
    use chronos_date_api::event_bus::postgres::PostgresEventBus;
    use chronos_date_api::test_utils::{TestDb, TestFixtures};
    use std::sync::Arc;

    /// Helper to count events of a specific type in the events table
    async fn count_events(pool: &sqlx::PgPool, event_type: &str) -> i64 {
        sqlx::query_scalar::<_, i64>("SELECT COUNT(*) FROM events WHERE event_type = $1")
            .bind(event_type)
            .fetch_one(pool)
            .await
            .unwrap_or(0)
    }

    /// POSITIVE: Successfully remove a participant from an appointment
    #[tokio::test]
    async fn test_remove_participant_success() {
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        // Setup: Create creator and participant users
        let creator_id = fixtures
            .create_user("creator_remove_test", "creator@example.com")
            .await
            .expect("Failed to create creator");

        let participant_id = fixtures
            .create_user("participant_remove_test", "participant@example.com")
            .await
            .expect("Failed to create participant");

        // Create appointment
        let appointment_fixture = chronos_date_api::test_utils::AppointmentFixture::new()
            .with_title("Team Meeting");
        let appt_id = fixtures
            .create_appointment(creator_id, &appointment_fixture)
            .await
            .expect("Failed to create appointment");

        // Add participant to appointment
        let repo = AppointmentRepository::new(db.pool().clone());
        repo.add_participant(appt_id, participant_id, "ATTENDANT", "APPROVED")
            .await
            .expect("Failed to add participant");

        // Verify participant was added
        let is_participant_before = repo
            .is_participant(appt_id, participant_id)
            .await
            .expect("Failed to check participant status");
        assert!(is_participant_before, "Participant should be added before removal");

        // Remove participant
        repo.remove_participant(appt_id, participant_id)
            .await
            .expect("Failed to remove participant");

        // Verify participant was removed
        let is_participant_after = repo
            .is_participant(appt_id, participant_id)
            .await
            .expect("Failed to check participant status");
        assert!(
            !is_participant_after,
            "Participant should be removed after deletion"
        );
    }

    /// POSITIVE: AppointmentParticipationRemovedEvent is fired after removing a participant
    #[tokio::test]
    async fn test_remove_participant_fires_event() {
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        // Setup: Create creator and participant users
        let creator_id = fixtures
            .create_user("creator_event_test", "creator@example.com")
            .await
            .expect("Failed to create creator");

        let participant_id = fixtures
            .create_user("participant_event_test", "participant@example.com")
            .await
            .expect("Failed to create participant");

        // Create appointment
        let appointment_fixture = chronos_date_api::test_utils::AppointmentFixture::new()
            .with_title("Team Meeting");
        let appt_id = fixtures
            .create_appointment(creator_id, &appointment_fixture)
            .await
            .expect("Failed to create appointment");

        // Add participant to appointment
        let repo = AppointmentRepository::new(db.pool().clone());
        repo.add_participant(appt_id, participant_id, "ATTENDANT", "APPROVED")
            .await
            .expect("Failed to add participant");

        // Clear any setup events
        sqlx::query("DELETE FROM events WHERE event_type = 'AppointmentParticipationRemovedEvent'")
            .execute(db.pool())
            .await
            .ok();

        // Verify: No AppointmentParticipationRemovedEvent exists yet
        let initial_count = count_events(db.pool(), "AppointmentParticipationRemovedEvent").await;
        assert_eq!(initial_count, 0);

        // Remove participant using service with events
        let event_bus = Arc::new(PostgresEventBus::new(db.pool().clone()));
        let service = AppointmentService::with_events(repo, event_bus);

        service
            .remove_participant(appt_id, participant_id, creator_id)
            .await
            .expect("Failed to remove participant");

        // Verify: AppointmentParticipationRemovedEvent was fired
        let final_count = count_events(db.pool(), "AppointmentParticipationRemovedEvent").await;
        assert_eq!(
            final_count, 1,
            "AppointmentParticipationRemovedEvent should be fired after participant removal"
        );

        // Verify event payload contains correct data
        let event: (String, String) = sqlx::query_as(
            "SELECT event_type, payload FROM events WHERE event_type = 'AppointmentParticipationRemovedEvent' LIMIT 1"
        )
        .fetch_one(db.pool())
        .await
        .expect("Failed to fetch event");

        assert_eq!(event.0, "AppointmentParticipationRemovedEvent");
        let payload = serde_json::from_str::<serde_json::Value>(&event.1)
            .expect("Invalid event payload JSON");
        assert!(payload["appointment_id"].is_string());
        assert!(payload["target_user_id"].is_string());
        assert!(payload["acting_user_id"].is_string());
        assert!(payload["timestamp"].is_number());
    }

    /// NEGATIVE: Unauthorized user cannot remove participants (non-creator)
    #[tokio::test]
    async fn test_remove_participant_unauthorized_non_creator() {
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        // Setup: Create creator, participant, and another user (non-creator)
        let creator_id = fixtures
            .create_user("creator_unauth", "creator@example.com")
            .await
            .expect("Failed to create creator");

        let participant_id = fixtures
            .create_user("participant_unauth", "participant@example.com")
            .await
            .expect("Failed to create participant");

        let other_user_id = fixtures
            .create_user("other_user_unauth", "other@example.com")
            .await
            .expect("Failed to create other user");

        // Create appointment
        let appointment_fixture = chronos_date_api::test_utils::AppointmentFixture::new()
            .with_title("Team Meeting");
        let appt_id = fixtures
            .create_appointment(creator_id, &appointment_fixture)
            .await
            .expect("Failed to create appointment");

        // Add participant to appointment
        let repo = AppointmentRepository::new(db.pool().clone());
        repo.add_participant(appt_id, participant_id, "ATTENDANT", "APPROVED")
            .await
            .expect("Failed to add participant");

        // Attempt to remove participant as non-creator should fail
        let event_bus = Arc::new(PostgresEventBus::new(db.pool().clone()));
        let service = AppointmentService::with_events(repo, event_bus);

        let result = service
            .remove_participant(appt_id, participant_id, other_user_id)
            .await;

        assert!(
            result.is_err(),
            "Non-creator should not be able to remove participants"
        );
    }

    /// NEGATIVE: Cannot remove participant if appointment doesn't exist
    #[tokio::test]
    async fn test_remove_participant_appointment_not_found() {
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        // Setup: Create users
        let creator_id = fixtures
            .create_user("creator_notfound", "creator@example.com")
            .await
            .expect("Failed to create creator");

        let participant_id = fixtures
            .create_user("participant_notfound", "participant@example.com")
            .await
            .expect("Failed to create participant");

        // Try to remove participant from non-existent appointment
        let repo = AppointmentRepository::new(db.pool().clone());
        let event_bus = Arc::new(PostgresEventBus::new(db.pool().clone()));
        let service = AppointmentService::with_events(repo, event_bus);

        let fake_appt_id = uuid::Uuid::new_v4();
        let result = service
            .remove_participant(fake_appt_id, participant_id, creator_id)
            .await;

        assert!(
            result.is_err(),
            "Should return error when appointment doesn't exist"
        );
    }

    /// NEGATIVE: Cannot remove participant if they are not a participant
    #[tokio::test]
    async fn test_remove_participant_not_a_participant() {
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        // Setup: Create creator and two users (one not added to appointment)
        let creator_id = fixtures
            .create_user("creator_notparticipant", "creator@example.com")
            .await
            .expect("Failed to create creator");

        let non_participant_id = fixtures
            .create_user("non_participant", "nonparticipant@example.com")
            .await
            .expect("Failed to create non-participant");

        // Create appointment
        let appointment_fixture = chronos_date_api::test_utils::AppointmentFixture::new()
            .with_title("Team Meeting");
        let appt_id = fixtures
            .create_appointment(creator_id, &appointment_fixture)
            .await
            .expect("Failed to create appointment");

        // Try to remove user who is not a participant
        let repo = AppointmentRepository::new(db.pool().clone());
        let event_bus = Arc::new(PostgresEventBus::new(db.pool().clone()));
        let service = AppointmentService::with_events(repo, event_bus);

        let result = service
            .remove_participant(appt_id, non_participant_id, creator_id)
            .await;

        assert!(
            result.is_err(),
            "Should return error when user is not a participant"
        );
    }
}
