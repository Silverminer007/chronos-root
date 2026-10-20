//! Integration tests for DELETE /api/v2/appointments/:id/groups/:groupId
//!
//! Behaviour mirrors the Java backend (`AppointmentParticipationService#removeGroupFromAppointment`,
//! ADR-2026-10-20-024):
//! - 403 unless the caller is RESPONSIBLE (an unknown appointment is a 403 as well)
//! - 400 when the group is not linked to the appointment
//! - hard delete of the link and of every participation that came in through the group
mod common;

use axum::http::StatusCode;
use common::TestApp;

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
    participation(app, id, creator, "RESPONSIBLE", None).await;
    id
}

async fn participation(
    app: &TestApp,
    appointment: i64,
    user: &str,
    role: &str,
    group: Option<i64>,
) {
    sqlx::query(
        "INSERT INTO appointment_participation
             (id, appointment_id, user_oidcid, role, status, group_participation_id)
         VALUES (nextval('appointment_participation_seq'), $1, $2, $3, 'PENDING', $4)",
    )
    .bind(appointment)
    .bind(user)
    .bind(role)
    .bind(group)
    .execute(app.db.pool())
    .await
    .expect("Failed to insert participation");
}

async fn link_group(app: &TestApp, appointment: i64, group: i64) {
    sqlx::query(
        "INSERT INTO appointment_group_participations (id, appointment_id, group_id, role)
         VALUES (nextval('appointment_group_participations_seq'), $1, $2, 'ATTENDANT')",
    )
    .bind(appointment)
    .bind(group)
    .execute(app.db.pool())
    .await
    .expect("Failed to link group");
}

async fn count(app: &TestApp, sql: &str, appointment: i64) -> i64 {
    sqlx::query_scalar::<_, i64>(sql)
        .bind(appointment)
        .fetch_one(app.db.pool())
        .await
        .unwrap()
}

fn uri(appointment: i64, group: i64) -> String {
    format!("/api/v2/appointments/{appointment}/groups/{group}")
}

#[tokio::test]
async fn removes_link_and_group_sourced_participants_but_keeps_direct_ones() {
    let app = TestApp::new().await;
    let group = app.add_group("alice", "Gruppe").await;
    let other_group = app.add_group("alice", "Andere").await;
    let id = appointment(&app, "alice").await;
    link_group(&app, id, group).await;
    link_group(&app, id, other_group).await;
    participation(&app, id, "bob", "ATTENDANT", Some(group)).await;
    participation(&app, id, "carol", "ATTENDANT", None).await;
    participation(&app, id, "dave", "ATTENDANT", Some(other_group)).await;

    let (status, _) = app.delete("alice", &uri(id, group)).await;

    assert_eq!(status, StatusCode::OK);
    let links = "SELECT COUNT(*) FROM appointment_group_participations WHERE appointment_id = $1";
    assert_eq!(count(&app, links, id).await, 1);
    let users: Vec<String> = sqlx::query_scalar(
        "SELECT user_oidcid FROM appointment_participation WHERE appointment_id = $1 ORDER BY id",
    )
    .bind(id)
    .fetch_all(app.db.pool())
    .await
    .unwrap();
    assert_eq!(users, vec!["alice", "carol", "dave"]);
}

#[tokio::test]
async fn publishes_group_participation_removed_event() {
    let app = TestApp::new().await;
    let group = app.add_group("alice", "Gruppe").await;
    let id = appointment(&app, "alice").await;
    link_group(&app, id, group).await;

    let (status, _) = app.delete("alice", &uri(id, group)).await;

    assert_eq!(status, StatusCode::OK);
    let payload: String = sqlx::query_scalar(
        "SELECT payload::TEXT FROM events WHERE event_type = 'AppointmentGroupParticipationRemovedEvent'",
    )
    .fetch_one(app.db.pool())
    .await
    .expect("event published");
    let payload: serde_json::Value = serde_json::from_str(&payload).unwrap();
    assert_eq!(payload["appointment_id"], id);
    assert_eq!(payload["group_id"], group);
    assert_eq!(payload["actor_id"], "alice");
}

#[tokio::test]
async fn non_responsible_caller_is_forbidden_and_nothing_is_removed() {
    let app = TestApp::new().await;
    let group = app.add_group("alice", "Gruppe").await;
    let id = appointment(&app, "alice").await;
    link_group(&app, id, group).await;
    participation(&app, id, "bob", "ATTENDANT", None).await;

    let (status, body) = app.delete("bob", &uri(id, group)).await;

    assert_eq!(status, StatusCode::FORBIDDEN);
    assert!(body
        .to_string()
        .contains("You have to be responsible for this appointment"));
    let links = "SELECT COUNT(*) FROM appointment_group_participations WHERE appointment_id = $1";
    assert_eq!(count(&app, links, id).await, 1);
}

#[tokio::test]
async fn unknown_appointment_is_forbidden_like_in_java() {
    let app = TestApp::new().await;
    let group = app.add_group("alice", "Gruppe").await;

    let (status, _) = app.delete("alice", &uri(999_999, group)).await;

    assert_eq!(status, StatusCode::FORBIDDEN);
}

#[tokio::test]
async fn group_not_linked_is_a_bad_request() {
    let app = TestApp::new().await;
    let group = app.add_group("alice", "Gruppe").await;
    let id = appointment(&app, "alice").await;

    let (status, body) = app.delete("alice", &uri(id, group)).await;

    assert_eq!(status, StatusCode::BAD_REQUEST);
    assert!(body
        .to_string()
        .contains("This group is not a participant of this appointment"));
}

#[tokio::test]
async fn authorization_is_checked_before_the_link() {
    let app = TestApp::new().await;
    let group = app.add_group("alice", "Gruppe").await;
    let id = appointment(&app, "alice").await;

    let (status, _) = app.delete("mallory", &uri(id, group)).await;

    assert_eq!(status, StatusCode::FORBIDDEN);
}
