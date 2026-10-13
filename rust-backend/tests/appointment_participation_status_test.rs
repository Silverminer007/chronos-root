/// Integration tests for GET /api/v2/appointments/:id/participation/status
///
/// Behaviour mirrors the Java backend (AppointmentParticipationResource#getParticipationStatus):
/// - always 200 OK with `{"status": "<PENDING|APPROVED|REJECTED>"}`
/// - PENDING when the caller has no participation row
/// - no appointment existence check and no soft-delete filtering
/// - only the authenticated caller's own status is returned
use axum::{
    body::Body,
    extract::Request,
    http::StatusCode,
    middleware::{self, Next},
    routing::get,
    Router,
};
use chronos_date_api::appointments::handlers::{get_participation_status, AppState};
use chronos_date_api::event_bus::postgres::PostgresEventBus;
use chronos_date_api::security::PrincipalContext;
use chronos_date_api::test_utils::{AppointmentFixture, TestDb, TestFixtures};
use std::sync::Arc;
use tower::Service;
use uuid::Uuid;

/// Builds a router with the status route, authenticated as `user_id`.
///
/// Token validation needs a live Keycloak JWKS, so the principal is injected
/// the same way the auth middleware would after a successful validation.
fn router_for(state: Arc<AppState>, user_id: Uuid) -> Router {
    Router::new()
        .route(
            "/api/v2/appointments/:id/participation/status",
            get(get_participation_status),
        )
        .with_state(state)
        .layer(middleware::from_fn(
            move |mut request: Request, next: Next| async move {
                request
                    .extensions_mut()
                    .insert(Arc::new(PrincipalContext::new(user_id)));
                next.run(request).await
            },
        ))
}

/// Calls the status endpoint as `user_id` and returns status code and JSON body
async fn get_status(
    db: &TestDb,
    user_id: Uuid,
    appointment_id: Uuid,
) -> (StatusCode, serde_json::Value) {
    let state = Arc::new(AppState {
        db_pool: db.pool().clone(),
        event_publisher: Arc::new(PostgresEventBus::new(db.pool().clone())),
    });
    let mut router = router_for(state, user_id);

    let request = http::Request::builder()
        .uri(format!(
            "/api/v2/appointments/{}/participation/status",
            appointment_id
        ))
        .method("GET")
        .body(Body::empty())
        .expect("Failed to build request");

    let response = router.call(request).await.expect("Router call failed");
    let status = response.status();
    let bytes = axum::body::to_bytes(response.into_body(), usize::MAX)
        .await
        .expect("Failed to read body");
    let body = if bytes.is_empty() {
        serde_json::Value::Null
    } else {
        serde_json::from_slice(&bytes).unwrap_or_else(|_| {
            panic!(
                "Body is not JSON (status {}): {}",
                status,
                String::from_utf8_lossy(&bytes)
            )
        })
    };
    (status, body)
}

/// Creates a user with an appointment created by `creator`
async fn setup_appointment(db: &TestDb, creator_name: &str) -> (Uuid, Uuid) {
    let fixtures = TestFixtures::new(db.pool().clone());
    let creator_id = fixtures
        .create_user(creator_name, &format!("{}@example.com", creator_name))
        .await
        .expect("Failed to create creator");
    let appointment_id = fixtures
        .create_appointment(
            creator_id,
            &AppointmentFixture::new().with_title("Status Test"),
        )
        .await
        .expect("Failed to create appointment");
    (creator_id, appointment_id)
}

/// Creates a user and adds them as participant with the given status
async fn add_user_with_status(db: &TestDb, name: &str, appointment_id: Uuid, status: &str) -> Uuid {
    let fixtures = TestFixtures::new(db.pool().clone());
    let user_id = fixtures
        .create_user(name, &format!("{}@example.com", name))
        .await
        .expect("Failed to create user");
    fixtures
        .add_participant(appointment_id, user_id, status)
        .await
        .expect("Failed to add participant");
    user_id
}

#[tokio::test]
async fn test_returns_approved_status_for_participant() {
    let db = TestDb::new()
        .await
        .expect("Failed to initialize test database");
    let (_creator, appointment_id) = setup_appointment(&db, "approved_creator").await;
    let user_id = add_user_with_status(&db, "approved_user", appointment_id, "APPROVED").await;

    let (status, body) = get_status(&db, user_id, appointment_id).await;

    assert_eq!(status, StatusCode::OK);
    assert_eq!(body, serde_json::json!({ "status": "APPROVED" }));
}

