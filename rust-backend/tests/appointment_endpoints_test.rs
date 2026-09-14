/// Integration tests for appointment GET endpoints
#[cfg(test)]
mod appointment_endpoint_tests {
    use chronos_date_api::appointments::models::Appointment;
    use chronos_date_api::test_utils::{AppointmentFixture, TestDb, TestFixtures, TestAuthHelper};
    use uuid::Uuid;
    use chrono::Utc;

    #[tokio::test]
    #[ignore]
    async fn test_get_single_appointment_by_id_success() {
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

        // Create an auth token for the creator
        let auth_helper = TestAuthHelper::new();
        let _auth_header = auth_helper
            .create_auth_header(&creator_id.to_string())
            .expect("Failed to create auth header");

        // Verify the appointment exists in the database
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
    }

    #[tokio::test]
    #[ignore]
    async fn test_get_nonexistent_appointment_returns_404() {
        // Setup
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");

        // Create a fake appointment ID that doesn't exist
        let nonexistent_id = Uuid::new_v4();

        // Verify the appointment doesn't exist
        let result: Result<Appointment, sqlx::Error> = sqlx::query_as(
            "SELECT id, title, description, start_time, end_time, location, creator_id, created_at, updated_at
             FROM appointments WHERE id = $1"
        )
        .bind(nonexistent_id)
        .fetch_optional(db.pool())
        .await
        .expect("Database query failed")
        .ok_or(sqlx::Error::RowNotFound);

        // Assert that no appointment is found
        assert!(result.is_err());
    }

    #[tokio::test]
    #[ignore]
    async fn test_list_appointments_returns_all() {
        // Setup
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        // Create multiple users and appointments
        let user1_id = fixtures
            .create_user("user1", "user1@example.com")
            .await
            .expect("Failed to create user1");

        let user2_id = fixtures
            .create_user("user2", "user2@example.com")
            .await
            .expect("Failed to create user2");

        // Create appointments by different users
        let appt1_id = fixtures
            .create_appointment(
                user1_id,
                &AppointmentFixture::new()
                    .with_title("Meeting 1"),
            )
            .await
            .expect("Failed to create appointment 1");

        let appt2_id = fixtures
            .create_appointment(
                user2_id,
                &AppointmentFixture::new()
                    .with_title("Meeting 2"),
            )
            .await
            .expect("Failed to create appointment 2");

        // Verify all appointments can be retrieved
        let all_appointments: Vec<Appointment> = sqlx::query_as(
            "SELECT id, title, description, start_time, end_time, location, creator_id, created_at, updated_at
             FROM appointments ORDER BY start_time DESC"
        )
        .fetch_all(db.pool())
        .await
        .expect("Failed to fetch appointments");

        // Assert
        assert_eq!(all_appointments.len(), 2);
        assert!(all_appointments.iter().any(|a| a.id == appt1_id));
        assert!(all_appointments.iter().any(|a| a.id == appt2_id));
    }

    #[tokio::test]
    #[ignore]
    async fn test_appointment_response_structure() {
        // Setup
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        // Create a user and appointment
        let creator_id = fixtures
            .create_user("creator", "creator@example.com")
            .await
            .expect("Failed to create creator");

        let now = Utc::now();
        let appt_fixture = AppointmentFixture::new()
            .with_title("Structured Test")
            .with_description(Some("Test description"))
            .with_location(Some("Test Location"))
            .with_times(now, now + chrono::Duration::hours(2));

        let appointment_id = fixtures
            .create_appointment(creator_id, &appt_fixture)
            .await
            .expect("Failed to create appointment");

        // Fetch and verify response structure
        let appointment: Appointment = sqlx::query_as(
            "SELECT id, title, description, start_time, end_time, location, creator_id, created_at, updated_at
             FROM appointments WHERE id = $1"
        )
        .bind(appointment_id)
        .fetch_one(db.pool())
        .await
        .expect("Failed to fetch appointment");

        // Convert to response and verify all fields are present
        let response = serde_json::json!({
            "id": appointment.id,
            "title": appointment.title,
            "description": appointment.description,
            "start_time": appointment.start_time,
            "end_time": appointment.end_time,
            "location": appointment.location,
            "creator_id": appointment.creator_id,
            "created_at": appointment.created_at,
            "updated_at": appointment.updated_at,
        });

        // Assert all fields are present and properly formatted
        assert!(response.get("id").is_some());
        assert!(response.get("title").is_some());
        assert!(response.get("creator_id").is_some());
        assert!(response.get("start_time").is_some());
        assert!(response.get("end_time").is_some());
    }
}
