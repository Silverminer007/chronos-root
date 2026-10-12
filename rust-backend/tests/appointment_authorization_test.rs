/// Integration tests for appointment authorization and filtering
///
/// The Java-compatible schema has no creator column: the creator of an appointment is its
/// RESPONSIBLE participant (ADR 0013).
#[cfg(test)]
mod appointment_auth_tests {
    use chronos_date_api::appointments::repository::AppointmentRepository;
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

        // Each creator creates an appointment
        let appt1_id = fixtures
            .create_appointment(
                &creator1_id,
                &AppointmentFixture::new().with_title("Creator 1 Meeting"),
            )
            .await
            .expect("Failed to create appointment 1");

        let appt2_id = fixtures
            .create_appointment(
                &creator2_id,
                &AppointmentFixture::new().with_title("Creator 2 Meeting"),
            )
            .await
            .expect("Failed to create appointment 2");

        let repo = AppointmentRepository::new(db.pool().clone());

        // The creator is the RESPONSIBLE participant of exactly their own appointment
        let creator1_appointments = repo.find_by_creator(&creator1_id).await.unwrap();
        assert_eq!(creator1_appointments.len(), 1);
        assert_eq!(creator1_appointments[0].id, appt1_id);
        assert_eq!(creator1_appointments[0].name, "Creator 1 Meeting");

        let creator2_appointments = repo.find_by_creator(&creator2_id).await.unwrap();
        assert_eq!(creator2_appointments.len(), 1);
        assert_eq!(creator2_appointments[0].id, appt2_id);
    }

    #[tokio::test]
    #[ignore]
    async fn test_list_creator_appointments_filters_correctly() {
        // Setup
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

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
            fixtures
                .create_appointment(
                    &user1_id,
                    &AppointmentFixture::new().with_title(&format!("User1 Meeting {}", i)),
                )
                .await
                .unwrap_or_else(|_| panic!("Failed to create appointment {}", i));
        }

        // User 2 creates 2 appointments
        for i in 1..=2 {
            fixtures
                .create_appointment(
                    &user2_id,
                    &AppointmentFixture::new().with_title(&format!("User2 Meeting {}", i)),
                )
                .await
                .unwrap_or_else(|_| panic!("Failed to create user2 appointment {}", i));
        }

        let repo = AppointmentRepository::new(db.pool().clone());
        assert_eq!(repo.find_by_creator(&user1_id).await.unwrap().len(), 3);
        assert_eq!(repo.find_by_creator(&user2_id).await.unwrap().len(), 2);
    }

    #[tokio::test]
    #[ignore]
    async fn test_visible_to_user_includes_participations_but_not_creator_only_filter() {
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());

        let creator = fixtures
            .create_user("creator", "c@example.com")
            .await
            .unwrap();
        let guest = fixtures
            .create_user("guest", "g@example.com")
            .await
            .unwrap();

        let appt = fixtures
            .create_appointment(&creator, &AppointmentFixture::new())
            .await
            .unwrap();
        fixtures
            .add_participant(appt, &guest, "PENDING")
            .await
            .unwrap();

        let repo = AppointmentRepository::new(db.pool().clone());
        assert_eq!(repo.find_visible_to_user(&guest).await.unwrap().len(), 1);
        assert!(repo.find_by_creator(&guest).await.unwrap().is_empty());
    }
}
