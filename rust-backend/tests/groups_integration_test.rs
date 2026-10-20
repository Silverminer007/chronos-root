//! Integration tests for the groups endpoints (`/api/v2/groups`).
//!
//! The behaviour mirrors the Java `GroupsResource` / `GroupService` / `AuthorizationService`
//! (ADR-2026-10-20-018). Requests go through the production router incl. authentication.

mod common;

use axum::http::StatusCode;
use common::TestApp;
use serde_json::{json, Value};

async fn app_with_users() -> TestApp {
    let app = TestApp::new().await;
    app.add_user("alice", "Alice", "Anders", "alice@example.com")
        .await;
    app.add_user("bob", "Bob", "Becker", "bob@example.com")
        .await;
    app.add_user("carol", "Carol", "Clark", "carol@example.com")
        .await;
    app
}

fn assert_error(body: &Value, status: u16, error: &str, code: &str, message: &str, path: &str) {
    assert_eq!(body["status"], json!(status), "{body}");
    assert_eq!(body["error"], json!(error), "{body}");
    assert_eq!(body["errorCode"], json!(code), "{body}");
    assert_eq!(body["message"], json!(message), "{body}");
    assert_eq!(body["path"], json!(path), "{body}");
    assert!(body["timestamp"].is_string(), "{body}");
}

// ===== authentication =====

#[tokio::test]
async fn test_group_routes_require_authentication() {
    let app = app_with_users().await;
    for (method, uri) in [
        ("GET", "/api/v2/groups"),
        ("POST", "/api/v2/groups"),
        ("DELETE", "/api/v2/groups/1"),
        ("PATCH", "/api/v2/groups/1"),
        ("GET", "/api/v2/groups/1/users"),
        ("POST", "/api/v2/groups/1/user/bob"),
        ("DELETE", "/api/v2/groups/1/user/bob"),
    ] {
        let (status, _) = app.call(None, method, uri, None).await;
        assert_eq!(status, StatusCode::UNAUTHORIZED, "{method} {uri}");
    }
}

// ===== GET /groups =====

#[tokio::test]
async fn test_list_groups_returns_only_groups_the_user_is_member_of_with_member_names() {
    let app = app_with_users().await;
    let team = app.add_group("alice", "Team").await;
    app.add_member(team, "bob").await;
    app.add_group("bob", "Bobs Gruppe").await;
    // Owner without membership row: Java lists by membership only
    sqlx::query("INSERT INTO groups (id, groupname, owner_oidcid) VALUES (nextval('groups_seq'), 'Verwaist', 'alice')")
        .execute(app.db.pool())
        .await
        .unwrap();

    let (status, body) = app.get("alice", "/api/v2/groups").await;

    assert_eq!(status, StatusCode::OK);
    assert_eq!(
        body,
        json!([{
            "id": team,
            "name": "Team",
            "members": [
                {"id": "alice", "first_name": "Alice", "last_name": "Anders"},
                {"id": "bob", "first_name": "Bob", "last_name": "Becker"}
            ]
        }])
    );
}

#[tokio::test]
async fn test_list_groups_member_without_profile_has_null_names() {
    let app = app_with_users().await;
    let team = app.add_group("alice", "Team").await;
    app.add_member(team, "ghost").await;

    let (_, body) = app.get("alice", "/api/v2/groups").await;

    assert_eq!(
        body[0]["members"][1],
        json!({"id": "ghost", "first_name": null, "last_name": null})
    );
}

#[tokio::test]
async fn test_list_groups_search_filters_by_name_case_insensitively() {
    let app = app_with_users().await;
    app.add_group("alice", "Jugendleiter").await;
    app.add_group("alice", "Kochen").await;

    let (_, body) = app.get("alice", "/api/v2/groups?search=LEIT").await;

    let names: Vec<&str> = body
        .as_array()
        .unwrap()
        .iter()
        .map(|g| g["name"].as_str().unwrap())
        .collect();
    assert_eq!(names, vec!["Jugendleiter"]);

    let (_, all) = app.get("alice", "/api/v2/groups?search=").await;
    assert_eq!(all.as_array().unwrap().len(), 2);
}

// ===== POST /groups =====

