use async_trait::async_trait;
/// Integration tests for GET and POST /api/v2/appointments/:id/messages
///
/// Behaviour mirrors the Java backend (MessageResource#getMessagesForEvent / MessageService#getMessages):
/// - any participant (role other than NONE) may read, everybody else gets 403
/// - messages are ordered newest first
/// - body fields: id, sender_id, sender_name, appointment_id, body, timestamp
///
/// POST (MessageResource#sendMessage / MessageService#sendMessage):
/// - any participant (role other than NONE) may post, everybody else gets 403
/// - answers 200 (Java `Response.ok`, not 201) with the stored message, `sender_name` taken from the caller's token
/// - fires `MessageSentEvent {message_id}` after the message is stored
use axum::{
    body::Body,
    extract::Request,
    http::StatusCode,
    middleware::{self, Next},
    routing::get,
    Router,
};
use chronos_date_api::appointments::handlers::{list_messages, send_message, AppState};
use chronos_date_api::event_bus::{postgres::PostgresEventBus, Event, EventPublisher};
use chronos_date_api::security::PrincipalContext;
use chronos_date_api::test_utils::{AppointmentFixture, TestDb, TestFixtures};
use std::sync::{Arc, Mutex};
use tower::Service;

/// Builds a router with the messages route, authenticated as `user_id`
fn router_for(state: Arc<AppState>, user_id: String) -> Router {
    router_for_principal(state, PrincipalContext::new(user_id))
}

