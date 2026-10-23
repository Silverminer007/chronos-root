//! Issue #245: a CANCELLED appointment must stay readable and editable (Java hides only DELETED);
//! only a DELETED appointment is a 404.

mod common;

use axum::http::StatusCode;
use common::TestApp;
use serde_json::json;

const OWNER: &str = "alice";

async fn app_with_appointment() -> (TestApp, i64) {
    let app = TestApp::new().await;
    app.add_user(OWNER, "Alice", "Anders", "alice@example.com")
        .await;
    let (status, body) = app
        .post(
            OWNER,
            "/api/v2/appointments",
            Some(json!({"name": "Eins", "start": "2030-01-01T10:00:00Z", "end": "2030-01-01T11:00:00Z"})),
        )
        .await;
    assert_eq!(status, StatusCode::CREATED, "{body}");
    (app, body["id"].as_i64().unwrap())
}

#[tokio::test]
async fn test_get_and_patch_on_cancelled_appointment_answer_200() {
    let (app, id) = app_with_appointment().await;
    let uri = format!("/api/v2/appointments/{id}");
    let (status, body) = app.post(OWNER, &format!("{uri}/cancel"), None).await;
    assert_eq!(status, StatusCode::OK, "cancel: {body}");

    let (status, body) = app
        .get(
            OWNER,
            &format!("{uri}?participants=true&messages=true&group_participants=true"),
        )
        .await;
    assert_eq!(status, StatusCode::OK, "GET detail of CANCELLED: {body}");
    assert_eq!(body["status"], json!("CANCELLED"));

    let (status, body) = app.get(OWNER, &uri).await;
    assert_eq!(status, StatusCode::OK, "GET plain of CANCELLED: {body}");

    let (status, body) = app.patch(OWNER, &uri, Some(json!({"venue": "Heim"}))).await;
    assert_eq!(status, StatusCode::OK, "PATCH of CANCELLED: {body}");
}

#[tokio::test]
async fn test_get_on_deleted_appointment_answers_404() {
    let (app, id) = app_with_appointment().await;
    let uri = format!("/api/v2/appointments/{id}");
    let (status, body) = app.delete(OWNER, &uri).await;
    assert!(status.is_success(), "delete: {body}");

    let (status, body) = app.get(OWNER, &uri).await;
    assert_eq!(status, StatusCode::NOT_FOUND, "GET of DELETED: {body}");
}
