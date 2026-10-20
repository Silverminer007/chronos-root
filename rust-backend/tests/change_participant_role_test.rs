/// Tests for changing a participant's role: service level and HTTP router level
/// (PUT /api/v2/appointments/:id/participation/:userId/role)
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

/// Router-level tests: route, auth, request parsing and status mapping via `build_router`
#[cfg(test)]
mod change_participant_role_http_tests {
    use axum::{body::Body, http::StatusCode, routing::get, Json, Router};
    use chronos_date_api::app::build_router;
    use chronos_date_api::appointments::handlers::AppState;
    use chronos_date_api::event_bus::postgres::PostgresEventBus;
    use chronos_date_api::security::TokenValidator;
    use chronos_date_api::test_utils::{
        user_state_for_tests, AppointmentFixture, TestDb, TestFixtures,
    };
    use jsonwebtoken::{encode, Algorithm, EncodingKey, Header};
    use std::sync::Arc;
    use tower::Service;

    /// Test-only RSA key pair. The public half is served from a local mock JWKS endpoint.
    const TEST_PRIVATE_KEY_PEM: &str = include_str!("fixtures/rs256_test_key.pem");
    const TEST_JWK_KID: &str = "change-role-test";
    const TEST_JWK_MODULUS: &str = "915TXanmgE10B-CUnu4gKx24VaNysPwL0PTTu59SiJ9PEwRL2NHdx44BC_G0_vACwXOtrFhE6oLHpezfZnNb2NwM8Jcxh1XWNRRjuzBwEWtfcbb27U0cHnjwSu7gMoWVAuUq0Uj1j2vI1GBdFGJSoZpdFrIwiuvpo5hu5kWJYbu-kYKFxzdPmaQoVTGryxecwZCndLSOoY2Lal36BvFtnknGj_i32P1ll4lND7l3yYpdjGOuEXfrfUX9PkPfCHmK2_vAFnA1b54uW0xoDIfZbP6cS0BIaEZsq-0xqOIMdr1Jj3l-tSlhtJxairnmH02eujLRA1abGtTUc4jI7-p8sw";
    const TEST_JWK_EXPONENT: &str = "AQAB";

    async fn start_mock_keycloak() -> String {
        let jwks = serde_json::json!({
            "keys": [{
                "kid": TEST_JWK_KID,
                "kty": "RSA",
                "n": TEST_JWK_MODULUS,
                "e": TEST_JWK_EXPONENT,
            }]
        });
        let app = Router::new().route(
            "/realms/chronos/protocol/openid-connect/certs",
            get(move || {
                let jwks = jwks.clone();
                async move { Json(jwks) }
            }),
        );
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0")
            .await
            .expect("Failed to bind mock Keycloak");
        let addr = listener.local_addr().expect("Failed to read address");
        tokio::spawn(async move {
            axum::serve(listener, app)
                .await
                .expect("Mock Keycloak server failed")
        });
        format!("http://{}/realms/chronos", addr)
    }

    fn sign_token(user_id: &str) -> String {
        let now = chrono::Utc::now().timestamp();
        let claims = serde_json::json!({ "sub": user_id, "iat": now, "exp": now + 3600 });
        let mut header = Header::new(Algorithm::RS256);
        header.kid = Some(TEST_JWK_KID.to_string());
        let key = EncodingKey::from_rsa_pem(TEST_PRIVATE_KEY_PEM.as_bytes())
            .expect("Invalid test private key");
        encode(&header, &claims, &key).expect("Failed to sign test token")
    }

    struct Setup {
        db: TestDb,
        creator_id: String,
        participant_id: String,
        appointment_id: i64,
    }

    async fn setup() -> Setup {
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let fixtures = TestFixtures::new(db.pool().clone());
        let creator_id = fixtures
            .create_user("http_role_creator", "http_role_creator@example.com")
            .await
            .expect("Failed to create creator");
        let participant_id = fixtures
            .create_user("http_role_participant", "http_role_participant@example.com")
            .await
            .expect("Failed to create participant");
        let appointment_id = fixtures
            .create_appointment(
                &creator_id,
                &AppointmentFixture::new().with_title("Role HTTP Test"),
            )
            .await
            .expect("Failed to create appointment");
        fixtures
            .add_participant(appointment_id, &participant_id, "PENDING")
            .await
            .expect("Failed to add participant");
        Setup {
            db,
            creator_id,
            participant_id,
            appointment_id,
        }
    }