/// Builds a router with the messages route, authenticated as `principal`
fn router_for_principal(state: Arc<AppState>, principal: PrincipalContext) -> Router {
    Router::new()
        .route(
            "/api/v2/appointments/:id/messages",
            get(list_messages).post(send_message),
        )
        .with_state(state)
        .layer(middleware::from_fn(
            move |mut request: Request, next: Next| {
                let principal = principal.clone();
                async move {
                    request.extensions_mut().insert(Arc::new(principal));
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

/// Collects fired events instead of publishing them
#[derive(Default)]
struct RecordingPublisher {
    events: Mutex<Vec<Event>>,
}

#[async_trait]
impl EventPublisher for RecordingPublisher {
    async fn fire(&self, event: Event) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        self.events.lock().unwrap().push(event);
        Ok(())
    }
}

async fn post_message(
    db: &TestDb,
    principal: PrincipalContext,
    appointment_id: i64,
    payload: &str,
) -> (StatusCode, serde_json::Value, Vec<Event>) {
    let publisher = Arc::new(RecordingPublisher::default());
    let state = Arc::new(AppState {
        db_pool: db.pool().clone(),
        event_publisher: publisher.clone(),
    });
    let mut router = router_for_principal(state, principal);
    let request = http::Request::builder()
        .uri(format!("/api/v2/appointments/{}/messages", appointment_id))
        .method("POST")
        .header("content-type", "application/json")
        .body(Body::from(payload.to_string()))
        .expect("Failed to build request");

    let response = router.call(request).await.expect("Router call failed");
    let status = response.status();
    let bytes = axum::body::to_bytes(response.into_body(), usize::MAX)
        .await
        .expect("Failed to read body");
    let body = serde_json::from_slice(&bytes).unwrap_or(serde_json::Value::Null);
    let events = publisher.events.lock().unwrap().clone();
    (status, body, events)
}

#[tokio::test]
async fn test_post_stores_message_and_answers_200_with_java_shape() {
    let db = TestDb::new()
        .await
        .expect("Failed to initialize test database");
    let (creator, appointment_id) = setup_appointment(&db, "post_creator").await;
    let principal = PrincipalContext::new(creator.clone()).with_profile(
        Some("Erika".into()),
        Some("Muster".into()),
        None,
        None,
    );

    let (status, body, _) =
        post_message(&db, principal, appointment_id, r#"{"body":"Hallo"}"#).await;

    assert_eq!(status, StatusCode::OK);
    assert_eq!(body["body"], "Hallo");
    assert_eq!(body["sender_id"], creator.as_str());
    assert_eq!(body["sender_name"], "Erika Muster");
    assert_eq!(body["appointment_id"], appointment_id);
    assert!(body["timestamp"].as_str().unwrap().ends_with('Z'));
    let (stored_body, stored_sender): (Option<String>, String) =
        sqlx::query_as("SELECT body, sender_oidcid FROM message WHERE id = $1")
            .bind(body["id"].as_i64().expect("numeric id"))
            .fetch_one(db.pool())
            .await
            .expect("message stored");
    assert_eq!(stored_body.as_deref(), Some("Hallo"));
    assert_eq!(stored_sender, creator);
}

#[tokio::test]
async fn test_post_fires_message_sent_event_with_message_id() {
    let db = TestDb::new()
        .await
        .expect("Failed to initialize test database");
    let (creator, appointment_id) = setup_appointment(&db, "event_creator").await;

    let (_, body, events) = post_message(
        &db,
        PrincipalContext::new(creator),
        appointment_id,
        r#"{"body":"ping"}"#,
    )
    .await;

    assert_eq!(events.len(), 1);
    assert_eq!(events[0].event_type, "MessageSentEvent");
    assert_eq!(
        events[0].payload,
        serde_json::json!({"message_id": body["id"]})
    );
}

#[tokio::test]
async fn test_post_by_pending_participant_is_allowed() {
    let db = TestDb::new()
        .await
        .expect("Failed to initialize test database");
    let (_, appointment_id) = setup_appointment(&db, "post_pending_creator").await;
    let fixtures = TestFixtures::new(db.pool().clone());
    let guest = fixtures
        .create_user("post_pending_guest", "pg@example.com")
        .await
        .expect("Failed to create guest");
    fixtures
        .add_participant(appointment_id, &guest, "PENDING")
        .await
        .expect("Failed to add participant");

    let (status, _, _) = post_message(
        &db,
        PrincipalContext::new(guest),
        appointment_id,
        r#"{"body":"hi"}"#,
    )
    .await;

    assert_eq!(status, StatusCode::OK);
}

#[tokio::test]
async fn test_post_by_non_participant_is_forbidden_and_stores_nothing() {
    let db = TestDb::new()
        .await
        .expect("Failed to initialize test database");
    let (_, appointment_id) = setup_appointment(&db, "post_forbidden_creator").await;
    let stranger = TestFixtures::new(db.pool().clone())
        .create_user("post_stranger", "ps@example.com")
        .await
        .expect("Failed to create stranger");

    let (status, _, events) = post_message(
        &db,
        PrincipalContext::new(stranger),
        appointment_id,
        r#"{"body":"let me in"}"#,
    )
    .await;

    assert_eq!(status, StatusCode::FORBIDDEN);
    assert!(events.is_empty());
    let count: i64 = sqlx::query_scalar("SELECT count(*) FROM message WHERE appointment_id = $1")
        .bind(appointment_id)
        .fetch_one(db.pool())
        .await
        .unwrap();
    assert_eq!(count, 0);
}

#[tokio::test]
async fn test_post_to_unknown_appointment_is_forbidden() {
    let db = TestDb::new()
        .await
        .expect("Failed to initialize test database");
    let (creator, _) = setup_appointment(&db, "post_unknown_creator").await;

    let (status, _, _) = post_message(
        &db,
        PrincipalContext::new(creator),
        999_999,
        r#"{"body":"x"}"#,
    )
    .await;

    assert_eq!(status, StatusCode::FORBIDDEN);
}

#[tokio::test]
async fn test_post_accepts_blank_body_like_java() {
    let db = TestDb::new()
        .await
        .expect("Failed to initialize test database");
    let (creator, appointment_id) = setup_appointment(&db, "post_blank_creator").await;

    let (status, body, _) = post_message(
        &db,
        PrincipalContext::new(creator),
        appointment_id,
        r#"{"body":"   "}"#,
    )
    .await;

    assert_eq!(status, StatusCode::OK);
    assert_eq!(body["body"], "   ");
}

#[tokio::test]
async fn test_posted_message_is_listed() {
    let db = TestDb::new()
        .await
        .expect("Failed to initialize test database");
    let (creator, appointment_id) = setup_appointment(&db, "post_list_creator").await;

    post_message(
        &db,
        PrincipalContext::new(creator.clone()),
        appointment_id,
        r#"{"body":"sichtbar"}"#,
    )
    .await;
    let (_, listed) = get_messages(&db, &creator, appointment_id).await;

    assert_eq!(listed[0]["body"], "sichtbar");
}
