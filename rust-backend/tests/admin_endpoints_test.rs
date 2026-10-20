//! Integration tests for `/api/v2/admin/*` (Java: the `Admin*Resource` classes, ADR-2026-10-20-023).
//! Requests go through the production router incl. authentication; the admin is a user whose
//! access token carries the realm role `ADMIN_API`.

mod common;

use async_trait::async_trait;
use axum::http::StatusCode;
use chronos_date_api::keycloak::KeycloakUser;
use chronos_date_api::push_notifications::{PushSender, PushSubscription};
use chronos_date_api::test_utils::FakeKeycloak;
use common::TestApp;
use serde_json::{json, Value};
use std::sync::{Arc, Mutex};

const ADMIN: &str = "admin";
const USER: &str = "alice";

/// Remembers (endpoint, payload) of every push
#[derive(Default)]
struct RecordingSender {
    sent: Mutex<Vec<(String, Value)>>,
}

#[async_trait]
impl PushSender for RecordingSender {
    async fn send(&self, subscription: &PushSubscription, payload: &str) -> Result<u16, String> {
        self.sent.lock().unwrap().push((
            subscription.endpoint.clone(),
            serde_json::from_str(payload).expect("payload is JSON"),
        ));
        Ok(201)
    }
}

async fn app() -> TestApp {
    let app = TestApp::new().await;
    prepare(&app).await;
    app
}

async fn prepare(app: &TestApp) {
    app.add_user(ADMIN, "Ada", "Admin", "ada@example.com").await;
    app.add_user(USER, "Alice", "Anders", "alice@example.com")
        .await;
    app.add_user("bob", "Bob", "Becker", "bob@example.com")
        .await;
    app.grant_admin(ADMIN);
}

async fn appointment(app: &TestApp, owner: &str, name: &str) -> i64 {
    let (status, body) = app
        .post(
            owner,
            "/api/v2/appointments",
            Some(json!({"name": name, "start": "2030-01-01T10:00:00Z", "end": "2030-01-01T11:00:00Z"})),
        )
        .await;
    assert_eq!(status, StatusCode::CREATED, "{body}");
    body["id"].as_i64().unwrap()
}

async fn count(app: &TestApp, sql: &str) -> i64 {
    sqlx::query_scalar::<_, i64>(sql)
        .fetch_one(app.db.pool())
        .await
        .unwrap()
}

fn assert_error(body: &Value, status: u16, code: &str, message: &str) {
    assert_eq!(body["status"], json!(status), "{body}");
    assert_eq!(body["errorCode"], json!(code), "{body}");
    assert_eq!(body["message"], json!(message), "{body}");
}

// ===== authorization =====

const ENDPOINTS: [(&str, &str); 14] = [
    ("POST", "/api/v2/admin/appointments"),
    ("POST", "/api/v2/admin/appointments/1/participants/groups"),
    ("POST", "/api/v2/admin/appointments/participants/status"),
    ("POST", "/api/v2/admin/appointments/message"),
    ("GET", "/api/v2/admin/appointments"),
    ("POST", "/api/v2/admin/friendship/befriend"),
    ("POST", "/api/v2/admin/groups"),
    ("POST", "/api/v2/admin/groups/1/users/bob"),
    ("POST", "/api/v2/admin/push/test/bob"),
    ("GET", "/api/v2/admin/push/log"),
    ("GET", "/api/v2/admin/stats"),
    ("GET", "/api/v2/admin/users"),
    ("GET", "/api/v2/admin/users/bob"),
    ("DELETE", "/api/v2/admin/users/bob"),
];

#[tokio::test]
async fn test_every_admin_endpoint_requires_authentication() {
    let app = app().await;
    for (method, uri) in ENDPOINTS {
        let (status, _) = app.call(None, method, uri, None).await;
        assert_eq!(status, StatusCode::UNAUTHORIZED, "{method} {uri}");
    }
}

