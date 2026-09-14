/// Integration tests for appointment mutation endpoints (POST/PUT/DELETE)
#[cfg(test)]
mod appointment_mutation_tests {
    use chronos_date_api::appointments::models::{
        Appointment, CreateAppointmentRequest, MoveAppointmentRequest, UpdateAppointmentRequest,
    };
    use chronos_date_api::test_utils::{AppointmentFixture, TestAuthHelper, TestDb, TestFixtures};
    use chrono::Utc;
    use uuid::Uuid;

    #[tokio::test]
    #[ignore]
    async fn test_create_appointment_success() {
        // Setup
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        // Create a test user (creator of the appointment)
        let creator_id = fixtures
            .create_user("keycloak_user_create_test", "creator@example.com")
            .await
            .expect("Failed to create test user");

        let now = Utc::now();
        let request = CreateAppointmentRequest {
            title: "New Meeting".to_string(),
            description: Some("Test appointment".to_string()),
            location: Some("Room A".to_string()),
            start_time: now + chrono::Duration::hours(1),
            end_time: now + chrono::Duration::hours(2),
            minimal_attendees: Some(2),
        };

        // Create the appointment
        let repo =
            chronos_date_api::appointments::repository::AppointmentRepository::new(db.pool().clone());
        let service = chronos_date_api::appointments::services::AppointmentService::new(repo);

        let created = service
            .create_appointment(
                request.title.clone(),
                request.description.clone(),
                request.location.clone(),
                request.start_time,
                request.end_time,
                creator_id,
            )
            .await
            .expect("Failed to create appointment");

        // Verify the appointment was created
        assert_eq!(created.title, "New Meeting");
        assert_eq!(created.description, Some("Test appointment".to_string()));
        assert_eq!(created.location, Some("Room A".to_string()));
        assert_eq!(created.creator_id, creator_id);
    }

    #[tokio::test]
    #[ignore]
    async fn test_create_appointment_empty_title_validation() {
        // Setup
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        let creator_id = fixtures
            .create_user("keycloak_user_validation_test", "creator@example.com")
            .await
            .expect("Failed to create test user");

        let now = Utc::now();
        let repo = chronos_date_api::appointments::repository::AppointmentRepository::new(
            db.pool().clone(),
        );
        let service = chronos_date_api::appointments::services::AppointmentService::new(repo);

        // Try to create appointment with empty title
        let result = service
            .create_appointment(
                "".to_string(),
                None,
                None,
                now + chrono::Duration::hours(1),
                now + chrono::Duration::hours(2),
                creator_id,
            )
            .await;

        // Should fail validation
        assert!(result.is_err());
    }

    #[tokio::test]
    #[ignore]
    async fn test_create_appointment_invalid_time_range() {
        // Setup
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        let creator_id = fixtures
            .create_user("keycloak_user_time_test", "creator@example.com")
            .await
            .expect("Failed to create test user");

        let now = Utc::now();
        let repo = chronos_date_api::appointments::repository::AppointmentRepository::new(
            db.pool().clone(),
        );
        let service = chronos_date_api::appointments::services::AppointmentService::new(repo);

        // Try to create appointment with invalid time range (start >= end)
        let result = service
            .create_appointment(
                "Invalid Meeting".to_string(),
                None,
                None,
                now + chrono::Duration::hours(2),
                now + chrono::Duration::hours(1),
                creator_id,
            )
            .await;

        // Should fail validation
        assert!(result.is_err());
    }

    #[tokio::test]
    #[ignore]
    async fn test_update_appointment_success() {
        // Setup
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        let creator_id = fixtures
            .create_user("keycloak_user_update_test", "creator@example.com")
            .await
            .expect("Failed to create test user");

        let appointment_fixture = AppointmentFixture::new()
            .with_title("Original Title")
            .with_description(Some("Original description".to_string()))
            .with_location(Some("Room A".to_string()));

        let appointment_id = fixtures
            .create_appointment(creator_id, &appointment_fixture)
            .await
            .expect("Failed to create appointment");

        let repo = chronos_date_api::appointments::repository::AppointmentRepository::new(
            db.pool().clone(),
        );
        let service = chronos_date_api::appointments::services::AppointmentService::new(repo);

        // Update the appointment
        let updated = service
            .update_appointment(
                appointment_id,
                Some("Updated Title".to_string()),
                Some("Updated description".to_string()),
                None,
                None,
                None,
            )
            .await
            .expect("Failed to update appointment");

        // Verify the update
        assert_eq!(updated.title, "Updated Title");
        assert_eq!(updated.description, Some("Updated description".to_string()));
        // Location should remain unchanged
        assert_eq!(updated.location, Some("Room A".to_string()));
    }

