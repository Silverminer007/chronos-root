//! Error answers of the appointment and push endpoints (ticket #157, ADR-2026-10-20-025).
//!
//! Every error answer of a handler carries the Java `ErrorResponse` JSON body, and an unknown
//! appointment is a 403 after authorization, like in Java (`AuthorizationService`).
mod common;

use axum::http::StatusCode;
use common::TestApp;
use serde_json::{json, Value};

const NO_ROLE: &str = "You have no role at this appointment";
const NOT_ATTENDANT: &str = "You have to be at least attendant of this appointment";
const NOT_RESPONSIBLE: &str = "You have to be responsible for this appointment";
const MISSING_ID: i64 = 999_999;

async fn appointment(app: &TestApp, creator: &str) -> i64 {
    let id = sqlx::query_scalar::<_, i64>("SELECT nextval('appointment_seq')")
        .fetch_one(app.db.pool())
        .await
        .unwrap();
    sqlx::query(
        "INSERT INTO appointment (id, name, start_time, end_time, status, createdat, lastupdate)
         VALUES ($1, 'Treffen', NOW(), NOW() + INTERVAL '1 hour', 0, NOW(), NOW())",
    )
    .bind(id)
    .execute(app.db.pool())
    .await
    .unwrap();
    sqlx::query(
        "INSERT INTO appointment_participation
             (id, appointment_id, user_oidcid, role, status)
         VALUES (nextval('appointment_participation_seq'), $1, $2, 'RESPONSIBLE', 'PENDING')",
    )
    .bind(id)
    .bind(creator)
    .execute(app.db.pool())
    .await
    .unwrap();
    id
}

fn assert_error(
    (status, body): (StatusCode, Value),
    expected_status: StatusCode,
    code: &str,
    message: &str,
    path: &str,
) {
    assert_eq!(status, expected_status, "{body}");
    assert_eq!(body["status"], json!(expected_status.as_u16()), "{body}");
    assert_eq!(body["errorCode"], json!(code), "{body}");
    assert_eq!(body["message"], json!(message), "{body}");
    assert_eq!(body["path"], json!(path), "{body}");
    assert!(body["timestamp"].is_string(), "{body}");
}

#[tokio::test]
async fn missing_appointment_is_forbidden_for_get() {
    let app = TestApp::new().await;
    let path = format!("/api/v2/appointments/{MISSING_ID}");

    let answer = app.get("alice", &path).await;

    assert_error(answer, StatusCode::FORBIDDEN, "FORBIDDEN", NO_ROLE, &path);
}

#[tokio::test]
async fn missing_appointment_is_forbidden_for_patch() {
    let app = TestApp::new().await;
    let path = format!("/api/v2/appointments/{MISSING_ID}");

    let answer = app
        .patch("alice", &path, Some(json!({"name": "Neu"})))
        .await;

    assert_error(
        answer,
        StatusCode::FORBIDDEN,
        "FORBIDDEN",
        NOT_ATTENDANT,
        &path,
    );
}

#[tokio::test]
async fn missing_appointment_is_forbidden_for_delete() {
    let app = TestApp::new().await;
    let path = format!("/api/v2/appointments/{MISSING_ID}");

    let answer = app.delete("alice", &path).await;

    assert_error(
        answer,
        StatusCode::FORBIDDEN,
        "FORBIDDEN",
        NOT_RESPONSIBLE,
        &path,
    );
}

#[tokio::test]
async fn missing_appointment_is_forbidden_for_add_group() {
    let app = TestApp::new().await;
    let group = app.add_group("alice", "Gruppe").await;
    let path = format!("/api/v2/appointments/{MISSING_ID}/groups/{group}");

    let answer = app
        .post("alice", &path, Some(json!({"role": "ATTENDANT"})))
        .await;

    assert_error(
        answer,
        StatusCode::FORBIDDEN,
        "FORBIDDEN",
        NOT_RESPONSIBLE,
        &path,
    );
}