#[tokio::test]
async fn test_every_admin_endpoint_answers_an_empty_403_to_a_normal_token() {
    let app = app().await;
    for (method, uri) in ENDPOINTS {
        let (status, body) = app.call(Some(USER), method, uri, Some(json!({}))).await;
        assert_eq!(status, StatusCode::FORBIDDEN, "{method} {uri}");
        assert_eq!(body, Value::Null, "{method} {uri}");
    }
}

#[tokio::test]
async fn test_a_normal_token_changes_nothing() {
    let app = app().await;
    let (status, _) = app
        .post(
            USER,
            "/api/v2/admin/groups",
            Some(json!({"ownerId": USER, "groupName": "Hack"})),
        )
        .await;
    assert_eq!(status, StatusCode::FORBIDDEN);
    assert_eq!(count(&app, "SELECT COUNT(*) FROM groups").await, 0);
}

// ===== POST /appointments =====

#[tokio::test]
async fn test_admin_creates_an_appointment_and_is_its_responsible_participant() {
    let app = app().await;
    let (status, body) = app
        .post(
            ADMIN,
            "/api/v2/admin/appointments",
            Some(json!({"name": "Sommerlager", "start": "2030-07-01T08:00:00Z", "end": "2030-07-08T08:00:00Z", "minimal_attendees": 3})),
        )
        .await;
    assert_eq!(status, StatusCode::OK, "{body}");
    assert_eq!(body["name"], json!("Sommerlager"));
    assert_eq!(body["status"], json!("PLANNED"));
    assert_eq!(body["minimal_attendees"], json!(3));
    let id = body["id"].as_i64().unwrap();
    let role = sqlx::query_scalar::<_, String>(
        "SELECT role FROM appointment_participation WHERE appointment_id = $1 AND user_oidcid = $2",
    )
    .bind(id)
    .bind(ADMIN)
    .fetch_one(app.db.pool())
    .await
    .unwrap();
    assert_eq!(role, "RESPONSIBLE");
}

#[tokio::test]
async fn test_admin_appointment_accepts_the_trailing_slash_of_the_java_path() {
    let app = app().await;
    let (status, _) = app
        .post(
            ADMIN,
            "/api/v2/admin/appointments/",
            Some(json!({"name": "X", "start": "2030-07-01T08:00:00Z", "end": "2030-07-01T09:00:00Z"})),
        )
        .await;
    assert_eq!(status, StatusCode::OK);
}

#[tokio::test]
async fn test_admin_create_appointment_without_body_is_an_internal_error() {
    let app = app().await;
    let (status, body) = app.post(ADMIN, "/api/v2/admin/appointments", None).await;
    assert_eq!(status, StatusCode::INTERNAL_SERVER_ERROR);
    assert_error(
        &body,
        500,
        "INTERNAL_ERROR",
        "Ein interner Fehler ist aufgetreten",
    );
}

// ===== POST /appointments/{id}/participants/groups =====

#[tokio::test]
async fn test_admin_adds_a_group_to_an_appointment_it_does_not_own() {
    let app = app().await;
    let appointment_id = appointment(&app, USER, "Gruppenstunde").await;
    let group = app.add_group("bob", "Meute").await;

    let (status, _) = app
        .post(
            ADMIN,
            &format!("/api/v2/admin/appointments/{appointment_id}/participants/groups"),
            Some(json!({"group_id": group, "user_role": "GUEST"})),
        )
        .await;

    assert_eq!(status, StatusCode::OK);
    let role = sqlx::query_scalar::<_, String>(
        "SELECT role FROM appointment_group_participations WHERE appointment_id = $1 AND group_id = $2",
    )
    .bind(appointment_id)
    .bind(group)
    .fetch_one(app.db.pool())
    .await
    .unwrap();
    assert_eq!(role, "GUEST");
    assert_eq!(app.events("AppointmentGroupAddedEvent").await.len(), 1);
}

