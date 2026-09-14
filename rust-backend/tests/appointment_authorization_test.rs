/// Integration tests for appointment authorization and filtering
#[cfg(test)]
mod appointment_auth_tests {
    use chronos_date_api::test_utils::{AppointmentFixture, TestDb, TestFixtures};

    #[tokio::test]
    #[ignore]
    async fn test_get_only_creator_appointments_for_user() {
        // Setup
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        // Create multiple users
        let creator1_id = fixtures
            .create_user("creator1", "creator1@example.com")
            .await
            .expect("Failed to create creator1");

        let creator2_id = fixtures
            .create_user("creator2", "creator2@example.com")
            .await
            .expect("Failed to create creator2");

        // Creator 1 creates an appointment
        let appt1_id = fixtures
            .create_appointment(
                creator1_id,
                &AppointmentFixture::new().with_title("Creator 1 Meeting"),
            )
            .await
            .expect("Failed to create appointment 1");

        // Creator 2 creates an appointment
        let appt2_id = fixtures
            .create_appointment(
                creator2_id,
                &AppointmentFixture::new().with_title("Creator 2 Meeting"),
            )
            .await
            .expect("Failed to create appointment 2");

        // Verify creator1's appointment belongs to creator1
        let appt1: chronos_date_api::appointments::models::Appointment = sqlx::query_as(
            "SELECT id, title, description, start_time, end_time, location, creator_id, created_at, updated_at
             FROM appointments WHERE id = $1"
        )
        .bind(appt1_id)
        .fetch_one(db.pool())
        .await
        .expect("Failed to fetch appointment 1");

        assert_eq!(appt1.creator_id, creator1_id);

        // Verify creator2's appointment belongs to creator2
        let appt2: chronos_date_api::appointments::models::Appointment = sqlx::query_as(
            "SELECT id, title, description, start_time, end_time, location, creator_id, created_at, updated_at
             FROM appointments WHERE id = $1"
        )
        .bind(appt2_id)
        .fetch_one(db.pool())
        .await
        .expect("Failed to fetch appointment 2");

        assert_eq!(appt2.creator_id, creator2_id);
    }

    #[tokio::test]
    #[ignore]
    async fn test_list_creator_appointments_filters_correctly() {
        // Setup
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        // Create two users
        let user1_id = fixtures
            .create_user("user1", "user1@example.com")
            .await
            .expect("Failed to create user1");

        let user2_id = fixtures
            .create_user("user2", "user2@example.com")
            .await
            .expect("Failed to create user2");

        // User 1 creates 3 appointments
        for i in 1..=3 {
            let _appt_id = fixtures
                .create_appointment(
                    user1_id,
                    &AppointmentFixture::new().with_title(&format!("User1 Meeting {}", i)),
                )
                .await
                .expect(&format!("Failed to create appointment {}", i));
        }

        // User 2 creates 2 appointments
        for i in 1..=2 {
            let _appt_id = fixtures
                .create_appointment(
                    user2_id,
                    &AppointmentFixture::new().with_title(&format!("User2 Meeting {}", i)),
                )
                .await
                .expect(&format!("Failed to create user2 appointment {}", i));
        }

        // Verify User 1 has exactly 3 appointments
        let user1_appointments: Vec<(i64,)> = sqlx::query_as(
            "SELECT COUNT(*) FROM appointments WHERE creator_id = $1"
        )
        .bind(user1_id)
        .fetch_all(db.pool())
        .await
        .expect("Failed to fetch user1 appointments count");

        assert_eq!(user1_appointments.len(), 1);
        assert_eq!(user1_appointments[0].0, 3);

        // Verify User 2 has exactly 2 appointments
        let user2_appointments: Vec<(i64,)> = sqlx::query_as(
            "SELECT COUNT(*) FROM appointments WHERE creator_id = $1"
        )
        .bind(user2_id)
        .fetch_all(db.pool())
        .await
        .expect("Failed to fetch user2 appointments count");

        assert_eq!(user2_appointments.len(), 1);
        assert_eq!(user2_appointments[0].0, 2);
    }
}