#[tokio::test]
async fn test_create_group_returns_dto_adds_creator_and_fires_event() {
    let app = app_with_users().await;

    let (status, body) = app
        .post("alice", "/api/v2/groups", Some(json!({"name": "Neu"})))
        .await;

    assert_eq!(status, StatusCode::OK);
    let id = body["id"].as_i64().expect("numeric id");
    assert_eq!(body, json!({"id": id, "name": "Neu", "members": null}));
    assert_eq!(app.members_of(id).await, vec!["alice".to_string()]);
    let events = app.events("GroupCreatedEvent").await;
    assert_eq!(events.len(), 1);
    assert_eq!(events[0]["group_id"], json!(id));
    assert_eq!(events[0]["acting_user_id"], json!("alice"));
}

#[tokio::test]
async fn test_create_group_with_blank_name_is_a_validation_error() {
    let app = app_with_users().await;

    for payload in [json!({"name": "  "}), json!({"name": null}), json!({})] {
        let (status, body) = app.post("alice", "/api/v2/groups", Some(payload)).await;

        assert_eq!(status, StatusCode::BAD_REQUEST);
        assert_error(
            &body,
            400,
            "Bad Request",
            "VALIDATION_ERROR",
            "Group name is required",
            "/api/v2/groups",
        );
        assert_eq!(body["fieldErrors"], json!({}));
    }
}

#[tokio::test]
async fn test_create_group_without_body_is_an_internal_error() {
    let app = app_with_users().await;

    let (status, body) = app.post("alice", "/api/v2/groups", None).await;

    assert_eq!(status, StatusCode::INTERNAL_SERVER_ERROR);
    assert_error(
        &body,
        500,
        "Internal Server Error",
        "INTERNAL_ERROR",
        "Ein interner Fehler ist aufgetreten",
        "/api/v2/groups",
    );
}

// ===== DELETE /groups/{group} =====

#[tokio::test]
async fn test_owner_deletes_group_and_event_is_fired() {
    let app = app_with_users().await;
    let team = app.add_group("alice", "Team").await;

    let (status, body) = app.delete("alice", &format!("/api/v2/groups/{team}")).await;

    assert_eq!(status, StatusCode::NO_CONTENT);
    assert_eq!(body, Value::Null);
    assert!(app.members_of(team).await.is_empty());
    let events = app.events("GroupDeletedEvent").await;
    assert_eq!(events[0]["group_id"], json!(team));
    assert_eq!(events[0]["acting_user_id"], json!("alice"));
}

#[tokio::test]
async fn test_only_owner_may_delete_group() {
    let app = app_with_users().await;
    let team = app.add_group("alice", "Team").await;
    app.add_member(team, "bob").await;
    let uri = format!("/api/v2/groups/{team}");

    let (status, body) = app.delete("bob", &uri).await;

    assert_eq!(status, StatusCode::FORBIDDEN);
    assert_error(
        &body,
        403,
        "Forbidden",
        "FORBIDDEN",
        "Only the group owner can delete a group",
        &uri,
    );
}

#[tokio::test]
async fn test_deleting_unknown_group_is_forbidden_as_in_java() {
    // Java checks ownership first; an unknown group has no owner
    let app = app_with_users().await;

    let (status, body) = app.delete("alice", "/api/v2/groups/999999").await;

    assert_eq!(status, StatusCode::FORBIDDEN);
    assert_eq!(
        body["message"],
        json!("Only the group owner can delete a group")
    );
}

#[tokio::test]
async fn test_non_numeric_group_id_is_a_plain_404() {
    let app = app_with_users().await;

    let (status, body) = app.delete("alice", "/api/v2/groups/abc").await;

    assert_eq!(status, StatusCode::NOT_FOUND);
    assert_eq!(body["status"], json!(404));
    assert_eq!(body["error"], json!("Not Found"));
    assert_eq!(body["message"], json!("HTTP 404 Not Found"));
    assert_eq!(body["path"], json!("/api/v2/groups/abc"));
    assert!(body.get("errorCode").is_none(), "{body}");
}

// ===== PATCH /groups/{group} =====