#[tokio::test]
async fn test_admin_add_group_participant_failures() {
    let app = app().await;
    let appointment_id = appointment(&app, USER, "Gruppenstunde").await;
    let group = app.add_group("bob", "Meute").await;
    let uri = format!("/api/v2/admin/appointments/{appointment_id}/participants/groups");

    let (status, _) = app
        .post(
            ADMIN,
            &uri,
            Some(json!({"group_id": group, "user_role": "BOSS"})),
        )
        .await;
    assert_eq!(status, StatusCode::INTERNAL_SERVER_ERROR, "unknown role");

    let (status, _) = app
        .post(
            ADMIN,
            "/api/v2/admin/appointments/999/participants/groups",
            Some(json!({"group_id": group, "user_role": "GUEST"})),
        )
        .await;
    assert_eq!(status, StatusCode::NOT_FOUND, "unknown appointment");

    let (status, _) = app
        .post(
            ADMIN,
            "/api/v2/admin/appointments/abc/participants/groups",
            Some(json!({"group_id": group, "user_role": "GUEST"})),
        )
        .await;
    assert_eq!(status, StatusCode::NOT_FOUND, "non-numeric id");

    let body = Some(json!({"group_id": group, "user_role": "GUEST"}));
    assert_eq!(app.post(ADMIN, &uri, body.clone()).await.0, StatusCode::OK);
    assert_eq!(
        app.post(ADMIN, &uri, body).await.0,
        StatusCode::BAD_REQUEST,
        "group already participates"
    );
}

// ===== POST /appointments/participants/status =====

#[tokio::test]
async fn test_admin_changes_the_participation_status_of_a_user() {
    let app = app().await;
    let appointment_id = appointment(&app, USER, "Treffen").await;

    let (status, _) = app
        .post(
            ADMIN,
            "/api/v2/admin/appointments/participants/status",
            Some(json!({"userId": USER, "appointmentId": appointment_id, "participationStatus": "REJECTED"})),
        )
        .await;

    assert_eq!(status, StatusCode::OK);
    let stored = sqlx::query_scalar::<_, String>(
        "SELECT status FROM appointment_participation WHERE appointment_id = $1 AND user_oidcid = $2",
    )
    .bind(appointment_id)
    .bind(USER)
    .fetch_one(app.db.pool())
    .await
    .unwrap();
    assert_eq!(stored, "REJECTED");
    assert_eq!(
        app.events("AppointmentParticipationStatusChangedEvent")
            .await
            .len(),
        1
    );
}

#[tokio::test]
async fn test_admin_change_participation_status_failures() {
    let app = app().await;
    let appointment_id = appointment(&app, USER, "Treffen").await;
    let uri = "/api/v2/admin/appointments/participants/status";
    let change = |user: &str, status: Value| {
        Some(
            json!({"userId": user, "appointmentId": appointment_id, "participationStatus": status}),
        )
    };

    assert_eq!(
        app.post(ADMIN, uri, change(USER, json!("PENDING"))).await.0,
        StatusCode::BAD_REQUEST,
        "back to pending"
    );
    assert_eq!(
        app.post(ADMIN, uri, change("bob", json!("APPROVED")))
            .await
            .0,
        StatusCode::BAD_REQUEST,
        "not a participant"
    );
    assert_eq!(
        app.post(ADMIN, uri, change(USER, json!("MAYBE"))).await.0,
        StatusCode::INTERNAL_SERVER_ERROR,
        "unknown status"
    );
    assert_eq!(
        app.post(ADMIN, uri, change(USER, Value::Null)).await.0,
        StatusCode::INTERNAL_SERVER_ERROR,
        "missing status"
    );
}

// ===== POST /appointments/message =====

