/// Comprehensive tests for AppointmentService methods
#[cfg(test)]
mod appointment_service_tests {
    use chronos_date_api::appointments::{
        models::{CreateAppointmentRequest, UpdateAppointmentRequest},
        repository::AppointmentRepository,
        services::{AppointmentSearch, AppointmentService},
    };
    use chronos_date_api::test_utils::{TestDb, TestFixtures};

    macro_rules! setup_test_db {
        () => {
            match TestDb::new().await {
                Ok(db) => db,
                Err(e) => {
                    eprintln!("Skipping test: Failed to initialize test database: {}", e);
                    return;
                }
            }
        };
    }

    #[tokio::test]
    async fn test_get_appointment_not_found() {
        let db = setup_test_db!();
        let repo = AppointmentRepository::new(db.pool().clone());
        let service = AppointmentService::new(repo);

        let result = service.get_appointment(999_999_i64).await;
        assert!(result.is_err());
    }

    #[tokio::test]
    async fn test_list_user_appointments_empty() {
        let db = setup_test_db!();
        let pool = db.pool().clone();
        let fixtures = TestFixtures::new(pool.clone());

        let user_id = fixtures
            .create_user("user_list_empty", "user@example.com")
            .await
            .expect("Failed to create user");

        let repo = AppointmentRepository::new(pool);
        let service = AppointmentService::new(repo);

        let result = service
            .search_user_appointments(&user_id, &AppointmentSearch::default(), 0, 10)
            .await;

        assert!(result.is_ok());
        let (items, total) = result.unwrap();
        assert!(items.is_empty());
        assert_eq!(total, 0);
    }

    #[tokio::test]
    async fn test_list_appointments_with_pagination() {
        let db = setup_test_db!();
        let pool = db.pool().clone();
        let fixtures = TestFixtures::new(pool.clone());

        let user_id = fixtures
            .create_user("user_paginate_test", "user@example.com")
            .await
            .expect("Failed to create user");

        let repo = AppointmentRepository::new(pool);
        let service = AppointmentService::new(repo);

        // Create multiple upcoming appointments
        for i in 0..5 {
            let request = CreateAppointmentRequest {
                name: format!("Meeting {}", i),
                description: None,
                venue: None,
                start: format!("2030-12-{}T10:00:00Z", 20 + i),
                end: format!("2030-12-{}T11:00:00Z", 20 + i),
                minimal_attendees: None,
            };
            let _ = service
                .create_appointment(request, user_id.to_string())
                .await;
        }

        let (items, total) = service
            .search_user_appointments(&user_id, &AppointmentSearch::default(), 1, 2)
            .await
            .expect("Failed to search appointments");

        assert_eq!(total, 5);
        let names: Vec<&str> = items.iter().map(|a| a.name.as_str()).collect();
        assert_eq!(names, vec!["Meeting 2", "Meeting 3"]);
    }

    #[tokio::test]
    async fn test_update_appointment_success() {
        let db = setup_test_db!();
        let pool = db.pool().clone();
        let fixtures = TestFixtures::new(pool.clone());

        let user_id = fixtures
            .create_user("user_update_test", "user@example.com")
            .await
            .expect("Failed to create user");

        let repo = AppointmentRepository::new(pool);
        let service = AppointmentService::new(repo);

        let request = CreateAppointmentRequest {
            name: "Original".to_string(),
            description: None,
            venue: None,
            start: "2025-12-20T10:00:00Z".to_string(),
            end: "2025-12-20T11:00:00Z".to_string(),
            minimal_attendees: None,
        };

        let response = service
            .create_appointment(request, user_id.to_string())
            .await
            .expect("Failed to create");

        let update_request = UpdateAppointmentRequest {
            name: Some("Updated".to_string()),
            description: Some("New desc".to_string()),
            venue: Some("New venue".to_string()),
            start: Some("2025-12-21T10:00:00Z".to_string()),
            end: Some("2025-12-21T11:00:00Z".to_string()),
            minimal_attendees: Some(5),
        };

        let result = service
            .update_appointment(response.id, update_request, "test-user")
            .await;

        assert!(result.is_ok());
    }

    #[tokio::test]
    async fn test_delete_appointment_success() {
        let db = setup_test_db!();
        let pool = db.pool().clone();
        let fixtures = TestFixtures::new(pool.clone());

        let user_id = fixtures
            .create_user("user_delete_test", "user@example.com")
            .await
            .expect("Failed to create user");

        let repo = AppointmentRepository::new(pool);
        let service = AppointmentService::new(repo);

        let request = CreateAppointmentRequest {
            name: "To Delete".to_string(),
            description: None,
            venue: None,
            start: "2025-12-20T10:00:00Z".to_string(),
            end: "2025-12-20T11:00:00Z".to_string(),
            minimal_attendees: None,
        };

        let response = service
            .create_appointment(request, user_id.to_string())
            .await
            .expect("Failed to create");

        let result = service.delete_appointment(response.id).await;

        assert!(result.is_ok());
    }

    #[tokio::test]
    async fn test_cancel_appointment_success() {
        let db = setup_test_db!();
        let pool = db.pool().clone();
        let fixtures = TestFixtures::new(pool.clone());

        let user_id = fixtures
            .create_user("user_cancel_test", "user@example.com")
            .await
            .expect("Failed to create user");

        let repo = AppointmentRepository::new(pool);
        let service = AppointmentService::new(repo);

        let request = CreateAppointmentRequest {
            name: "To Cancel".to_string(),
            description: None,
            venue: None,
            start: "2025-12-20T10:00:00Z".to_string(),
            end: "2025-12-20T11:00:00Z".to_string(),
            minimal_attendees: None,
        };

        let response = service
            .create_appointment(request, user_id.to_string())
            .await
            .expect("Failed to create");

        let result = service.cancel_appointment(response.id, "test-user").await;

        assert!(result.is_ok());
    }

    #[tokio::test]
    async fn test_update_nonexistent_appointment() {
        let db = setup_test_db!();
        let pool = db.pool().clone();

        let repo = AppointmentRepository::new(pool);
        let service = AppointmentService::new(repo);

        let update_request = UpdateAppointmentRequest {
            name: Some("Updated".to_string()),
            description: None,
            venue: None,
            start: None,
            end: None,
            minimal_attendees: None,
        };

        let result = service
            .update_appointment(999_999_i64, update_request, "test-user")
            .await;

        assert!(result.is_err());
    }

    #[tokio::test]
    async fn test_delete_nonexistent_appointment() {
        let db = setup_test_db!();
        let pool = db.pool().clone();

        let repo = AppointmentRepository::new(pool);
        let service = AppointmentService::new(repo);

        let result = service.delete_appointment(999_999_i64).await;

        assert!(result.is_err());
    }

    #[tokio::test]
    async fn test_cancel_nonexistent_appointment() {
        let db = setup_test_db!();
        let pool = db.pool().clone();

        let repo = AppointmentRepository::new(pool);
        let service = AppointmentService::new(repo);

        let result = service.cancel_appointment(999_999_i64, "test-user").await;

        assert!(result.is_err());
    }
}
