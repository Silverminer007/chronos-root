/// Integration tests for GET /api/v2/appointments/:id/messages
///
/// Behaviour mirrors the Java backend (MessageResource#getMessagesForEvent / MessageService#getMessages):
/// - any participant (role other than NONE) may read, everybody else gets 403
/// - messages are ordered newest first
/// - body fields: id, sender_id, sender_name, appointment_id, body, timestamp
use axum::{
    body::Body,
    extract::Request,
    http::StatusCode,
    middleware::{self, Next},
    routing::get,
    Router,
};
use chronos_date_api::appointments::handlers::{list_messages, AppState};
use chronos_date_api::event_bus::postgres::PostgresEventBus;
use chronos_date_api::security::PrincipalContext;
use chronos_date_api::test_utils::{AppointmentFixture, TestDb, TestFixtures};
use std::sync::Arc;
use tower::Service;

/// Builds a router with the messages route, authenticated as `user_id`
fn router_for(state: Arc<AppState>, user_id: String) -> Router {
    Router::new()
        .route("/api/v2/appointments/:id/messages", get(list_messages))
        .with_state(state)
        .layer(middleware::from_fn(
            move |mut request: Request, next: Next| {
                let user_id = user_id.clone();
                async move {
                    request
                        .extensions_mut()
                        .insert(Arc::new(PrincipalContext::new(user_id)));
                    next.run(request).await
                }
            },
        ))
}

async fn get_messages(
    db: &TestDb,
    user_id: &str,
    appointment_id: i64,
) -> (StatusCode, serde_json::Value) {
    let state = Arc::new(AppState {
        db_pool: db.pool().clone(),
        event_publisher: Arc::new(PostgresEventBus::new(db.pool().clone())),
    });
    let mut router = router_for(state, user_id.to_string());
    let request = http::Request::builder()
        .uri(format!("/api/v2/appointments/{}/messages", appointment_id))
        .method("GET")
        .body(Body::empty())
        .expect("Failed to build request");

    let response = router.call(request).await.expect("Router call failed");
    let status = response.status();
    let bytes = axum::body::to_bytes(response.into_body(), usize::MAX)
        .await
        .expect("Failed to read body");
    let body = serde_json::from_slice(&bytes).unwrap_or(serde_json::Value::Null);
    (status, body)
}

async fn setup_appointment(db: &TestDb, creator_name: &str) -> (String, i64) {
    let fixtures = TestFixtures::new(db.pool().clone());
    let creator_id = fixtures
        .create_user(creator_name, &format!("{}@example.com", creator_name))
        .await
        .expect("Failed to create creator");
    let appointment_id = fixtures
        .create_appointment(
            &creator_id,
            &AppointmentFixture::new().with_title("Message Test"),
        )
        .await
        .expect("Failed to create appointment");
    (creator_id, appointment_id)
}

async fn insert_message(
    db: &TestDb,
    appointment_id: i64,
    sender: &str,
    body: &str,
    ts: &str,
) -> i64 {
    sqlx::query_scalar::<_, i64>(
        "INSERT INTO message (appointment_id, sender_oidcid, body, \"timestamp\")
         VALUES ($1, $2, $3, $4::timestamptz) RETURNING id",
    )
    .bind(appointment_id)
    .bind(sender)
    .bind(body)
    .bind(ts)
    .fetch_one(db.pool())
    .await
    .expect("Failed to insert message")
}

#[tokio::test]
async fn test_participant_gets_messages_newest_first_with_java_shape() {
    let db = TestDb::new()
        .await
        .expect("Failed to initialize test database");
    let (creator, appointment_id) = setup_appointment(&db, "msg_creator").await;
    let older = insert_message(
        &db,
        appointment_id,
        &creator,
        "first",
        "2026-10-01T10:00:00Z",
    )
    .await;
    let newer = insert_message(
        &db,
        appointment_id,
        &creator,
        "second",
        "2026-10-01T10:00:00.123Z",
    )
    .await;

    let (status, body) = get_messages(&db, &creator, appointment_id).await;

    assert_eq!(status, StatusCode::OK);
    let messages = body.as_array().expect("array body");
    assert_eq!(messages.len(), 2);
    assert_eq!(messages[0]["id"], newer);
    assert_eq!(messages[0]["body"], "second");
    assert_eq!(messages[0]["timestamp"], "2026-10-01T10:00:00.123Z");
    assert_eq!(messages[1]["id"], older);
    assert_eq!(messages[1]["timestamp"], "2026-10-01T10:00:00Z");
    assert_eq!(messages[0]["sender_id"], creator.as_str());
    assert_eq!(messages[0]["appointment_id"], appointment_id);
    assert_eq!(messages[0]["sender_name"], "Test User");
}