#[tokio::test]
async fn test_admin_sends_a_message_for_any_sender_and_time() {
    let app = app().await;
    let appointment_id = appointment(&app, USER, "Treffen").await;

    let (status, _) = app
        .post(
            ADMIN,
            "/api/v2/admin/appointments/message",
            Some(json!({"appointment_id": appointment_id, "body": "Bis gleich", "sender_id": "bob", "timestamp": "2030-01-01T09:30:00Z"})),
        )
        .await;

    assert_eq!(status, StatusCode::OK);
    let (message_id, body, sender, timestamp): (i64, String, String, String) = sqlx::query_as(
        "SELECT id, body, sender_oidcid, to_char(\"timestamp\" AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS') FROM message WHERE appointment_id = $1",
    )
    .bind(appointment_id)
    .fetch_one(app.db.pool())
    .await
    .unwrap();
    assert_eq!(
        (body.as_str(), sender.as_str(), timestamp.as_str()),
        ("Bis gleich", "bob", "2030-01-01T09:30:00")
    );
    assert_eq!(
        app.events("MessageSentEvent").await,
        vec![json!({"message_id": message_id})]
    );
}

#[tokio::test]
async fn test_admin_message_with_a_bad_timestamp_is_an_internal_error() {
    let app = app().await;
    let (status, _) = app
        .post(
            ADMIN,
            "/api/v2/admin/appointments/message",
            Some(json!({"appointment_id": 1, "body": "x", "sender_id": "bob", "timestamp": "yesterday"})),
        )
        .await;
    assert_eq!(status, StatusCode::INTERNAL_SERVER_ERROR);
    assert_eq!(count(&app, "SELECT COUNT(*) FROM message").await, 0);
}

// ===== GET /appointments =====

#[tokio::test]
async fn test_admin_lists_all_appointments_of_any_status_with_paging_meta() {
    let app = app().await;
    let first = appointment(&app, USER, "Eins").await;
    let second = appointment(&app, USER, "Zwei").await;
    let third = appointment(&app, "bob", "Drei").await;
    app.post(USER, &format!("/api/v2/appointments/{second}/cancel"), None)
        .await;
    app.delete("bob", &format!("/api/v2/appointments/{third}"))
        .await;

    let (status, body) = app.get(ADMIN, "/api/v2/admin/appointments?size=2").await;
    assert_eq!(status, StatusCode::OK, "{body}");
    assert_eq!(body["meta"], json!({"page": 0, "size": 2, "total": 3}));
    let ids: Vec<i64> = body["data"]
        .as_array()
        .unwrap()
        .iter()
        .map(|a| a["id"].as_i64().unwrap())
        .collect();
    assert_eq!(ids, vec![first, second]);
    assert_eq!(body["data"][1]["status"], json!("CANCELLED"));

    let (_, page2) = app
        .get(ADMIN, "/api/v2/admin/appointments/?page=1&size=2")
        .await;
    assert_eq!(page2["meta"], json!({"page": 1, "size": 2, "total": 3}));
    assert_eq!(page2["data"][0]["status"], json!("DELETED"));
    assert_eq!(page2["data"].as_array().unwrap().len(), 1);
}

#[tokio::test]
async fn test_admin_list_appointments_defaults_and_invalid_paging() {
    let app = app().await;
    let (_, body) = app.get(ADMIN, "/api/v2/admin/appointments").await;
    assert_eq!(
        body,
        json!({"data": [], "meta": {"page": 0, "size": 20, "total": 0}})
    );
    for uri in ["?size=0", "?page=-1"] {
        let (status, _) = app
            .get(ADMIN, &format!("/api/v2/admin/appointments{uri}"))
            .await;
        assert_eq!(status, StatusCode::INTERNAL_SERVER_ERROR, "{uri}");
    }
    let (status, _) = app.get(ADMIN, "/api/v2/admin/appointments?page=abc").await;
    assert_eq!(status, StatusCode::NOT_FOUND);
}

// ===== POST /friendship/befriend =====

async fn friendship_rows(app: &TestApp) -> Vec<(String, String, String)> {
    sqlx::query_as(
        "SELECT requester_oidcid, addressee_oidcid, status FROM friendship_requests ORDER BY id",
    )
    .fetch_all(app.db.pool())
    .await
    .unwrap()
}

