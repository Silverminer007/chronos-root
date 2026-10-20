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
        "VALIDATION_ERROR",
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

const ADMIN: &str = "admin";

fn assert_json_content_type(content_type: Option<String>) {
    let content_type = content_type.expect("Content-Type header");
    assert!(
        content_type.starts_with("application/json"),
        "{content_type}"
    );
}

#[tokio::test]
async fn admin_endpoint_for_missing_appointment_is_a_json_not_found() {
    let app = TestApp::new().await;
    app.grant_admin(ADMIN);
    let group = app.add_group(ADMIN, "Gruppe").await;
    let path = format!("/api/v2/admin/appointments/{MISSING_ID}/participants/groups");

    let (status, content_type, body) = app
        .call_typed(
            Some(ADMIN),
            "POST",
            &path,
            Some(json!({"group_id": group, "user_role": "GUEST"})),
        )
        .await;

    assert_json_content_type(content_type);
    assert_error(
        (status, body),
        StatusCode::NOT_FOUND,
        "RESOURCE_NOT_FOUND",
        "Appointment not found",
        &path,
    );
}

#[tokio::test]
async fn admin_participation_status_change_of_non_participant_is_a_json_bad_request() {
    let app = TestApp::new().await;
    app.grant_admin(ADMIN);
    let id = appointment(&app, "alice").await;
    let path = "/api/v2/admin/appointments/participants/status";

    let (status, content_type, body) = app
        .call_typed(
            Some(ADMIN),
            "POST",
            path,
            Some(json!({"userId": "bob", "appointmentId": id, "participationStatus": "APPROVED"})),
        )
        .await;

    assert_json_content_type(content_type);
    assert_eq!(status, StatusCode::BAD_REQUEST, "{body}");
    assert_eq!(body["status"], json!(400), "{body}");
    assert_eq!(body["errorCode"], json!("VALIDATION_ERROR"), "{body}");
    assert_eq!(body["path"], json!(path), "{body}");
    assert!(body["message"].is_string(), "{body}");
}

#[tokio::test]
async fn adding_a_group_the_actor_is_not_a_member_of_is_forbidden() {
    let app = TestApp::new().await;
    let id = appointment(&app, "alice").await;
    let path = format!("/api/v2/appointments/{id}/groups/{MISSING_ID}");

    let answer = app
        .post("alice", &path, Some(json!({"role": "ATTENDANT"})))
        .await;

    assert_error(
        answer,
        StatusCode::FORBIDDEN,
        "FORBIDDEN",
        "You can only add groups, you're a member of",
        &path,
    );
}

#[tokio::test]
async fn invalid_appointment_fields_are_validation_errors_with_field_errors() {
    let app = TestApp::new().await;
    let path = "/api/v2/appointments";

    let (status, body) = app
        .post(
            "alice",
            path,
            Some(json!({"name": "T", "start": "2026-10-20T10:00:00Z", "end": "2026-10-20T09:00:00Z"})),
        )
        .await;
    assert_eq!(status, StatusCode::BAD_REQUEST, "{body}");
    assert_eq!(body["errorCode"], json!("VALIDATION_ERROR"), "{body}");
    assert_eq!(
        body["message"],
        json!("Validierungsfehler bei Feld: end"),
        "{body}"
    );
    assert_eq!(
        body["fieldErrors"],
        json!({"end": "End date cannot be before start date"}),
        "{body}"
    );

    let (status, body) = app
        .post(
            "alice",
            path,
            Some(json!({"name": "T", "start": "2026-10-20T09:00:00Z", "end": "2026-10-20T10:00:00Z", "minimal_attendees": -1})),
        )
        .await;
    assert_eq!(status, StatusCode::BAD_REQUEST, "{body}");
    assert_eq!(
        body["fieldErrors"],
        json!({"minimal_attendees": "Minimal attendees must be positive"}),
        "{body}"
    );
}

#[tokio::test]
async fn removing_a_non_participant_is_a_validation_error_with_the_java_text() {
    let app = TestApp::new().await;
    let id = appointment(&app, "alice").await;
    let path = format!("/api/v2/appointments/{id}/participants/bob");

    let answer = app.delete("alice", &path).await;

    assert_error(
        answer,
        StatusCode::BAD_REQUEST,
        "VALIDATION_ERROR",
        "This user is not a participant of this event",
        &path,
    );
}

#[tokio::test]
async fn deleted_appointment_is_a_not_found_naming_the_id() {
    let app = TestApp::new().await;
    let id = appointment(&app, "alice").await;
    sqlx::query("UPDATE appointment SET status = 2 WHERE id = $1")
        .bind(id)
        .execute(app.db.pool())
        .await
        .unwrap();
    let path = format!("/api/v2/appointments/{id}");

    let answer = app.get("alice", &path).await;

    assert_error(
        answer,
        StatusCode::NOT_FOUND,
        "RESOURCE_NOT_FOUND",
        &format!("appointment mit ID {id} wurde nicht gefunden"),
        &path,
    );
}