#[tokio::test]
async fn missing_appointment_is_forbidden_for_role_change() {
    let app = TestApp::new().await;
    let path = format!("/api/v2/appointments/{MISSING_ID}/participation/bob/role");

    let answer = app
        .call(
            Some("alice"),
            "PUT",
            &path,
            Some(json!({"role": "ATTENDANT"})),
        )
        .await;

    assert_error(
        answer,
        StatusCode::FORBIDDEN,
        "FORBIDDEN",
        NOT_RESPONSIBLE,
        &path,
    );
}

#[tokio::test]
async fn messages_of_missing_appointment_are_forbidden_as_json() {
    let app = TestApp::new().await;
    let path = format!("/api/v2/appointments/{MISSING_ID}/messages");

    let answer = app.get("alice", &path).await;

    assert_error(answer, StatusCode::FORBIDDEN, "FORBIDDEN", NO_ROLE, &path);
}

#[tokio::test]
async fn delete_by_non_responsible_user_is_json() {
    let app = TestApp::new().await;
    let id = appointment(&app, "alice").await;
    let path = format!("/api/v2/appointments/{id}");

    let answer = app.delete("mallory", &path).await;

    assert_error(
        answer,
        StatusCode::FORBIDDEN,
        "FORBIDDEN",
        NOT_RESPONSIBLE,
        &path,
    );
}

#[tokio::test]
async fn role_change_of_non_participant_is_a_json_bad_request() {
    let app = TestApp::new().await;
    let id = appointment(&app, "alice").await;
    let path = format!("/api/v2/appointments/{id}/participation/bob/role");

    let answer = app
        .call(
            Some("alice"),
            "PUT",
            &path,
            Some(json!({"role": "ATTENDANT"})),
        )
        .await;

    assert_error(
        answer,
        StatusCode::BAD_REQUEST,
        "BAD_REQUEST",
        "This user is not a participant of this event",
        &path,
    );
}

#[tokio::test]
async fn unknown_route_is_a_json_not_found() {
    let app = TestApp::new().await;

    let answer = app.get("alice", "/api/v2/does-not-exist").await;

    let (status, body) = answer;
    assert_eq!(status, StatusCode::NOT_FOUND, "{body}");
    assert_eq!(body["message"], json!("HTTP 404 Not Found"), "{body}");
    assert_eq!(body["path"], json!("/api/v2/does-not-exist"), "{body}");
    assert!(body.get("errorCode").is_none(), "{body}");
}

#[tokio::test]
async fn non_numeric_appointment_id_is_a_json_not_found() {
    let app = TestApp::new().await;

    let (status, body) = app.get("alice", "/api/v2/appointments/abc").await;

    assert_eq!(status, StatusCode::NOT_FOUND, "{body}");
    assert_eq!(body["message"], json!("HTTP 404 Not Found"), "{body}");
}

#[tokio::test]
async fn unreadable_json_body_is_a_json_bad_request() {
    let app = TestApp::new().await;
    let id = appointment(&app, "alice").await;
    let path = format!("/api/v2/appointments/{id}/messages");

    let (status, body) = app.post("alice", &path, Some(json!("kein Objekt"))).await;

    assert_eq!(status, StatusCode::BAD_REQUEST, "{body}");
    assert_eq!(body["errorCode"], json!("BAD_REQUEST"), "{body}");
    assert_eq!(body["path"], json!(path), "{body}");
}

#[tokio::test]
async fn invalid_push_subscription_is_a_json_server_error() {
    let app = TestApp::new().await;

    let (status, body) = app
        .post("alice", "/api/v2/push/subscribe", Some(json!({})))
        .await;

    assert_eq!(status, StatusCode::INTERNAL_SERVER_ERROR, "{body}");
    assert_eq!(body["errorCode"], json!("INTERNAL_ERROR"), "{body}");
    assert_eq!(body["path"], json!("/api/v2/push/subscribe"), "{body}");
}