#[tokio::test]
async fn test_admin_befriends_everybody_with_everybody_once() {
    let app = app().await;
    let uri = "/api/v2/admin/friendship/befriend";
    let body = Some(json!({"userIds": ["a", "b", "c", "a"]}));

    assert_eq!(app.post(ADMIN, uri, body.clone()).await.0, StatusCode::OK);
    assert_eq!(app.post(ADMIN, uri, body).await.0, StatusCode::OK);

    let rows = friendship_rows(&app).await;
    assert_eq!(rows.len(), 3, "{rows:?}");
    assert!(rows.iter().all(|row| row.2 == "ACCEPTED"));
    let responded = count(
        &app,
        "SELECT COUNT(*) FROM friendship_requests WHERE responded_at IS NOT NULL",
    )
    .await;
    assert_eq!(responded, 3);
    assert!(app.events("FriendshipAcceptedEvent").await.is_empty());
}

#[tokio::test]
async fn test_admin_befriend_keeps_pending_and_replaces_declined_requests() {
    let app = app().await;
    app.add_friendship("a", "b", "PENDING").await;
    let declined = app.add_friendship("c", "a", "DECLINED").await;

    let (status, _) = app
        .post(
            ADMIN,
            "/api/v2/admin/friendship/befriend",
            Some(json!({"userIds": ["a", "b", "c"]})),
        )
        .await;

    assert_eq!(status, StatusCode::OK);
    assert_eq!(app.friendship_status(declined).await, None);
    let rows = friendship_rows(&app).await;
    assert!(
        rows.contains(&("a".into(), "b".into(), "PENDING".into())),
        "{rows:?}"
    );
    assert!(
        rows.contains(&("a".into(), "c".into(), "ACCEPTED".into())),
        "{rows:?}"
    );
    assert!(
        rows.contains(&("b".into(), "c".into(), "ACCEPTED".into())),
        "{rows:?}"
    );
    assert_eq!(rows.len(), 3);
}

#[tokio::test]
async fn test_admin_befriend_without_body_is_a_400_and_without_ids_a_500() {
    let app = app().await;
    let uri = "/api/v2/admin/friendship/befriend";
    let (status, body) = app.post(ADMIN, uri, None).await;
    assert_eq!((status, body), (StatusCode::BAD_REQUEST, Value::Null));
    assert_eq!(
        app.post(ADMIN, uri, Some(json!({}))).await.0,
        StatusCode::INTERNAL_SERVER_ERROR
    );
}

// ===== groups =====

#[tokio::test]
async fn test_admin_creates_a_group_for_an_owner() {
    let app = app().await;
    let (status, body) = app
        .post(
            ADMIN,
            "/api/v2/admin/groups",
            Some(json!({"ownerId": USER, "groupName": "Meute"})),
        )
        .await;
    assert_eq!(status, StatusCode::CREATED, "{body}");
    let id = body.as_i64().expect("the body is the bare group id");
    assert_eq!(app.members_of(id).await, vec![USER.to_string()]);
    let name = sqlx::query_scalar::<_, String>("SELECT groupname FROM groups WHERE id = $1")
        .bind(id)
        .fetch_one(app.db.pool())
        .await
        .unwrap();
    assert_eq!(name, "Meute");
    assert_eq!(app.events("GroupCreatedEvent").await.len(), 1);
}

#[tokio::test]
async fn test_admin_create_group_validation() {
    let app = app().await;
    let uri = "/api/v2/admin/groups";
    for body in [None, Some(json!({"groupName": "Ohne Owner"}))] {
        let (status, response) = app.post(ADMIN, uri, body).await;
        assert_eq!((status, response), (StatusCode::BAD_REQUEST, Value::Null));
    }
    let (status, body) = app.post(ADMIN, uri, Some(json!({"ownerId": USER}))).await;
    assert_eq!(status, StatusCode::BAD_REQUEST);
    assert_error(&body, 400, "VALIDATION_ERROR", "Group name is required");
    assert_eq!(count(&app, "SELECT COUNT(*) FROM groups").await, 0);
}

