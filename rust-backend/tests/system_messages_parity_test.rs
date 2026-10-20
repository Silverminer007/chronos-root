//! Ticket #159: Java parity of system messages, `DELETE` after `cancel`, group members as
//! participants and the admin message endpoint (ADR-2026-10-20-027).

mod common;

use axum::http::StatusCode;
use common::TestApp;
use serde_json::json;

const ADMIN: &str = "admin";
const OWNER: &str = "owner";
const GUEST: &str = "guest";

async fn app() -> TestApp {
    let app = TestApp::new().await;
    app.add_user(ADMIN, "Ada", "Admin", "ada@example.com").await;
    app.add_user(OWNER, "Olga", "Owner", "olga@example.com")
        .await;
    app.add_user(GUEST, "Gustav", "Guest", "gustav@example.com")
        .await;
    app.grant_admin(ADMIN);
    app
}

async fn appointment(app: &TestApp) -> i64 {
    let (status, body) = app
        .post(
            OWNER,
            "/api/v2/appointments",
            Some(json!({"name": "Treffen", "start": "2030-01-01T10:00:00Z", "end": "2030-01-01T11:00:00Z"})),
        )
        .await;
    assert_eq!(status, StatusCode::CREATED, "{body}");
    body["id"].as_i64().unwrap()
}

async fn bodies(app: &TestApp, appointment_id: i64) -> Vec<String> {
    let (status, body) = app
        .get(
            OWNER,
            &format!("/api/v2/appointments/{appointment_id}/messages"),
        )
        .await;
    assert_eq!(status, StatusCode::OK, "{body}");
    body.as_array()
        .unwrap()
        .iter()
        .map(|m| m["body"].as_str().unwrap().to_string())
        .collect()
}

async fn invite(app: &TestApp, appointment_id: i64, user: &str) {
    let (status, body) = app
        .post(
            OWNER,
            &format!("/api/v2/appointments/{appointment_id}/participants/{user}"),
            Some(json!({"user_role": "ATTENDANT"})),
        )
        .await;
    assert_eq!(status, StatusCode::OK, "{body}");
}

#[tokio::test]
async fn test_rsvp_writes_a_system_message_with_the_name_of_the_user() {
    let app = app().await;
    let id = appointment(&app).await;
    invite(&app, id, GUEST).await;

    let (status, _) = app
        .post(
            GUEST,
            &format!("/api/v2/appointments/{id}/participation"),
            Some(json!({"status": "APPROVED"})),
        )
        .await;
    assert_eq!(status, StatusCode::OK);
    let (status, _) = app
        .post(
            GUEST,
            &format!("/api/v2/appointments/{id}/participation"),
            Some(json!({"status": "REJECTED"})),
        )
        .await;
    assert_eq!(status, StatusCode::OK);

    assert_eq!(
        bodies(&app, id).await,
        vec!["Gustav Guest hat abgesagt", "Gustav Guest hat zugesagt"]
    );
}

#[tokio::test]
async fn test_cancel_writes_a_system_message_and_the_appointment_can_still_be_deleted() {
    let app = app().await;
    let id = appointment(&app).await;

    let (status, _) = app
        .post(OWNER, &format!("/api/v2/appointments/{id}/cancel"), None)
        .await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(
        bodies(&app, id).await,
        vec!["Olga Owner hat diesen Termin abgesagt. Er wird nicht stattfinden!"]
    );

    let (status, body) = app
        .delete(OWNER, &format!("/api/v2/appointments/{id}"))
        .await;
    assert_eq!(status, StatusCode::OK, "{body}");
}

#[tokio::test]
async fn test_group_members_become_pending_participants_via_the_group() {
    let app = app().await;
    let id = appointment(&app).await;
    let group = app.add_group(OWNER, "Meute").await;
    app.add_member(group, GUEST).await;

    let (status, _) = app
        .post(
            ADMIN,
            &format!("/api/v2/admin/appointments/{id}/participants/groups"),
            Some(json!({"group_id": group, "user_role": "ATTENDANT"})),
        )
        .await;
    assert_eq!(status, StatusCode::OK);

    let rows: Vec<(String, String, String, Option<i64>)> = sqlx::query_as(
        "SELECT user_oidcid, role, status, group_participation_id FROM appointment_participation
         WHERE appointment_id = $1 ORDER BY user_oidcid",
    )
    .bind(id)
    .fetch_all(app.db.pool())
    .await
    .unwrap();
    assert_eq!(
        rows,
        vec![
            (
                GUEST.into(),
                "ATTENDANT".into(),
                "PENDING".into(),
                Some(group)
            ),
            (OWNER.into(), "RESPONSIBLE".into(), "APPROVED".into(), None),
        ]
    );

    // a group member can then be moved by the admin, like in Java
    let (status, body) = app
        .post(
            ADMIN,
            "/api/v2/admin/appointments/participants/status",
            Some(json!({"userId": GUEST, "appointmentId": id, "participationStatus": "APPROVED"})),
        )
        .await;
    assert_eq!(status, StatusCode::OK, "{body}");
    assert_eq!(bodies(&app, id).await, vec!["Gustav Guest hat zugesagt"]);
}

#[tokio::test]
async fn test_admin_message_after_a_user_message_is_stored() {
    let app = app().await;
    let id = appointment(&app).await;
    let (status, _) = app
        .post(
            OWNER,
            &format!("/api/v2/appointments/{id}/messages"),
            Some(json!({"body": "Hallo"})),
        )
        .await;
    assert_eq!(status, StatusCode::OK);

    let (status, body) = app
        .post(
            ADMIN,
            "/api/v2/admin/appointments/message",
            Some(json!({"appointment_id": id, "sender_id": OWNER, "body": "Admin Nachricht", "timestamp": "2030-01-01T00:00:00Z"})),
        )
        .await;

    assert_eq!(status, StatusCode::OK, "{body}");
    let bodies = bodies(&app, id).await;
    assert!(
        bodies.contains(&"Admin Nachricht".to_string()),
        "{bodies:?}"
    );
}
