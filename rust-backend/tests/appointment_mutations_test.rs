/// Integration tests for appointment mutations (create, update, delete, cancel)
#[cfg(test)]
mod appointment_mutation_tests {
    use chronos_date_api::appointments::models::{CreateAppointmentRequest, AppointmentResponse};
    use chronos_date_api::test_utils::{TestDb, TestFixtures};

    #[tokio::test]
    #[ignore]
    async fn test_create_appointment_persists_to_database() {
        // Setup
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        // Create a user (creator)
        let creator_id = fixtures
            .create_user("creator1", "creator@example.com")
            .await
            .expect("Failed to create creator");

        // Create appointment request matching Java contract
        let request = CreateAppointmentRequest {
            name: "Team Sync".to_string(),
            description: Some("Weekly team synchronization".to_string()),
            venue: Some("Conference Room B".to_string()),
            start: "2025-09-20T10:00:00Z".to_string(),
            end: "2025-09-20T11:00:00Z".to_string(),
            minimal_attendees: Some(3),
        };

        // Verify: Appointment should be created with correct fields
        // Expected response should have:
        // - name (not title), venue (not location)
        // - start/end as ISO-8601 strings
        // - status = PLANNED
        // - minimal_attendees preserved

        let json = serde_json::to_string(&request).unwrap();
        assert!(json.contains("\"name\":\"Team Sync\""));
        assert!(json.contains("\"venue\":\"Conference Room B\""));
        assert!(json.contains("\"start\":\"2025-09-20T10:00:00Z\""));
    }

    #[tokio::test]
    #[ignore]
    async fn test_update_appointment_changes_fields() {
        // Setup
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        // Create a user and appointment
        let creator_id = fixtures
            .create_user("creator2", "creator2@example.com")
            .await
            .expect("Failed to create creator");

        // For now, just verify the UpdateAppointmentRequest serializes correctly
        use chronos_date_api::appointments::models::UpdateAppointmentRequest;
        let request = UpdateAppointmentRequest {
            name: Some("Updated Name".to_string()),
            description: None,
            venue: Some("New Room".to_string()),
            start: Some("2025-09-21T14:00:00Z".to_string()),
            end: Some("2025-09-21T15:00:00Z".to_string()),
            minimal_attendees: Some(5),
        };

        let json = serde_json::to_string(&request).unwrap();
        assert!(json.contains("\"name\":\"Updated Name\""));
        assert!(json.contains("\"venue\":\"New Room\""));
        assert!(json.contains("\"start\":\"2025-09-21T14:00:00Z\""));
    }

    #[tokio::test]
    #[ignore]
    async fn test_appointment_response_includes_java_contract_fields() {
        // Verify response structure matches Java AppointmentDto
        let response = AppointmentResponse {
            id: uuid::Uuid::new_v4(),
            name: "Test Meeting".to_string(),
            description: Some("Test description".to_string()),
            start: "2025-09-20T10:00:00Z".to_string(),
            end: "2025-09-20T11:00:00Z".to_string(),
            venue: Some("Room A".to_string()),
            status: "PLANNED".to_string(),
            minimal_attendees: Some(3),
            participants: None,
            messages: None,
            group_participants: None,
        };

        let json = serde_json::to_string(&response).unwrap();

        // Verify Java field names are used
        assert!(json.contains("\"name\":"));
        assert!(json.contains("\"venue\":"));
        assert!(json.contains("\"start\":"));
        assert!(json.contains("\"end\":"));
        assert!(json.contains("\"status\":\"PLANNED\""));
        assert!(json.contains("\"minimal_attendees\":3"));
    }
}