#[tokio::test]
async fn test_admin_adds_any_user_to_a_group_without_friendship() {
    let app = app().await;
    let group = app.add_group(USER, "Meute").await;
    let uri = format!("/api/v2/admin/groups/{group}/users/bob");

    assert_eq!(app.post(ADMIN, &uri, None).await.0, StatusCode::OK);
    // already a member: Java swallows the ValidationException
    assert_eq!(app.post(ADMIN, &uri, None).await.0, StatusCode::OK);

    assert_eq!(
        app.members_of(group).await,
        vec![USER.to_string(), "bob".to_string()]
    );
    assert_eq!(app.events("GroupMemberAddedEvent").await.len(), 1);
}

#[tokio::test]
async fn test_admin_add_group_member_to_an_unknown_group_is_a_404() {
    let app = app().await;
    let (status, body) = app
        .post(ADMIN, "/api/v2/admin/groups/999/users/bob", None)
        .await;
    assert_eq!(status, StatusCode::NOT_FOUND);
    assert_eq!(body["errorCode"], json!("RESOURCE_NOT_FOUND"));
    let (status, _) = app
        .post(ADMIN, "/api/v2/admin/groups/abc/users/bob", None)
        .await;
    assert_eq!(status, StatusCode::NOT_FOUND);
}

// ===== push =====

async fn push_app() -> (TestApp, Arc<RecordingSender>) {
    let sender = Arc::new(RecordingSender::default());
    let app = TestApp::with_collaborators(FakeKeycloak::new(), Some(sender.clone())).await;
    prepare(&app).await;
    sqlx::query(
        "INSERT INTO pushsubscription (id, user_oidcid, endpoint, p256dh, auth) VALUES (nextval('pushsubscription_seq'), 'bob', 'https://push.test/bob', 'p', 'a')",
    )
    .execute(app.db.pool())
    .await
    .unwrap();
    (app, sender)
}

#[tokio::test]
async fn test_admin_push_test_sends_the_given_payload_and_answers_204() {
    let (app, sender) = push_app().await;
    let (status, _) = app
        .call(
            Some(ADMIN),
            "POST",
            "/api/v2/admin/push/test/bob",
            Some(json!({"type": "CUSTOM", "n": 1})),
        )
        .await;
    assert_eq!(status, StatusCode::NO_CONTENT);
    assert_eq!(
        *sender.sent.lock().unwrap(),
        vec![(
            "https://push.test/bob".to_string(),
            json!({"type": "CUSTOM", "n": 1})
        )]
    );
}

#[tokio::test]
async fn test_admin_push_test_without_payload_sends_the_greeting() {
    let (app, sender) = push_app().await;
    let (status, _) = app.post(ADMIN, "/api/v2/admin/push/test/bob", None).await;
    assert_eq!(status, StatusCode::NO_CONTENT);
    assert_eq!(
        sender.sent.lock().unwrap()[0].1,
        json!({"title": "\"Testbenachrichtigung\"\n", "body": "\"Push\" funktioniert!\n"})
    );
}

async fn log_entry(app: &TestApp, user: &str, kind: Option<&str>, success: bool, created: &str) {
    sqlx::query(
        "INSERT INTO push_notification_log (id, user_oidcid, notification_type, payload, endpoint, http_status_code, success, error_message, created_at)
         VALUES (nextval('push_notification_log_seq'), $1, $2, '{}', 'https://push.test/x', $3, $4, $5, $6::timestamptz)",
    )
    .bind(user)
    .bind(kind)
    .bind(if success { Some(201) } else { None })
    .bind(success)
    .bind(if success { None } else { Some("boom") })
    .bind(created)
    .execute(app.db.pool())
    .await
    .unwrap();
}

