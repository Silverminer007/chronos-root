/// Integration tests for appointment creation and database verification
#[cfg(test)]
mod integration_tests {
    use chronos_date_api::appointments::models::Appointment;
    use chronos_date_api::test_utils::{AppointmentFixture, TestAuthHelper, TestDb, TestFixtures};
    use uuid::Uuid;

    #[tokio::test]
    #[ignore]
    async fn test_create_appointment_and_verify_in_db() {
        // Setup
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        // Create a test user (creator of the appointment)
        let creator_id = fixtures
            .create_user("keycloak_user_1", "creator@example.com")
            .await
            .expect("Failed to create test user");

        // Create an appointment using fixture
        let appointment_fixture = AppointmentFixture::new()
            .with_title("Team Standup")
            .with_description(Some("Daily team standup meeting"))
            .with_location(Some("Conference Room A"));

        let appointment_id = fixtures
            .create_appointment(creator_id, &appointment_fixture)
            .await
            .expect("Failed to create appointment");

        // Verify: Query the appointment from the database
        let fetched_appointment: Appointment = sqlx::query_as(
            "SELECT id, title, description, start_time, end_time, location, creator_id, created_at, updated_at
             FROM appointments WHERE id = $1"
        )
        .bind(appointment_id)
        .fetch_one(db.pool())
        .await
        .expect("Failed to fetch appointment from database");

        // Assert
        assert_eq!(fetched_appointment.id, appointment_id);
        assert_eq!(fetched_appointment.title, "Team Standup");
        assert_eq!(
            fetched_appointment.description,
            Some("Daily team standup meeting".to_string())
        );
        assert_eq!(
            fetched_appointment.location,
            Some("Conference Room A".to_string())
        );
        assert_eq!(fetched_appointment.creator_id, creator_id);
        assert_eq!(
            fetched_appointment.end_time > fetched_appointment.start_time,
            true
        );
    }

    #[tokio::test]
    #[ignore]
    async fn test_create_multiple_users_and_appointments() {
        // Setup
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        // Create multiple test users
        let user1_id = fixtures
            .create_user("user_1", "user1@example.com")
            .await
            .expect("Failed to create user 1");

        let user2_id = fixtures
            .create_user("user_2", "user2@example.com")
            .await
            .expect("Failed to create user 2");

        // Create appointments by both users
        let appt1_id = fixtures
            .create_appointment(
                user1_id,
                &AppointmentFixture::new().with_title("User 1 Meeting"),
            )
            .await
            .expect("Failed to create appointment 1");

        let appt2_id = fixtures
            .create_appointment(
                user2_id,
                &AppointmentFixture::new().with_title("User 2 Meeting"),
            )
            .await
            .expect("Failed to create appointment 2");

        // Verify: Query all appointments
        let appointments: Vec<Appointment> = sqlx::query_as(
            "SELECT id, title, description, start_time, end_time, location, creator_id, created_at, updated_at
             FROM appointments ORDER BY created_at"
        )
        .fetch_all(db.pool())
        .await
        .expect("Failed to fetch appointments");

        // Assert
        assert_eq!(appointments.len(), 2);
        assert_eq!(appointments[0].creator_id, user1_id);
        assert_eq!(appointments[1].creator_id, user2_id);
        assert_eq!(appointments[0].title, "User 1 Meeting");
        assert_eq!(appointments[1].title, "User 2 Meeting");
    }

    #[tokio::test]
    #[ignore]
    async fn test_add_appointment_participants() {
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

        let participant1_id = fixtures
            .create_user("participant1", "participant1@example.com")
            .await
            .expect("Failed to create participant 1");

        let participant2_id = fixtures
            .create_user("participant2", "participant2@example.com")
            .await
            .expect("Failed to create participant 2");

        // Create appointment
        let appointment_id = fixtures
            .create_appointment(
                creator_id,
                &AppointmentFixture::new().with_title("Team Workshop"),
            )
            .await
            .expect("Failed to create appointment");

        // Add participants
        let p1_status = fixtures
            .add_participant(appointment_id, participant1_id, "ACCEPTED")
            .await
            .expect("Failed to add participant 1");

        let p2_status = fixtures
            .add_participant(appointment_id, participant2_id, "PENDING")
            .await
            .expect("Failed to add participant 2");

        // Verify: Query participants
        #[derive(sqlx::FromRow)]
        struct Participant {
            id: Uuid,
            appointment_id: Uuid,
            user_id: Uuid,
            status: String,
        }

        let participants: Vec<Participant> = sqlx::query_as(
            "SELECT id, appointment_id, user_id, status FROM appointment_participants
             WHERE appointment_id = $1 ORDER BY created_at",
        )
        .bind(appointment_id)
        .fetch_all(db.pool())
        .await
        .expect("Failed to fetch participants");

        // Assert
        assert_eq!(participants.len(), 2);
        assert_eq!(participants[0].user_id, participant1_id);
        assert_eq!(participants[0].status, "ACCEPTED");
        assert_eq!(participants[1].user_id, participant2_id);
        assert_eq!(participants[1].status, "PENDING");
    }

    #[tokio::test]
    #[ignore]
    async fn test_transaction_rollback() {
        // Setup
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        // Create initial user
        let user_id = fixtures
            .create_user("user", "user@example.com")
            .await
            .expect("Failed to create user");

        // Verify user exists
        let count_before: (i64,) = sqlx::query_as("SELECT COUNT(*) FROM users")
            .fetch_one(db.pool())
            .await
            .expect("Failed to count users");
        assert_eq!(count_before.0, 1);

        // Rollback all data
        db.rollback_all()
            .await
            .expect("Failed to rollback all data");

        // Verify all data is gone
        let count_after: (i64,) = sqlx::query_as("SELECT COUNT(*) FROM users")
            .fetch_one(db.pool())
            .await
            .expect("Failed to count users after rollback");
        assert_eq!(count_after.0, 0);
    }

    #[test]
    fn test_auth_helper_token_creation() {
        let auth_helper = TestAuthHelper::new();
        let token = auth_helper
            .create_token("test-user-123")
            .expect("Failed to create token");

        assert!(!token.is_empty());
        let parts: Vec<&str> = token.split('.').collect();
        assert_eq!(parts.len(), 3, "JWT should have 3 parts");
    }

    #[test]
    fn test_auth_helper_authorization_header() {
        let auth_helper = TestAuthHelper::new();
        let header = auth_helper
            .create_auth_header("test-user-456")
            .expect("Failed to create auth header");

        assert!(header.starts_with("Bearer "));
        assert!(header.len() > "Bearer ".len());
    }

    #[test]
    fn test_appointment_fixture_builder() {
        let fixture = AppointmentFixture::new()
            .with_title("Test")
            .with_location(Some("Room"));

        assert_eq!(fixture.title, "Test");
        assert_eq!(fixture.location, Some("Room".to_string()));
    }
}