#[tokio::test]
async fn test_only_messages_of_the_appointment_are_returned() {
    let db = TestDb::new()
        .await
        .expect("Failed to initialize test database");
    let (creator, appointment_id) = setup_appointment(&db, "scope_creator").await;
    let (other_creator, other_appointment) = setup_appointment(&db, "scope_other").await;
    insert_message(
        &db,
        appointment_id,
        &creator,
        "mine",
        "2026-10-01T10:00:00Z",
    )
    .await;
    insert_message(
        &db,
        other_appointment,
        &other_creator,
        "theirs",
        "2026-10-01T10:00:00Z",
    )
    .await;

    let (_, body) = get_messages(&db, &creator, appointment_id).await;

    let messages = body.as_array().expect("array body");
    assert_eq!(messages.len(), 1);
    assert_eq!(messages[0]["body"], "mine");
}

#[tokio::test]
async fn test_empty_list_when_no_messages() {
    let db = TestDb::new()
        .await
        .expect("Failed to initialize test database");
    let (creator, appointment_id) = setup_appointment(&db, "empty_creator").await;

    let (status, body) = get_messages(&db, &creator, appointment_id).await;

    assert_eq!(status, StatusCode::OK);
    assert_eq!(body, serde_json::json!([]));
}

#[tokio::test]
async fn test_pending_participant_may_read() {
    let db = TestDb::new()
        .await
        .expect("Failed to initialize test database");
    let (creator, appointment_id) = setup_appointment(&db, "pending_msg_creator").await;
    let fixtures = TestFixtures::new(db.pool().clone());
    let guest = fixtures
        .create_user("pending_msg_guest", "guest@example.com")
        .await
        .expect("Failed to create guest");
    fixtures
        .add_participant(appointment_id, &guest, "PENDING")
        .await
        .expect("Failed to add participant");
    insert_message(
        &db,
        appointment_id,
        &creator,
        "hello",
        "2026-10-01T10:00:00Z",
    )
    .await;

    let (status, body) = get_messages(&db, &guest, appointment_id).await;

    assert_eq!(status, StatusCode::OK);
    assert_eq!(body.as_array().expect("array body").len(), 1);
}

#[tokio::test]
async fn test_non_participant_is_forbidden() {
    let db = TestDb::new()
        .await
        .expect("Failed to initialize test database");
    let (creator, appointment_id) = setup_appointment(&db, "forbidden_creator").await;
    insert_message(
        &db,
        appointment_id,
        &creator,
        "secret",
        "2026-10-01T10:00:00Z",
    )
    .await;
    let stranger = TestFixtures::new(db.pool().clone())
        .create_user("forbidden_stranger", "stranger@example.com")
        .await
        .expect("Failed to create stranger");

    let (status, _) = get_messages(&db, &stranger, appointment_id).await;

    assert_eq!(status, StatusCode::FORBIDDEN);
}

#[tokio::test]
async fn test_unknown_appointment_is_forbidden() {
    let db = TestDb::new()
        .await
        .expect("Failed to initialize test database");
    let (creator, _) = setup_appointment(&db, "unknown_creator").await;

    // Java derives the role NONE for an unknown appointment and answers 403
    let (status, _) = get_messages(&db, &creator, 999_999).await;

    assert_eq!(status, StatusCode::FORBIDDEN);
}

#[tokio::test]
async fn test_sender_without_profile_is_named_deleted_user() {
    let db = TestDb::new()
        .await
        .expect("Failed to initialize test database");
    let (creator, appointment_id) = setup_appointment(&db, "ghost_creator").await;
    insert_message(
        &db,
        appointment_id,
        "ghost-user",
        "boo",
        "2026-10-01T10:00:00Z",
    )
    .await;

    let (_, body) = get_messages(&db, &creator, appointment_id).await;

    assert_eq!(body[0]["sender_id"], "ghost-user");
    assert_eq!(body[0]["sender_name"], "Gelöschter Benutzer");
}