#[tokio::test]
async fn test_admin_push_log_is_filtered_paged_and_newest_first() {
    let app = app().await;
    log_entry(
        &app,
        "bob",
        Some("APPOINTMENT_MOVED"),
        true,
        "2030-01-01T10:00:00Z",
    )
    .await;
    log_entry(&app, "bob", None, false, "2030-01-02T10:00:00.5Z").await;
    log_entry(
        &app,
        USER,
        Some("APPOINTMENT_MOVED"),
        true,
        "2030-01-03T10:00:00Z",
    )
    .await;

    let (status, all) = app.get(ADMIN, "/api/v2/admin/push/log").await;
    assert_eq!(status, StatusCode::OK);
    let users: Vec<&str> = all
        .as_array()
        .unwrap()
        .iter()
        .map(|e| e["user_id"].as_str().unwrap())
        .collect();
    assert_eq!(users, vec![USER, "bob", "bob"]);
    assert_eq!(
        all[1],
        json!({
            "id": all[1]["id"], "user_id": "bob", "notification_type": null, "payload": "{}",
            "endpoint": "https://push.test/x", "http_status_code": null, "success": false,
            "error_message": "boom", "created_at": "2030-01-02T10:00:00.500Z"
        })
    );
    assert_eq!(all[0]["created_at"], json!("2030-01-03T10:00:00Z"));

    let filtered = |query: &'static str| {
        let app = &app;
        async move {
            app.get(ADMIN, &format!("/api/v2/admin/push/log?{query}"))
                .await
                .1
                .as_array()
                .unwrap()
                .len()
        }
    };
    assert_eq!(filtered("user_id=bob").await, 2);
    assert_eq!(filtered("success=false").await, 1);
    assert_eq!(
        filtered("success=true&notification_type=APPOINTMENT_MOVED").await,
        2
    );
    assert_eq!(
        filtered("from=2030-01-02T00:00:00Z&to=2030-01-02T23:59:59Z").await,
        1
    );
    assert_eq!(filtered("size=1&page=2").await, 1);
    assert_eq!(filtered("size=1&page=3").await, 0);
    let (status, _) = app.get(ADMIN, "/api/v2/admin/push/log?from=gestern").await;
    assert_eq!(status, StatusCode::INTERNAL_SERVER_ERROR);
}

// ===== GET /stats =====

#[tokio::test]
async fn test_admin_statistics_count_rows_and_keycloak_users() {
    let keycloak = FakeKeycloak::new().with_realm_users(vec![
        KeycloakUser {
            id: Some("u1".into()),
            ..Default::default()
        },
        KeycloakUser {
            id: Some("u2".into()),
            ..Default::default()
        },
    ]);
    let app = TestApp::with_collaborators(keycloak, None).await;
    prepare(&app).await;
    let planned = appointment(&app, USER, "Eins").await;
    let cancelled = appointment(&app, USER, "Zwei").await;
    let deleted = appointment(&app, USER, "Drei").await;
    app.post(
        USER,
        &format!("/api/v2/appointments/{cancelled}/cancel"),
        None,
    )
    .await;
    app.delete(USER, &format!("/api/v2/appointments/{deleted}"))
        .await;
    for (appointment_id, status) in [
        (planned, "PENDING"),
        (cancelled, "REJECTED"),
        (deleted, "APPROVED"),
    ] {
        sqlx::query("UPDATE appointment_participation SET status = $1 WHERE appointment_id = $2")
            .bind(status)
            .bind(appointment_id)
            .execute(app.db.pool())
            .await
            .unwrap();
    }
    let group = app.add_group(USER, "Meute").await;
    app.add_member(group, "bob").await;
    app.add_friendship(USER, "bob", "ACCEPTED").await;
    app.add_friendship(USER, ADMIN, "PENDING").await;
    app.add_friendship("bob", ADMIN, "DECLINED").await;
    log_entry(&app, "bob", None, true, "2030-01-01T10:00:00Z").await;
    sqlx::query("INSERT INTO message (id, body, sender_oidcid, appointment_id) VALUES (nextval('message_seq'), 'x', 'bob', $1)")
        .bind(planned)
        .execute(app.db.pool())
        .await
        .unwrap();
    sqlx::query("INSERT INTO pushsubscription (id, user_oidcid, endpoint, p256dh, auth) VALUES (nextval('pushsubscription_seq'), 'bob', 'e', 'p', 'a')")
        .execute(app.db.pool())
        .await
        .unwrap();

    let (status, body) = app.get(ADMIN, "/api/v2/admin/stats/").await;

    assert_eq!(status, StatusCode::OK, "{body}");
    assert_eq!(
        body,
        json!({
            "users": {"total": 2},
            "appointments": {"total": 3, "planned": 1, "cancelled": 1, "deleted": 1, "notEnoughAttendees": 0},
            "participations": {"total": 3, "pending": 1, "approved": 1, "rejected": 1},
            "groups": {"total": 1, "totalMembers": 2},
            "friendships": {"total": 3, "pending": 1, "accepted": 1, "declined": 1},
            "messages": {"total": 1},
            "pushSubscriptions": {"total": 1}
        })
    );
}

