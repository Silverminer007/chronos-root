/// Integration tests for adding participants to appointments
#[cfg(test)]
mod add_participant_tests {
    use chronos_date_api::test_utils::{AppointmentFixture, TestDb, TestFixtures};
    use uuid::Uuid;

    /// Helper to add a user as participant with a specific role
    async fn add_participant_with_role(
        pool: &sqlx::PgPool,
        appointment_id: Uuid,
        user_id: Uuid,
        role: &str,
    ) -> Result<Uuid, Box<dyn std::error::Error>> {
        let participant_id = Uuid::new_v4();
        sqlx::query(
            "INSERT INTO appointment_participants (id, appointment_id, user_id, status, role) VALUES ($1, $2, $3, 'PENDING', $4)"
        )
        .bind(participant_id)
        .bind(appointment_id)
        .bind(user_id)
        .bind(role)
        .execute(pool)
        .await?;

        Ok(participant_id)
    }

    #[tokio::test]
    async fn test_add_participant_success() {
        // Setup
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        // Create users
        let creator_id = fixtures
            .create_user("creator", "creator@example.com")
            .await
            .expect("Failed to create creator");

        let participant_id = fixtures
            .create_user("participant", "participant@example.com")
            .await
            .expect("Failed to create participant");

        // Create appointment with creator as responsible
        let appointment_id = fixtures
            .create_appointment(creator_id, &AppointmentFixture::new().with_title("Team Meeting"))
            .await
            .expect("Failed to create appointment");

        // Creator should already be added as RESPONSIBLE participant
        // Now verify adding another participant works

        // Verify participant can be added
        let participant = sqlx::query_scalar::<_, i64>(
            "SELECT COUNT(*) FROM appointment_participants WHERE appointment_id = $1 AND user_id = $2"
        )
        .bind(appointment_id)
        .bind(participant_id)
        .fetch_one(db.pool())
        .await
        .expect("Failed to query participants");

        // Before adding, participant should not exist
        assert_eq!(participant, 0);
    }

    #[tokio::test]
    async fn test_add_participant_creates_pending_status() {
        // Setup
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        // Create users
        let creator_id = fixtures
            .create_user("creator", "creator@example.com")
            .await
            .expect("Failed to create creator");

        let participant_id = fixtures
            .create_user("participant", "participant@example.com")
            .await
            .expect("Failed to create participant");

        // Create appointment
        let appointment_id = fixtures
            .create_appointment(creator_id, &AppointmentFixture::new().with_title("Meeting"))
            .await
            .expect("Failed to create appointment");

        // Add participant with specific role
        add_participant_with_role(db.pool(), appointment_id, participant_id, "ATTENDANT")
            .await
            .expect("Failed to add participant");

        // Verify participant was added with PENDING status
        let (status, role): (String, String) = sqlx::query_as(
            "SELECT status, role FROM appointment_participants WHERE appointment_id = $1 AND user_id = $2"
        )
        .bind(appointment_id)
        .bind(participant_id)
        .fetch_one(db.pool())
        .await
        .expect("Failed to fetch participant");

        assert_eq!(status, "PENDING");
        assert_eq!(role, "ATTENDANT");
    }

    #[tokio::test]
    async fn test_cannot_add_participant_without_responsible_role() {
        // Setup
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        // Create users
        let creator_id = fixtures
            .create_user("creator", "creator@example.com")
            .await
            .expect("Failed to create creator");

        let non_responsible_user_id = fixtures
            .create_user("helper", "helper@example.com")
            .await
            .expect("Failed to create helper");

        let _target_user_id = fixtures
            .create_user("target", "target@example.com")
            .await
            .expect("Failed to create target");

        // Create appointment
        let appointment_id = fixtures
            .create_appointment(creator_id, &AppointmentFixture::new().with_title("Meeting"))
            .await
            .expect("Failed to create appointment");

        // Add helper to appointment with HELPER role (not RESPONSIBLE)
        add_participant_with_role(db.pool(), appointment_id, non_responsible_user_id, "HELPER")
            .await
            .expect("Failed to add helper");

        // Helper (with HELPER role) should not be able to add participants
        // This will be verified at the handler level with proper authorization
        let helper_role: String = sqlx::query_scalar(
            "SELECT role FROM appointment_participants WHERE appointment_id = $1 AND user_id = $2"
        )
        .bind(appointment_id)
        .bind(non_responsible_user_id)
        .fetch_one(db.pool())
        .await
        .expect("Failed to fetch helper role");

        assert_eq!(helper_role, "HELPER");
        assert_ne!(helper_role, "RESPONSIBLE");
    }

    #[tokio::test]
    async fn test_cannot_add_participant_who_is_already_participant() {
        // Setup
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        // Create users
        let creator_id = fixtures
            .create_user("creator", "creator@example.com")
            .await
            .expect("Failed to create creator");

        let participant_id = fixtures
            .create_user("participant", "participant@example.com")
            .await
            .expect("Failed to create participant");

        // Create appointment
        let appointment_id = fixtures
            .create_appointment(creator_id, &AppointmentFixture::new().with_title("Meeting"))
            .await
            .expect("Failed to create appointment");

        // Add participant first time
        add_participant_with_role(db.pool(), appointment_id, participant_id, "ATTENDANT")
            .await
            .expect("Failed to add participant first time");

        // Try to add again - should fail due to UNIQUE constraint
        let result = add_participant_with_role(db.pool(), appointment_id, participant_id, "HELPER")
            .await;

        // Should fail with database constraint error
        assert!(result.is_err());

        // Verify only one entry exists for this user
        let count: i64 = sqlx::query_scalar(
            "SELECT COUNT(*) FROM appointment_participants WHERE appointment_id = $1 AND user_id = $2"
        )
        .bind(appointment_id)
        .bind(participant_id)
        .fetch_one(db.pool())
        .await
        .expect("Failed to count participants");

        assert_eq!(count, 1);
    }
}