#[tokio::test]
async fn test_returns_rejected_status_for_participant() {
    let db = TestDb::new()
        .await
        .expect("Failed to initialize test database");
    let (_creator, appointment_id) = setup_appointment(&db, "rejected_creator").await;
    let user_id = add_user_with_status(&db, "rejected_user", appointment_id, "REJECTED").await;

    let (status, body) = get_status(&db, user_id, appointment_id).await;

    assert_eq!(status, StatusCode::OK);
    assert_eq!(body, serde_json::json!({ "status": "REJECTED" }));
}

#[tokio::test]
async fn test_returns_pending_status_for_pending_participant() {
    let db = TestDb::new()
        .await
        .expect("Failed to initialize test database");
    let (_creator, appointment_id) = setup_appointment(&db, "pending_creator").await;
    let user_id = add_user_with_status(&db, "pending_user", appointment_id, "PENDING").await;

    let (status, body) = get_status(&db, user_id, appointment_id).await;

    assert_eq!(status, StatusCode::OK);
    assert_eq!(body, serde_json::json!({ "status": "PENDING" }));
}

#[tokio::test]
async fn test_returns_only_callers_own_status() {
    let db = TestDb::new()
        .await
        .expect("Failed to initialize test database");
    let (_creator, appointment_id) = setup_appointment(&db, "own_status_creator").await;
    let approved_user =
        add_user_with_status(&db, "own_status_approved", appointment_id, "APPROVED").await;
    let rejected_user =
        add_user_with_status(&db, "own_status_rejected", appointment_id, "REJECTED").await;

    let (_, approved_body) = get_status(&db, approved_user, appointment_id).await;
    let (_, rejected_body) = get_status(&db, rejected_user, appointment_id).await;

    assert_eq!(approved_body, serde_json::json!({ "status": "APPROVED" }));
    assert_eq!(rejected_body, serde_json::json!({ "status": "REJECTED" }));
}

#[tokio::test]
async fn test_non_participant_gets_pending_status() {
    let db = TestDb::new()
        .await
        .expect("Failed to initialize test database");
    let (_creator, appointment_id) = setup_appointment(&db, "nonparticipant_creator").await;
    let stranger = TestFixtures::new(db.pool().clone())
        .create_user("nonparticipant_stranger", "stranger@example.com")
        .await
        .expect("Failed to create user");

    let (status, body) = get_status(&db, stranger, appointment_id).await;

    assert_eq!(status, StatusCode::OK);
    assert_eq!(body, serde_json::json!({ "status": "PENDING" }));
}

#[tokio::test]
async fn test_nonexistent_appointment_returns_pending_status() {
    let db = TestDb::new()
        .await
        .expect("Failed to initialize test database");
    let user = TestFixtures::new(db.pool().clone())
        .create_user("nonexistent_user", "nonexistent@example.com")
        .await
        .expect("Failed to create user");

    let (status, body) = get_status(&db, user, Uuid::new_v4()).await;

    assert_eq!(status, StatusCode::OK);
    assert_eq!(body, serde_json::json!({ "status": "PENDING" }));
}

#[tokio::test]
async fn test_cancelled_appointment_still_returns_participant_status() {
    let db = TestDb::new()
        .await
        .expect("Failed to initialize test database");
    let (_creator, appointment_id) = setup_appointment(&db, "cancelled_creator").await;
    let user_id = add_user_with_status(&db, "cancelled_user", appointment_id, "APPROVED").await;
    sqlx::query("UPDATE appointments SET status = 'CANCELLED' WHERE id = $1")
        .bind(appointment_id)
        .execute(db.pool())
        .await
        .expect("Failed to cancel appointment");

    let (status, body) = get_status(&db, user_id, appointment_id).await;

    assert_eq!(status, StatusCode::OK);
    assert_eq!(body, serde_json::json!({ "status": "APPROVED" }));
}

#[tokio::test]
async fn test_deleted_appointment_still_returns_participant_status() {
    let db = TestDb::new()
        .await
        .expect("Failed to initialize test database");
    let (_creator, appointment_id) = setup_appointment(&db, "deleted_creator").await;
    let user_id = add_user_with_status(&db, "deleted_user", appointment_id, "REJECTED").await;
    sqlx::query("UPDATE appointments SET status = 'DELETED' WHERE id = $1")
        .bind(appointment_id)
        .execute(db.pool())
        .await
        .expect("Failed to delete appointment");

    let (status, body) = get_status(&db, user_id, appointment_id).await;

    assert_eq!(status, StatusCode::OK);
    assert_eq!(body, serde_json::json!({ "status": "REJECTED" }));
}