// ===== users =====

fn keycloak_users() -> FakeKeycloak {
    FakeKeycloak::new().with_realm_users(vec![
        KeycloakUser {
            id: Some("u1".into()),
            first_name: Some("Ulla".into()),
            last_name: Some("Uhl".into()),
            email: Some("ulla@example.com".into()),
            created_timestamp: Some(1_700_000_000_123),
            ..Default::default()
        },
        KeycloakUser {
            id: Some("u2".into()),
            ..Default::default()
        },
        KeycloakUser {
            id: Some("u3".into()),
            ..Default::default()
        },
    ])
}

#[tokio::test]
async fn test_admin_lists_keycloak_users_with_paging() {
    let app = TestApp::with_collaborators(keycloak_users(), None).await;
    prepare(&app).await;

    let (status, body) = app.get(ADMIN, "/api/v2/admin/users?size=2").await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(
        body,
        json!({
            "items": [
                {"id": "u1", "firstName": "Ulla", "lastName": "Uhl", "email": "ulla@example.com", "createdAt": "2023-11-14T22:13:20.123Z"},
                {"id": "u2", "firstName": null, "lastName": null, "email": null, "createdAt": null}
            ],
            "page": 0, "size": 2, "total": 3
        })
    );
    let (_, second) = app.get(ADMIN, "/api/v2/admin/users/?page=1&size=2").await;
    assert_eq!(second["items"].as_array().unwrap().len(), 1);
    assert_eq!(
        (second["page"].clone(), second["total"].clone()),
        (json!(1), json!(3))
    );
}

#[tokio::test]
async fn test_admin_gets_a_keycloak_user_and_404s_for_unknown_ones() {
    let app = TestApp::with_collaborators(keycloak_users(), None).await;
    prepare(&app).await;

    let (status, body) = app.get(ADMIN, "/api/v2/admin/users/u1").await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(body["email"], json!("ulla@example.com"));
    assert_eq!(body["createdAt"], json!("2023-11-14T22:13:20.123Z"));

    let (status, body) = app.get(ADMIN, "/api/v2/admin/users/nobody").await;
    assert_eq!((status, body), (StatusCode::NOT_FOUND, Value::Null));
}

#[tokio::test]
async fn test_admin_deletes_a_keycloak_user_with_204() {
    let app = TestApp::with_collaborators(keycloak_users(), None).await;
    prepare(&app).await;
    for id in ["u1", "unknown"] {
        let (status, body) = app
            .delete(ADMIN, &format!("/api/v2/admin/users/{id}"))
            .await;
        assert_eq!(
            (status, body),
            (StatusCode::NO_CONTENT, Value::Null),
            "{id}"
        );
    }
}