    /// Sends PUT .../participation/{target}/role through the real router.
    /// `token_user` None means no Authorization header.
    async fn put_role(
        s: &Setup,
        token_user: Option<&str>,
        target: &str,
        body: &str,
    ) -> (StatusCode, Vec<u8>) {
        let state = Arc::new(AppState {
            db_pool: s.db.pool().clone(),
            event_publisher: Arc::new(PostgresEventBus::new(s.db.pool().clone())),
        });
        let validator = Arc::new(TokenValidator::new(start_mock_keycloak().await));
        let mut router = build_router(
            state.clone(),
            user_state_for_tests(state.db_pool.clone()),
            validator,
        );
        let mut builder = http::Request::builder()
            .uri(format!(
                "/api/v2/appointments/{}/participation/{}/role",
                s.appointment_id, target
            ))
            .method("PUT")
            .header("content-type", "application/json");
        if let Some(user) = token_user {
            builder = builder.header("authorization", format!("Bearer {}", sign_token(user)));
        }
        let request = builder
            .body(Body::from(body.to_string()))
            .expect("Failed to build request");
        let response = router.call(request).await.expect("Router call failed");
        let status = response.status();
        let bytes = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .expect("Failed to read body");
        (status, bytes.to_vec())
    }

    async fn stored_role(s: &Setup, user: &str) -> String {
        sqlx::query_scalar(
            "SELECT role FROM appointment_participation WHERE appointment_id = $1 AND user_oidcid = $2",
        )
        .bind(s.appointment_id)
        .bind(user)
        .fetch_one(s.db.pool())
        .await
        .expect("Failed to read role")
    }

    #[tokio::test]
    async fn test_route_responsible_changes_role_returns_200_empty_body() {
        let s = setup().await;

        let (status, body) = put_role(
            &s,
            Some(&s.creator_id),
            &s.participant_id,
            r#"{"role":"HELPER"}"#,
        )
        .await;

        assert_eq!(status, StatusCode::OK);
        assert!(body.is_empty(), "Java returns an empty body");
        assert_eq!(stored_role(&s, &s.participant_id).await, "HELPER");
    }

    #[tokio::test]
    async fn test_route_non_responsible_gets_403() {
        let s = setup().await;

        let (status, body) = put_role(
            &s,
            Some(&s.participant_id),
            &s.participant_id,
            r#"{"role":"RESPONSIBLE"}"#,
        )
        .await;

        assert_eq!(status, StatusCode::FORBIDDEN);
        assert!(
            String::from_utf8_lossy(&body)
                .contains("You have to be responsible for this appointment"),
            "Java message must be passed through"
        );
        assert_ne!(stored_role(&s, &s.participant_id).await, "RESPONSIBLE");
    }

    #[tokio::test]
    async fn test_route_invalid_role_gets_400() {
        let s = setup().await;

        let (status, _) = put_role(
            &s,
            Some(&s.creator_id),
            &s.participant_id,
            r#"{"role":"SUPERHERO"}"#,
        )
        .await;

        assert_eq!(status, StatusCode::BAD_REQUEST);
    }

    #[tokio::test]
    async fn test_route_body_without_role_is_client_error() {
        let s = setup().await;

        let (status, _) = put_role(&s, Some(&s.creator_id), &s.participant_id, "{}").await;

        assert!(status.is_client_error(), "got {}", status);
    }

    #[tokio::test]
    async fn test_route_without_token_gets_401() {
        let s = setup().await;

        let (status, _) = put_role(&s, None, &s.participant_id, r#"{"role":"HELPER"}"#).await;

        assert_eq!(status, StatusCode::UNAUTHORIZED);
    }
}