#[tokio::test]
async fn test_owner_renames_group_and_event_is_fired() {
    let app = app_with_users().await;
    let team = app.add_group("alice", "Alt").await;

    let (status, body) = app
        .patch(
            "alice",
            &format!("/api/v2/groups/{team}"),
            Some(json!({"name": "Neu"})),
        )
        .await;

    assert_eq!(status, StatusCode::OK);
    assert_eq!(body, json!({"id": team, "name": "Neu", "members": null}));
    let events = app.events("GroupNameChangedEvent").await;
    assert_eq!(events[0]["group_id"], json!(team));
    assert_eq!(events[0]["old_name"], json!("Alt"));
    assert_eq!(events[0]["new_name"], json!("Neu"));
    assert_eq!(events[0]["acting_user_id"], json!("alice"));
}

#[tokio::test]
async fn test_only_owner_may_edit_group() {
    let app = app_with_users().await;
    let team = app.add_group("alice", "Team").await;
    app.add_member(team, "bob").await;
    let uri = format!("/api/v2/groups/{team}");

    let (status, body) = app.patch("bob", &uri, Some(json!({"name": "X"}))).await;

    assert_eq!(status, StatusCode::FORBIDDEN);
    assert_error(
        &body,
        403,
        "Forbidden",
        "FORBIDDEN",
        "Only the group owner can edit a group",
        &uri,
    );
}

#[tokio::test]
async fn test_edit_group_with_blank_name_is_a_validation_error() {
    let app = app_with_users().await;
    let team = app.add_group("alice", "Team").await;

    let (status, body) = app
        .patch(
            "alice",
            &format!("/api/v2/groups/{team}"),
            Some(json!({"name": ""})),
        )
        .await;

    assert_eq!(status, StatusCode::BAD_REQUEST);
    assert_eq!(body["errorCode"], json!("VALIDATION_ERROR"));
    assert_eq!(body["message"], json!("Group name is required"));
}

// ===== GET /groups/{group}/users =====

#[tokio::test]
async fn test_member_lists_group_users_with_names() {
    let app = app_with_users().await;
    let team = app.add_group("alice", "Team").await;
    app.add_member(team, "bob").await;
    app.add_member(team, "ghost").await;

    let (status, body) = app
        .get("bob", &format!("/api/v2/groups/{team}/users"))
        .await;

    assert_eq!(status, StatusCode::OK);
    assert_eq!(
        body,
        json!([
            {"id": "alice", "first_name": "Alice", "last_name": "Anders"},
            {"id": "bob", "first_name": "Bob", "last_name": "Becker"},
            {"id": "ghost", "first_name": null, "last_name": null}
        ])
    );
}

#[tokio::test]
async fn test_non_member_cannot_list_group_users() {
    let app = app_with_users().await;
    let team = app.add_group("alice", "Team").await;
    let uri = format!("/api/v2/groups/{team}/users");

    let (status, body) = app.get("carol", &uri).await;

    assert_eq!(status, StatusCode::FORBIDDEN);
    assert_error(
        &body,
        403,
        "Forbidden",
        "FORBIDDEN",
        "You are not a member of this group",
        &uri,
    );
}

// ===== POST /groups/{groupId}/user/{userId} =====

#[tokio::test]
async fn test_member_adds_friend_to_group_and_event_is_fired() {
    let app = app_with_users().await;
    let team = app.add_group("alice", "Team").await;
    app.add_friendship("alice", "carol", "ACCEPTED").await;

    let (status, body) = app
        .post("alice", &format!("/api/v2/groups/{team}/user/carol"), None)
        .await;

    assert_eq!(status, StatusCode::NO_CONTENT);
    assert_eq!(body, Value::Null);
    assert_eq!(app.members_of(team).await, vec!["alice", "carol"]);
    let events = app.events("GroupMemberAddedEvent").await;
    assert_eq!(events[0]["group_id"], json!(team));
    assert_eq!(events[0]["new_member_id"], json!("carol"));
    assert_eq!(events[0]["acting_user_id"], json!("alice"));
}

#[tokio::test]
async fn test_non_member_cannot_add_group_member() {
    let app = app_with_users().await;
    let team = app.add_group("alice", "Team").await;
    app.add_friendship("carol", "bob", "ACCEPTED").await;
    let uri = format!("/api/v2/groups/{team}/user/bob");

    let (status, body) = app.post("carol", &uri, None).await;

    assert_eq!(status, StatusCode::FORBIDDEN);
    assert_error(
        &body,
        403,
        "Forbidden",
        "FORBIDDEN",
        "You can only add members to group you are a member of",
        &uri,
    );
}

