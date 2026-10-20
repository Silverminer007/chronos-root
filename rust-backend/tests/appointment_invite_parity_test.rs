//! Parity fixes for inviting participants (#158, ADR-2026-10-20-025):
//! - invited participants may read the appointment (Java `requireReadAppointment`)
//! - only friends can be added as participants (Java `requireAddUserToAppointment`)
//! - the creator starts as RESPONSIBLE with status PENDING
mod common;

use axum::http::StatusCode;
use common::TestApp;
use serde_json::json;

async fn create(app: &TestApp, user: &str) -> i64 {
    let (status, body) = app
        .post(
            user,
            "/api/v2/appointments",
            Some(json!({
                "name": "Treffen",
                "start": "2030-01-01T10:00:00Z",
                "end": "2030-01-01T11:00:00Z"
            })),
        )
        .await;
    assert!(status.is_success(), "create failed: {status} {body}");
    body["id"].as_i64().unwrap()
}

fn invite_uri(appointment: i64, user: &str) -> String {
    format!("/api/v2/appointments/{appointment}/participants/{user}")
}

#[tokio::test]
async fn invited_friend_can_read_the_appointment() {
    let app = TestApp::new().await;
    app.add_friendship("alice", "bob", "ACCEPTED").await;
    let id = create(&app, "alice").await;

    let (status, _) = app
        .post(
            "alice",
            &invite_uri(id, "bob"),
            Some(json!({"user_role": "ATTENDANT"})),
        )
        .await;
    assert_eq!(status, StatusCode::OK);

    let (status, body) = app.get("bob", &format!("/api/v2/appointments/{id}")).await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(body["id"], id);
}

#[tokio::test]
async fn stranger_cannot_read_the_appointment() {
    let app = TestApp::new().await;
    let id = create(&app, "alice").await;

    let (status, _) = app.get("bob", &format!("/api/v2/appointments/{id}")).await;
    assert_eq!(status, StatusCode::FORBIDDEN);
}

#[tokio::test]
async fn adding_a_non_friend_is_forbidden_and_adds_nobody() {
    let app = TestApp::new().await;
    app.add_friendship("alice", "carol", "PENDING").await;
    let id = create(&app, "alice").await;

    for target in ["bob", "carol"] {
        let (status, body) = app
            .post(
                "alice",
                &invite_uri(id, target),
                Some(json!({"user_role": "ATTENDANT"})),
            )
            .await;
        assert_eq!(status, StatusCode::FORBIDDEN, "{target}");
        assert!(body
            .to_string()
            .contains("You can only add friends to appointments"));
    }

    let count: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM appointment_participation WHERE appointment_id = $1",
    )
    .bind(id)
    .fetch_one(app.db.pool())
    .await
    .unwrap();
    assert_eq!(count, 1);
}

#[tokio::test]
async fn creator_starts_pending_and_can_approve() {
    let app = TestApp::new().await;
    let id = create(&app, "alice").await;

    let status: String = sqlx::query_scalar(
        "SELECT status FROM appointment_participation WHERE appointment_id = $1 AND user_oidcid = 'alice'",
    )
    .bind(id)
    .fetch_one(app.db.pool())
    .await
    .unwrap();
    assert_eq!(status, "PENDING");

    let (code, _) = app
        .post(
            "alice",
            &format!("/api/v2/appointments/{id}/participation"),
            Some(json!({"status": "APPROVED"})),
        )
        .await;
    assert_eq!(code, StatusCode::OK);
}