    #[tokio::test]
    #[ignore]
    async fn test_update_appointment_invalid_title() {
        // Setup
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        let creator_id = fixtures
            .create_user("keycloak_user_update_invalid_test", "creator@example.com")
            .await
            .expect("Failed to create test user");

        let appointment_fixture = AppointmentFixture::new()
            .with_title("Original Title");

        let appointment_id = fixtures
            .create_appointment(creator_id, &appointment_fixture)
            .await
            .expect("Failed to create appointment");

        let repo = chronos_date_api::appointments::repository::AppointmentRepository::new(
            db.pool().clone(),
        );
        let service = chronos_date_api::appointments::services::AppointmentService::new(repo);

        // Try to update with empty title
        let result = service
            .update_appointment(
                appointment_id,
                Some("".to_string()),
                None,
                None,
                None,
                None,
            )
            .await;

        // Should fail validation
        assert!(result.is_err());
    }

    #[tokio::test]
    #[ignore]
    async fn test_move_appointment_success() {
        // Setup
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        let creator_id = fixtures
            .create_user("keycloak_user_move_test", "creator@example.com")
            .await
            .expect("Failed to create test user");

        let appointment_fixture = AppointmentFixture::new()
            .with_title("Meeting to Move");

        let appointment_id = fixtures
            .create_appointment(creator_id, &appointment_fixture)
            .await
            .expect("Failed to create appointment");

        let repo = chronos_date_api::appointments::repository::AppointmentRepository::new(
            db.pool().clone(),
        );
        let service = chronos_date_api::appointments::services::AppointmentService::new(repo);

        let now = Utc::now();
        let new_start = now + chrono::Duration::days(5);
        let new_end = new_start + chrono::Duration::hours(1);

        // Move the appointment
        let moved = service
            .update_appointment(
                appointment_id,
                None,
                None,
                None,
                Some(new_start),
                Some(new_end),
            )
            .await
            .expect("Failed to move appointment");

        // Verify the move
        assert_eq!(moved.start_time, new_start);
        assert_eq!(moved.end_time, new_end);
        // Title should remain unchanged
        assert_eq!(moved.title, "Meeting to Move");
    }

    #[tokio::test]
    #[ignore]
    async fn test_delete_appointment_success() {
        // Setup
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        let creator_id = fixtures
            .create_user("keycloak_user_delete_test", "creator@example.com")
            .await
            .expect("Failed to create test user");

        let appointment_fixture = AppointmentFixture::new()
            .with_title("Meeting to Delete");

        let appointment_id = fixtures
            .create_appointment(creator_id, &appointment_fixture)
            .await
            .expect("Failed to create appointment");

        let repo = chronos_date_api::appointments::repository::AppointmentRepository::new(
            db.pool().clone(),
        );
        let service = chronos_date_api::appointments::services::AppointmentService::new(repo);

        // Delete the appointment
        let result = service.delete_appointment(appointment_id).await;

        assert!(result.is_ok());

        // Verify it's deleted
        let fetched = service
            .get_appointment(appointment_id)
            .await
            .expect("Failed to query database");

        assert!(fetched.is_none());
    }

    #[tokio::test]
    #[ignore]
    async fn test_delete_nonexistent_appointment() {
        // Setup
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");

        let repo = chronos_date_api::appointments::repository::AppointmentRepository::new(
            db.pool().clone(),
        );
        let service = chronos_date_api::appointments::services::AppointmentService::new(repo);

        let fake_id = Uuid::new_v4();

        // Try to delete non-existent appointment
        let result = service.delete_appointment(fake_id).await;

        // Should fail with NotFound
        assert!(result.is_err());
    }

    #[tokio::test]
    #[ignore]
    async fn test_update_nonexistent_appointment() {
        // Setup
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");

        let repo = chronos_date_api::appointments::repository::AppointmentRepository::new(
            db.pool().clone(),
        );
        let service = chronos_date_api::appointments::services::AppointmentService::new(repo);

        let fake_id = Uuid::new_v4();

        // Try to update non-existent appointment
        let result = service
            .update_appointment(
                fake_id,
                Some("New Title".to_string()),
                None,
                None,
                None,
                None,
            )
            .await;

        // Should fail with NotFound
        assert!(result.is_err());
    }
}
