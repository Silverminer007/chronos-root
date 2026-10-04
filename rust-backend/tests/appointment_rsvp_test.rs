/// Integration tests for appointment RSVP endpoint
#[cfg(test)]
mod appointment_rsvp_tests {
    use chronos_date_api::appointments::models::ParticipationStatus;
    use chronos_date_api::appointments::repository::AppointmentRepository;
    use chronos_date_api::appointments::services::AppointmentService;
    use chronos_date_api::test_utils::{AppointmentFixture, TestDb, TestFixtures};

    #[tokio::test]
    #[ignore]
    async fn test_rsvp_with_approved_status_success() {
        // Setup
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        // Create a test user (creator of the appointment)
        let creator_id = fixtures
            .create_user("keycloak_creator", "creator@example.com")
            .await
            .expect("Failed to create creator");

        // Create another user (participant)
        let participant_id = fixtures
            .create_user("keycloak_participant", "participant@example.com")
            .await
            .expect("Failed to create participant");

        // Create an appointment
        let appointment_fixture = AppointmentFixture::new()
            .with_title("Team Meeting")
            .with_description(Some("Monthly team sync"));

        let appointment_id = fixtures
            .create_appointment(creator_id, &appointment_fixture)
            .await
            .expect("Failed to create appointment");

        // Add participant with PENDING status
        fixtures
            .add_participant(appointment_id, participant_id, "PENDING")
            .await
            .expect("Failed to add participant");

        // Verify initial status is PENDING
        let initial_status: String = sqlx::query_scalar(
            "SELECT status FROM appointment_participants WHERE appointment_id = $1 AND user_id = $2"
        )
        .bind(appointment_id)
        .bind(participant_id)
        .fetch_one(db.pool())
        .await
        .expect("Failed to fetch initial status");

        assert_eq!(initial_status, "PENDING");

        // Create a service and call change_participation_status
        let repo = AppointmentRepository::new(db.pool().clone());
        let service = AppointmentService::new(repo);

        // Call the RSVP method
        service
            .change_participation_status(appointment_id, participant_id, ParticipationStatus::Approved)
            .await
            .expect("Failed to change participation status");

        // Verify status is now APPROVED
        let updated_status: String = sqlx::query_scalar(
            "SELECT status FROM appointment_participants WHERE appointment_id = $1 AND user_id = $2"
        )
        .bind(appointment_id)
        .bind(participant_id)
        .fetch_one(db.pool())
        .await
        .expect("Failed to fetch updated status");

        assert_eq!(updated_status, "APPROVED");
    }

    #[tokio::test]
    #[ignore]
    async fn test_rsvp_with_rejected_status_success() {
        // Setup
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        // Create users
        let creator_id = fixtures
            .create_user("keycloak_creator2", "creator2@example.com")
            .await
            .expect("Failed to create creator");

        let participant_id = fixtures
            .create_user("keycloak_participant2", "participant2@example.com")
            .await
            .expect("Failed to create participant");

        // Create appointment and add participant
        let appointment_fixture = AppointmentFixture::new().with_title("Team Meeting 2");
        let appointment_id = fixtures
            .create_appointment(creator_id, &appointment_fixture)
            .await
            .expect("Failed to create appointment");

        fixtures
            .add_participant(appointment_id, participant_id, "PENDING")
            .await
            .expect("Failed to add participant");

        // Create a service and call change_participation_status with REJECTED
        let repo = AppointmentRepository::new(db.pool().clone());
        let service = AppointmentService::new(repo);

        // Call the RSVP method with REJECTED status
        service
            .change_participation_status(appointment_id, participant_id, ParticipationStatus::Rejected)
            .await
            .expect("Failed to change participation status");

        // Verify status is now REJECTED
        let updated_status: String = sqlx::query_scalar(
            "SELECT status FROM appointment_participants WHERE appointment_id = $1 AND user_id = $2"
        )
        .bind(appointment_id)
        .bind(participant_id)
        .fetch_one(db.pool())
        .await
        .expect("Failed to fetch updated status");

        assert_eq!(updated_status, "REJECTED");
    }

    #[tokio::test]
    #[ignore]
    async fn test_rsvp_user_not_participant_fails() {
        // Setup
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        // Create users
        let creator_id = fixtures
            .create_user("keycloak_creator3", "creator3@example.com")
            .await
            .expect("Failed to create creator");

        let non_participant_id = fixtures
            .create_user("keycloak_non_participant", "non_participant@example.com")
            .await
            .expect("Failed to create non-participant user");

        // Create appointment (no participants)
        let appointment_fixture = AppointmentFixture::new().with_title("Team Meeting 3");
        let appointment_id = fixtures
            .create_appointment(creator_id, &appointment_fixture)
            .await
            .expect("Failed to create appointment");

        // Create a service and try to change participation status for non-participant
        let repo = AppointmentRepository::new(db.pool().clone());
        let service = AppointmentService::new(repo);

        // This should fail because the user is not a participant
        let result = service
            .change_participation_status(appointment_id, non_participant_id, ParticipationStatus::Approved)
            .await;

        // Verify that the error is as expected
        assert!(result.is_err());
        match result {
            Err(chronos_date_api::appointments::services::ServiceError::ValidationError(msg)) => {
                assert!(msg.contains("not a participant"));
            }
            _ => panic!("Expected ValidationError with 'not a participant' message"),
        }
    }
}