#[tokio::test]
async fn test_can_only_add_friends_to_group() {
    let app = app_with_users().await;
    let team = app.add_group("alice", "Team").await;
    app.add_friendship("alice", "bob", "PENDING").await;

    let (status, body) = app
        .post("alice", &format!("/api/v2/groups/{team}/user/bob"), None)
        .await;

    assert_eq!(status, StatusCode::FORBIDDEN);
    assert_eq!(body["message"], json!("You can only add friends to groups"));
}

#[tokio::test]
async fn test_adding_existing_member_is_a_field_validation_error() {
    let app = app_with_users().await;
    let team = app.add_group("alice", "Team").await;
    app.add_member(team, "bob").await;
    app.add_friendship("alice", "bob", "ACCEPTED").await;
    let uri = format!("/api/v2/groups/{team}/user/bob");

    let (status, body) = app.post("alice", &uri, None).await;

    assert_eq!(status, StatusCode::BAD_REQUEST);
    assert_error(
        &body,
        400,
        "Bad Request",
        "VALIDATION_ERROR",
        "Validierungsfehler bei Feld: user",
        &uri,
    );
    assert_eq!(
        body["fieldErrors"],
        json!({"user": "This user is already a member of this group"})
    );
}

// ===== DELETE /groups/{groupId}/user/{userId} =====

#[tokio::test]
async fn test_owner_removes_member_and_event_is_fired() {
    let app = app_with_users().await;
    let team = app.add_group("alice", "Team").await;
    app.add_member(team, "bob").await;

    let (status, body) = app
        .delete("alice", &format!("/api/v2/groups/{team}/user/bob"))
        .await;

    assert_eq!(status, StatusCode::NO_CONTENT);
    assert_eq!(body, Value::Null);
    assert_eq!(app.members_of(team).await, vec!["alice"]);
    let events = app.events("GroupMemberRemovedEvent").await;
    assert_eq!(events[0]["group_id"], json!(team));
    assert_eq!(events[0]["removed_member_id"], json!("bob"));
    assert_eq!(events[0]["acting_user_id"], json!("alice"));
}

#[tokio::test]
async fn test_owner_cannot_remove_themselves() {
    let app = app_with_users().await;
    let team = app.add_group("alice", "Team").await;

    let (status, body) = app
        .delete("alice", &format!("/api/v2/groups/{team}/user/alice"))
        .await;

    assert_eq!(status, StatusCode::FORBIDDEN);
    assert_eq!(
        body["message"],
        json!("You cannot remove yourself from a group")
    );
}

#[tokio::test]
async fn test_only_owner_can_remove_members() {
    let app = app_with_users().await;
    let team = app.add_group("alice", "Team").await;
    app.add_member(team, "bob").await;
    app.add_member(team, "carol").await;

    let (status, body) = app
        .delete("bob", &format!("/api/v2/groups/{team}/user/carol"))
        .await;

    assert_eq!(status, StatusCode::FORBIDDEN);
    assert_eq!(
        body["message"],
        json!("Only the group owner can remove members")
    );
}

#[tokio::test]
async fn test_removing_a_non_member_is_a_validation_error() {
    let app = app_with_users().await;
    let team = app.add_group("alice", "Team").await;

    let (status, body) = app
        .delete("alice", &format!("/api/v2/groups/{team}/user/carol"))
        .await;

    assert_eq!(status, StatusCode::BAD_REQUEST);
    assert_eq!(body["errorCode"], json!("VALIDATION_ERROR"));
    assert_eq!(
        body["message"],
        json!("This user is not member of this group")
    );
    assert_eq!(body["fieldErrors"], json!({}));
}

// ===== regression: appointment errors are not touched by `fill_error_path` =====

#[tokio::test]
async fn test_appointment_error_responses_keep_their_plain_text_body() {
    let app = app_with_users().await;

    let (status, body) = app.get_raw("alice", "/api/v2/appointments/987654321").await;

    // `AppointmentError` does not use `AppError`, so the path middleware leaves it as it was
    assert_eq!(status, StatusCode::NOT_FOUND);
    assert_eq!(body, "Appointment not found");
}
