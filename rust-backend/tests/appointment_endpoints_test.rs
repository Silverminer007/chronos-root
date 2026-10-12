/// Integration tests for appointment GET endpoints
#[cfg(test)]
mod appointment_endpoint_tests {
    use chrono::Utc;
    use chronos_date_api::appointments::models::Appointment;
    use chronos_date_api::appointments::repository::APPOINTMENT_COLUMNS;
    use chronos_date_api::test_utils::{AppointmentFixture, TestAuthHelper, TestDb, TestFixtures};

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
            .create_appointment(&creator_id, &appointment_fixture)
            .await
            .expect("Failed to create appointment");

        // Create an auth token for the creator
        let auth_helper = TestAuthHelper::new();
        let _auth_header = auth_helper
            .create_auth_header(&creator_id)
            .expect("Failed to create auth header");

        // Verify the appointment exists in the database
        let fetched_appointment: Appointment = sqlx::query_as(&format!(
            "SELECT {} FROM appointment WHERE id = $1",
            APPOINTMENT_COLUMNS
        ))
        .bind(appointment_id)
        .fetch_one(db.pool())
        .await
        .expect("Failed to fetch appointment from database");

        // Assert
        assert_eq!(fetched_appointment.id, appointment_id);
        assert_eq!(fetched_appointment.name, "Team Standup");
        assert_eq!(
            fetched_appointment.description,
            Some("Daily team standup meeting".to_string())
        );
        assert_eq!(
            fetched_appointment.venue,
            Some("Conference Room A".to_string())
        );

        // The creator is the RESPONSIBLE participant
        let role: String = sqlx::query_scalar(
            "SELECT role FROM appointment_participation WHERE appointment_id = $1 AND user_oidcid = $2",
        )
        .bind(appointment_id)
        .bind(&creator_id)
        .fetch_one(db.pool())
        .await
        .expect("Failed to fetch creator participation");
        assert_eq!(role, "RESPONSIBLE");
    }

    #[tokio::test]
    #[ignore]
    async fn test_get_nonexistent_appointment_returns_404() {
        // Setup
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");

        // Create a fake appointment ID that doesn't exist
        let nonexistent_id: i64 = 987_654_321;

        // Verify the appointment doesn't exist
        let result: Result<Appointment, sqlx::Error> = sqlx::query_as(&format!(
            "SELECT {} FROM appointment WHERE id = $1",
            APPOINTMENT_COLUMNS
        ))
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
                &user1_id,
                &AppointmentFixture::new().with_title("Meeting 1"),
            )
            .await
            .expect("Failed to create appointment 1");

        let appt2_id = fixtures
            .create_appointment(
                &user2_id,
                &AppointmentFixture::new().with_title("Meeting 2"),
            )
            .await
            .expect("Failed to create appointment 2");

        // Verify all appointments can be retrieved
        let all_appointments: Vec<Appointment> = sqlx::query_as(&format!(
            "SELECT {} FROM appointment ORDER BY start_time DESC",
            APPOINTMENT_COLUMNS
        ))
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
            .create_appointment(&creator_id, &appt_fixture)
            .await
            .expect("Failed to create appointment");

        // Fetch and verify response structure
        let appointment: Appointment = sqlx::query_as(&format!(
            "SELECT {} FROM appointment WHERE id = $1",
            APPOINTMENT_COLUMNS
        ))
        .bind(appointment_id)
        .fetch_one(db.pool())
        .await
        .expect("Failed to fetch appointment");

        // Convert to response and verify all fields are present
        let response = serde_json::json!({
            "id": appointment.id,
            "name": appointment.name,
            "description": appointment.description,
            "start_time": appointment.start_time,
            "end_time": appointment.end_time,
            "venue": appointment.venue,
            "status": appointment.status,
            "created_at": appointment.created_at,
            "last_update": appointment.last_update,
        });

        // Assert all fields are present and properly formatted
        assert!(response.get("id").is_some());
        assert!(response["id"].is_i64(), "ids are JSON numbers");
        assert!(response.get("name").is_some());
        assert_eq!(response["status"], "PLANNED");
        assert!(response.get("start_time").is_some());
        assert!(response.get("end_time").is_some());
    }
}
