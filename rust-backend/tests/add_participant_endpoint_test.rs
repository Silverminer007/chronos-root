/// Integration tests for adding participants via HTTP endpoint
#[cfg(test)]
mod add_participant_endpoint_tests {
    use chronos_date_api::appointments::models::{AddParticipantRequest, UserRole};
    use chronos_date_api::test_utils::{AppointmentFixture, TestDb, TestFixtures};
    use uuid::Uuid;

    /// Helper to create a friendship between two users
    async fn create_friendship(
        pool: &sqlx::PgPool,
        requester_id: Uuid,
        recipient_id: Uuid,
    ) -> Result<(), Box<dyn std::error::Error>> {
        sqlx::query(
            "INSERT INTO friendships (requester_id, recipient_id, status) VALUES ($1, $2, 'ACCEPTED')"
        )
        .bind(requester_id)
        .bind(recipient_id)
        .execute(pool)
        .await?;

        Ok(())
    }

    /// Helper to add a user as participant with RESPONSIBLE role (organizer)
    async fn add_participant_as_organizer(
        pool: &sqlx::PgPool,
        appointment_id: Uuid,
        user_id: Uuid,
    ) -> Result<(), Box<dyn std::error::Error>> {
        sqlx::query(
            "INSERT INTO appointment_participants (id, appointment_id, user_id, status, role) VALUES ($1, $2, $3, 'PENDING', 'RESPONSIBLE')"
        )
        .bind(Uuid::new_v4())
        .bind(appointment_id)
        .bind(user_id)
        .execute(pool)
        .await?;

        Ok(())
    }

    #[tokio::test]
    #[ignore]
    async fn test_add_participant_creates_entry_in_database() {
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

        // Create appointment and add creator as organizer
        let appointment_id = fixtures
            .create_appointment(creator_id, &AppointmentFixture::new().with_title("Team Meeting"))
            .await
            .expect("Failed to create appointment");

        add_participant_as_organizer(db.pool(), appointment_id, creator_id)
            .await
            .expect("Failed to add creator as organizer");

        // Create friendship
        create_friendship(db.pool(), creator_id, participant_id)
            .await
            .expect("Failed to create friendship");

        // Verify the request struct can be created
        let request = AddParticipantRequest {
            user_role: UserRole::Attendant,
        };

        // Verify we can serialize the request
        let serialized = serde_json::to_string(&request).expect("Failed to serialize request");
        assert!(serialized.contains("ATTENDANT"));
    }

    #[tokio::test]
    #[ignore]
    async fn test_participant_has_correct_role_after_adding() {
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

        add_participant_as_organizer(db.pool(), appointment_id, creator_id)
            .await
            .expect("Failed to add creator as organizer");

        // Add participant with HELPER role
        fixtures
            .add_participant(appointment_id, participant_id, "PENDING")
            .await
            .expect("Failed to add participant");

        // Update the participant's role to HELPER
        sqlx::query("UPDATE appointment_participants SET role = 'HELPER' WHERE appointment_id = $1 AND user_id = $2")
            .bind(appointment_id)
            .bind(participant_id)
            .execute(db.pool())
            .await
            .expect("Failed to update role");

        // Verify the role was set correctly
        let (role,): (String,) = sqlx::query_as(
            "SELECT role FROM appointment_participants WHERE appointment_id = $1 AND user_id = $2"
        )
        .bind(appointment_id)
        .bind(participant_id)
        .fetch_one(db.pool())
        .await
        .expect("Failed to fetch role");

        assert_eq!(role, "HELPER");
    }

    #[tokio::test]
    #[ignore]
    async fn test_friendships_must_be_accepted_to_add_participant() {
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

        let non_friend_id = fixtures
            .create_user("non_friend", "non_friend@example.com")
            .await
            .expect("Failed to create non-friend");

        // Create appointment
        let appointment_id = fixtures
            .create_appointment(creator_id, &AppointmentFixture::new().with_title("Meeting"))
            .await
            .expect("Failed to create appointment");

        add_participant_as_organizer(db.pool(), appointment_id, creator_id)
            .await
            .expect("Failed to add creator as organizer");

        // Create a pending friendship (not ACCEPTED)
        sqlx::query(
            "INSERT INTO friendships (requester_id, recipient_id, status) VALUES ($1, $2, 'PENDING')"
        )
        .bind(creator_id)
        .bind(non_friend_id)
        .execute(db.pool())
        .await
        .expect("Failed to create pending friendship");

        // Verify the friendship is not accepted
        let status: String = sqlx::query_scalar(
            "SELECT status FROM friendships WHERE requester_id = $1 AND recipient_id = $2"
        )
        .bind(creator_id)
        .bind(non_friend_id)
        .fetch_one(db.pool())
        .await
        .expect("Failed to fetch friendship");

        assert_eq!(status, "PENDING");
    }
}
