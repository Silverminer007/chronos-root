//! Integration tests for GET /api/v2/appointments/:id/participants
//!
//! Behaviour mirrors the Java backend (`AppointmentParticipationResource#getParticipants`,
//! `AppointmentParticipationService#getParticipants`, ADR-2026-10-20-022):
//! - any participant with a role other than NONE may read; everybody else gets 403
//! - one `UserParticipantDto` per participation row, `null` fields omitted
//! - removed participants (hard delete) no longer appear
mod common;

use axum::http::StatusCode;
use common::TestApp;
use serde_json::json;

async fn participation(app: &TestApp, appointment: i64, user: &str, role: &str, status: &str) {
    sqlx::query(
        "INSERT INTO appointment_participation (id, appointment_id, user_oidcid, role, status)
         VALUES (nextval('appointment_participation_seq'), $1, $2, $3, $4)",
    )
    .bind(appointment)
    .bind(user)
    .bind(role)
    .bind(status)
    .execute(app.db.pool())
    .await
    .expect("Failed to insert participation");
}

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
    participation(app, id, creator, "RESPONSIBLE", "APPROVED").await;
    id
}

fn uri(id: i64) -> String {
    format!("/api/v2/appointments/{id}/participants")
}

#[tokio::test]
async fn lists_participants_with_names_roles_and_status() {
    let app = TestApp::new().await;
    app.add_user("alice", "Alice", "Anders", "alice@example.com")
        .await;
    app.add_user("bob", "Bob", "Berger", "bob@example.com")
        .await;
    sqlx::query("UPDATE user_profiles SET profile_picture_url = 'https://pics/bob.png' WHERE oidc_id = 'bob'")
        .execute(app.db.pool())
        .await
        .unwrap();
    let id = appointment(&app, "alice").await;
    participation(&app, id, "bob", "ATTENDANT", "PENDING").await;

    // alice reads: a request upserts the caller's own profile from the token (without picture)
    let (status, body) = app.get("alice", &uri(id)).await;

    assert_eq!(status, StatusCode::OK);
    assert_eq!(
        body,
        json!([
            {"user_id": "alice", "name": "Alice Anders", "role": "RESPONSIBLE", "status": "APPROVED"},
            {"user_id": "bob", "name": "Bob Berger", "profile_picture_url": "https://pics/bob.png",
             "role": "ATTENDANT", "status": "PENDING"}
        ])
    );
}

#[tokio::test]
async fn participant_without_profile_is_listed_as_deleted_user() {
    let app = TestApp::new().await;
    app.add_user("alice", "Alice", "Anders", "alice@example.com")
        .await;
    let id = appointment(&app, "alice").await;
    participation(&app, id, "ghost", "GUEST", "PENDING").await;

    let (_, body) = app.get("alice", &uri(id)).await;

    assert_eq!(
        body[1],
        json!({"user_id": "ghost", "name": "Gelöschter Benutzer", "role": "GUEST", "status": "PENDING"})
    );
}

#[tokio::test]
async fn group_participant_carries_via_group() {
    let app = TestApp::new().await;
    app.add_user("alice", "Alice", "Anders", "alice@example.com")
        .await;
    app.add_user("bob", "Bob", "Berger", "bob@example.com")
        .await;
    let group = app.add_group("alice", "Jugendleiter").await;
    let id = appointment(&app, "alice").await;
    participation(&app, id, "bob", "ATTENDANT", "PENDING").await;
    sqlx::query("UPDATE appointment_participation SET group_participation_id = $1 WHERE user_oidcid = 'bob'")
        .bind(group)
        .execute(app.db.pool())
        .await
        .unwrap();

    let (_, body) = app.get("alice", &uri(id)).await;

    assert_eq!(body[1]["via_group_id"], json!(group));
    assert_eq!(body[1]["via_group_name"], json!("Jugendleiter"));
    assert!(body[0].get("via_group_id").is_none());
}

#[tokio::test]
async fn removed_participant_is_not_listed() {
    let app = TestApp::new().await;
    app.add_user("alice", "Alice", "Anders", "alice@example.com")
        .await;
    app.add_user("bob", "Bob", "Berger", "bob@example.com")
        .await;
    let id = appointment(&app, "alice").await;
    participation(&app, id, "bob", "ATTENDANT", "PENDING").await;

    let (status, _) = app
        .delete(
            "alice",
            &format!("/api/v2/appointments/{id}/participants/bob"),
        )
        .await;
    assert_eq!(status, StatusCode::OK);

    let (_, body) = app.get("alice", &uri(id)).await;
    assert_eq!(body.as_array().unwrap().len(), 1);
    assert_eq!(body[0]["user_id"], json!("alice"));
}

#[tokio::test]
async fn non_participant_is_forbidden() {
    let app = TestApp::new().await;
    app.add_user("alice", "Alice", "Anders", "alice@example.com")
        .await;
    app.add_user("eve", "Eve", "Fremd", "eve@example.com").await;
    let id = appointment(&app, "alice").await;

    let (status, _) = app.get_raw("eve", &uri(id)).await;

    assert_eq!(status, StatusCode::FORBIDDEN);
}

#[tokio::test]
async fn participant_with_role_none_is_forbidden() {
    let app = TestApp::new().await;
    app.add_user("alice", "Alice", "Anders", "alice@example.com")
        .await;
    app.add_user("bob", "Bob", "Berger", "bob@example.com")
        .await;
    let id = appointment(&app, "alice").await;
    participation(&app, id, "bob", "NONE", "PENDING").await;

    let (status, _) = app.get_raw("bob", &uri(id)).await;

    assert_eq!(status, StatusCode::FORBIDDEN);
}

#[tokio::test]
async fn unknown_appointment_is_forbidden_like_java() {
    let app = TestApp::new().await;
    app.add_user("alice", "Alice", "Anders", "alice@example.com")
        .await;

    let (status, _) = app.get_raw("alice", &uri(999_999)).await;

    assert_eq!(status, StatusCode::FORBIDDEN);
}

#[tokio::test]
async fn unauthenticated_request_is_rejected() {
    let app = TestApp::new().await;

    let (status, _) = app.call(None, "GET", &uri(1), None).await;

    assert_eq!(status, StatusCode::UNAUTHORIZED);
}
