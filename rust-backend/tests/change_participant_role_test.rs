/// Integration tests for changing a participant's role
/// Tests PUT /api/v2/appointments/:id/participation/:userId/role (service level)
#[cfg(test)]
mod change_participant_role_tests {
    use chronos_date_api::appointments::models::UserRole;
    use chronos_date_api::appointments::repository::AppointmentRepository;
    use chronos_date_api::appointments::services::{AppointmentService, ServiceError};
    use chronos_date_api::event_bus::postgres::PostgresEventBus;
    use chronos_date_api::test_utils::{AppointmentFixture, TestDb, TestFixtures};

    use std::sync::Arc;

    struct Setup {
        db: TestDb,
        creator_id: String,
        participant_id: String,
        appointment_id: i64,
    }

    /// Creator (RESPONSIBLE) plus one ATTENDANT participant
    async fn setup() -> Option<Setup> {
        let db = match TestDb::new().await {
            Ok(db) => db,
            Err(e) => {
                eprintln!("Skipping test: {}", e);
                return None;
            }
        };
        let fixtures = TestFixtures::new(db.pool().clone());
        let creator_id = fixtures
            .create_user("creator_role_test", "creator@example.com")
            .await
            .expect("Failed to create creator");
        let participant_id = fixtures
            .create_user("participant_role_test", "participant@example.com")
            .await
            .expect("Failed to create participant");
        let appointment_id = fixtures
            .create_appointment(
                &creator_id,
                &AppointmentFixture::new().with_title("Meeting"),
            )
            .await
            .expect("Failed to create appointment");
        sqlx::query(
            "INSERT INTO appointment_participation (id, appointment_id, user_oidcid, role, status)
             VALUES (nextval('appointment_participation_seq'), $1, $2, 'ATTENDANT', 'PENDING')",
        )
        .bind(appointment_id)
        .bind(&participant_id)
        .execute(db.pool())
        .await
        .expect("Failed to add participant");
        Some(Setup {
            db,
            creator_id,
            participant_id,
            appointment_id,
        })
    }

    fn service(s: &Setup) -> AppointmentService {
        let repo = AppointmentRepository::new(s.db.pool().clone());
        AppointmentService::with_events(repo, Arc::new(PostgresEventBus::new(s.db.pool().clone())))
    }

    async fn role_of(s: &Setup, user: &str) -> Option<UserRole> {
        AppointmentRepository::new(s.db.pool().clone())
            .get_participant_role(s.appointment_id, user)
            .await
            .expect("Failed to read role")
    }

    #[tokio::test]
    async fn test_change_role_success_and_fires_event_with_old_role() {
        let Some(s) = setup().await else { return };

        service(&s)
            .change_participant_role(
                s.appointment_id,
                &s.participant_id,
                &s.creator_id,
                UserRole::Helper,
            )
            .await
            .expect("Role change should succeed");

        assert_eq!(role_of(&s, &s.participant_id).await, Some(UserRole::Helper));

        let payload: String = sqlx::query_scalar(
            "SELECT payload FROM events WHERE event_type = 'AppointmentParticipationRoleChangedEvent'",
        )
        .fetch_one(s.db.pool())
        .await
        .expect("Event should be fired");
        let payload: serde_json::Value = serde_json::from_str(&payload).unwrap();
        assert_eq!(payload["appointment_id"], s.appointment_id);
        assert_eq!(payload["target_user_id"], s.participant_id);
        assert_eq!(payload["acting_user_id"], s.creator_id);
        assert_eq!(payload["old_role"], "ATTENDANT");
    }

    #[tokio::test]
    async fn test_change_role_requires_responsible() {
        let Some(s) = setup().await else { return };

        // The ATTENDANT participant tries to promote themselves
        let result = service(&s)
            .change_participant_role(
                s.appointment_id,
                &s.participant_id,
                &s.participant_id,
                UserRole::Responsible,
            )
            .await;

        assert!(matches!(result, Err(ServiceError::Forbidden(_))));
        assert_eq!(
            role_of(&s, &s.participant_id).await,
            Some(UserRole::Attendant)
        );
    }

    #[tokio::test]
    async fn test_change_role_promoted_responsible_may_change_roles() {
        let Some(s) = setup().await else { return };
        let svc = service(&s);
        svc.change_participant_role(
            s.appointment_id,
            &s.participant_id,
            &s.creator_id,
            UserRole::Responsible,
        )
        .await
        .unwrap();

        // The promoted user may now demote the creator (Java: any RESPONSIBLE)
        svc.change_participant_role(
            s.appointment_id,
            &s.creator_id,
            &s.participant_id,
            UserRole::Attendant,
        )
        .await
        .expect("A RESPONSIBLE participant may change roles");
    }

    #[tokio::test]
    async fn test_change_role_unknown_appointment_is_not_found() {
        let Some(s) = setup().await else { return };

        let result = service(&s)
            .change_participant_role(999_999, &s.participant_id, &s.creator_id, UserRole::Helper)
            .await;

        assert!(matches!(result, Err(ServiceError::NotFound)));
    }

    #[tokio::test]
    async fn test_change_role_target_not_participant() {
        let Some(s) = setup().await else { return };

        let result = service(&s)
            .change_participant_role(s.appointment_id, "nobody", &s.creator_id, UserRole::Helper)
            .await;

        assert!(matches!(result, Err(ServiceError::ValidationError(_))));
    }

    #[tokio::test]
    async fn test_change_role_to_same_role_is_rejected_without_event() {
        let Some(s) = setup().await else { return };

        let result = service(&s)
            .change_participant_role(
                s.appointment_id,
                &s.participant_id,
                &s.creator_id,
                UserRole::Attendant,
            )
            .await;

        assert!(matches!(result, Err(ServiceError::ValidationError(_))));
        let count: i64 = sqlx::query_scalar(
            "SELECT COUNT(*) FROM events WHERE event_type = 'AppointmentParticipationRoleChangedEvent'",
        )
        .fetch_one(s.db.pool())
        .await
        .unwrap();
        assert_eq!(count, 0);
    }
}
