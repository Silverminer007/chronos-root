/// Integration tests for appointment sorting
#[cfg(test)]
mod appointment_sorting_tests {
    use chronos_date_api::test_utils::{AppointmentFixture, TestDb, TestFixtures};
    use chrono::Utc;

    #[tokio::test]
    #[ignore]
    async fn test_sort_appointments_by_start_time() {
        // Setup
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        // Create a user
        let creator_id = fixtures
            .create_user("creator", "creator@example.com")
            .await
            .expect("Failed to create creator");

        // Create appointments at different times
        let now = Utc::now();
        let tomorrow = now + chrono::Duration::days(1);
        let next_week = now + chrono::Duration::days(7);

        // Create in reverse order to test sorting
        let _appt3_id = fixtures
            .create_appointment(
                creator_id,
                &AppointmentFixture::new()
                    .with_title("Next Week Meeting")
                    .with_times(next_week, next_week + chrono::Duration::hours(1)),
            )
            .await
            .expect("Failed to create appointment 3");

        let _appt1_id = fixtures
            .create_appointment(
                creator_id,
                &AppointmentFixture::new()
                    .with_title("Today Meeting")
                    .with_times(now, now + chrono::Duration::hours(1)),
            )
            .await
            .expect("Failed to create appointment 1");

        let _appt2_id = fixtures
            .create_appointment(
                creator_id,
                &AppointmentFixture::new()
                    .with_title("Tomorrow Meeting")
                    .with_times(tomorrow, tomorrow + chrono::Duration::hours(1)),
            )
            .await
            .expect("Failed to create appointment 2");

        // Fetch appointments ordered by start_time
        let appointments: Vec<(String, chrono::DateTime<chrono::Utc>)> = sqlx::query_as(
            "SELECT title, start_time FROM appointments WHERE creator_id = $1 ORDER BY start_time ASC"
        )
        .bind(creator_id)
        .fetch_all(db.pool())
        .await
        .expect("Failed to fetch appointments");

        // Assert they are sorted in ascending order
        assert_eq!(appointments.len(), 3);
        assert_eq!(appointments[0].0, "Today Meeting");
        assert_eq!(appointments[1].0, "Tomorrow Meeting");
        assert_eq!(appointments[2].0, "Next Week Meeting");

        // Verify the start times are in ascending order
        assert!(appointments[0].1 <= appointments[1].1);
        assert!(appointments[1].1 <= appointments[2].1);
    }

    #[tokio::test]
    #[ignore]
    async fn test_sort_appointments_by_title() {
        // Setup
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        // Create a user
        let creator_id = fixtures
            .create_user("creator", "creator@example.com")
            .await
            .expect("Failed to create creator");

        // Create appointments with different titles
        let _appt1_id = fixtures
            .create_appointment(
                creator_id,
                &AppointmentFixture::new().with_title("Charlie Meeting"),
            )
            .await
            .expect("Failed to create appointment 1");

        let _appt2_id = fixtures
            .create_appointment(
                creator_id,
                &AppointmentFixture::new().with_title("Alpha Meeting"),
            )
            .await
            .expect("Failed to create appointment 2");

        let _appt3_id = fixtures
            .create_appointment(
                creator_id,
                &AppointmentFixture::new().with_title("Bravo Meeting"),
            )
            .await
            .expect("Failed to create appointment 3");

        // Fetch appointments ordered by title
        let appointments: Vec<(String,)> = sqlx::query_as(
            "SELECT title FROM appointments WHERE creator_id = $1 ORDER BY title ASC"
        )
        .bind(creator_id)
        .fetch_all(db.pool())
        .await
        .expect("Failed to fetch appointments");

        // Assert they are sorted alphabetically
        assert_eq!(appointments.len(), 3);
        assert_eq!(appointments[0].0, "Alpha Meeting");
        assert_eq!(appointments[1].0, "Bravo Meeting");
        assert_eq!(appointments[2].0, "Charlie Meeting");
    }
}
