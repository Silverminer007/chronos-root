//! Integration tests for the friendship endpoints (`/api/v2/friendships`).
//!
//! The behaviour mirrors the Java `FriendshipResource` / `FriendshipService`
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
}

// ===== authentication =====

#[tokio::test]
async fn test_friendship_routes_require_authentication() {
    let app = app_with_users().await;
    for (method, uri) in [
        ("GET", "/api/v2/friendships/suggestions"),
        ("POST", "/api/v2/friendships/requests"),
        ("POST", "/api/v2/friendships/requests/1/accept"),
        ("POST", "/api/v2/friendships/requests/1/decline"),
        ("DELETE", "/api/v2/friendships/requests/1"),
        ("GET", "/api/v2/friendships/requests/incoming"),
        ("GET", "/api/v2/friendships/requests/outgoing"),
        ("GET", "/api/v2/friendships/friends"),
        ("DELETE", "/api/v2/friendships/friends/bob"),
    ] {
        let (status, _) = app.call(None, method, uri, None).await;
        assert_eq!(status, StatusCode::UNAUTHORIZED, "{method} {uri}");
    }
}

// ===== GET /suggestions =====

#[tokio::test]
async fn test_suggestions_without_search_return_recent_non_friends_excluding_self() {
    let app = app_with_users().await;
    app.add_friendship("alice", "bob", "ACCEPTED").await;

    let (status, body) = app.get("alice", "/api/v2/friendships/suggestions").await;

    assert_eq!(status, StatusCode::OK);
    assert_eq!(
        body,
        json!([{"id": "carol", "first_name": "Carol", "last_name": "Clark"}])
    );
}

#[tokio::test]
async fn test_suggestions_with_one_character_search_behave_like_no_search() {
    let app = app_with_users().await;

    let (_, body) = app
        .get("alice", "/api/v2/friendships/suggestions?search=%20b%20")
        .await;

    assert_eq!(body.as_array().unwrap().len(), 2);
}

#[tokio::test]
async fn test_suggestions_search_matches_names_and_email_and_honours_limit() {
    let app = app_with_users().await;
    app.add_user("dave", "Dave", "Becker", "dave@example.com")
        .await;

    let (_, by_name) = app
        .get("alice", "/api/v2/friendships/suggestions?search=becker")
        .await;
    assert_eq!(by_name.as_array().unwrap().len(), 2);

    let (_, limited) = app
        .get(
            "alice",
            "/api/v2/friendships/suggestions?search=becker&limit=1",
        )
        .await;
    assert_eq!(limited.as_array().unwrap().len(), 1);

    let (_, by_email) = app
        .get("alice", "/api/v2/friendships/suggestions?search=carol@")
        .await;
    assert_eq!(by_email[0]["id"], json!("carol"));
}

#[tokio::test]
async fn test_suggestions_exclude_accepted_friends_but_not_pending_ones() {
    let app = app_with_users().await;
    app.add_friendship("alice", "bob", "ACCEPTED").await;
    app.add_friendship("alice", "carol", "PENDING").await;

    let (_, body) = app
        .get("alice", "/api/v2/friendships/suggestions?search=example")
        .await;

    assert_eq!(body.as_array().unwrap().len(), 1);
    assert_eq!(body[0]["id"], json!("carol"));
}

// ===== POST /requests =====

#[tokio::test]
async fn test_send_request_by_id_creates_pending_request_and_fires_event() {
    let app = app_with_users().await;

    let (status, body) = app
        .post("alice", "/api/v2/friendships/requests?adresse_id=bob", None)
        .await;

    assert_eq!(status, StatusCode::CREATED);
    assert_eq!(body, Value::Null);
    let (_, outgoing) = app
        .get("alice", "/api/v2/friendships/requests/outgoing")
        .await;
    let request_id = outgoing[0]["requestId"].as_i64().unwrap();
    let events = app.events("FriendshipRequestSentEvent").await;
    assert_eq!(events.len(), 1);
    assert_eq!(events[0]["request_id"], json!(request_id));
    assert_eq!(events[0]["requester_id"], json!("alice"));
    assert_eq!(events[0]["addressee_id"], json!("bob"));
    assert_eq!(events[0]["requester_name"], json!("Alice Anders"));
}

#[tokio::test]
async fn test_send_request_by_email_is_case_insensitive() {
    let app = app_with_users().await;

    let (status, _) = app
        .post(
            "alice",
            "/api/v2/friendships/requests?email=BOB@Example.com",
            None,
        )
        .await;

    assert_eq!(status, StatusCode::CREATED);
    let (_, incoming) = app
        .get("bob", "/api/v2/friendships/requests/incoming")
        .await;
    assert_eq!(incoming[0]["userId"], json!("alice"));
}

#[tokio::test]
async fn test_send_request_with_unknown_email_is_not_found() {
    let app = app_with_users().await;
    let uri = "/api/v2/friendships/requests?email=nobody@example.com";

    let (status, body) = app.post("alice", uri, None).await;

    assert_eq!(status, StatusCode::NOT_FOUND);
    assert_error(
        &body,
        404,
        "Not Found",
        "RESOURCE_NOT_FOUND",
        "Es wurde kein User mit der E-Mail Adresse nobody@example.com gefunden",
        "/api/v2/friendships/requests",
    );
}

#[tokio::test]
async fn test_send_request_needs_addressee_or_email() {
    let app = app_with_users().await;

    let (status, body) = app
        .post("alice", "/api/v2/friendships/requests", None)
        .await;

    assert_eq!(status, StatusCode::BAD_REQUEST);
    assert_error(
        &body,
        400,
        "Bad Request",
        "BAD_REQUEST",
        "You must either set addressee_id or email",
        "/api/v2/friendships/requests",
    );
}

#[tokio::test]
async fn test_send_request_to_self_is_rejected() {
    let app = app_with_users().await;

    let (status, body) = app
        .post(
            "alice",
            "/api/v2/friendships/requests?adresse_id=alice",
            None,
        )
        .await;

    assert_eq!(status, StatusCode::BAD_REQUEST);
    assert_eq!(body["errorCode"], json!("BAD_REQUEST"));
    assert_eq!(
        body["message"],
        json!("Du kannst dir nicht selbst eine Freundschaftsanfrage senden")
    );
}

#[tokio::test]
async fn test_send_request_to_unknown_user_is_accepted_like_java() {
    // Java's existsById falls back to Keycloak, which never reports "not found".
    let app = app_with_users().await;

    let (status, _) = app
        .post(
            "alice",
            "/api/v2/friendships/requests?adresse_id=ghost",
            None,
        )
        .await;

    assert_eq!(status, StatusCode::CREATED);
    let (_, outgoing) = app
        .get("alice", "/api/v2/friendships/requests/outgoing")
        .await;
    assert_eq!(outgoing[0]["userId"], json!("ghost"));
}

#[tokio::test]
async fn test_send_request_rejects_duplicates_with_validation_errors() {
    let app = app_with_users().await;
    app.add_friendship("alice", "bob", "PENDING").await;
    app.add_friendship("carol", "alice", "PENDING").await;
    app.add_user("dave", "Dave", "Dorn", "dave@example.com")
        .await;
    app.add_friendship("alice", "dave", "ACCEPTED").await;

    for (target, message) in [
        ("bob", "Du hast bereits eine Freundschaftsanfrage gesendet"),
        (
            "carol",
            "Dieser User hat dir bereits eine Freundschaftsanfrage gesendet. Bitte nimm diese an.",
        ),
        ("dave", "Ihr seid bereits befreundet"),
    ] {
        let (status, body) = app
            .post(
                "alice",
                &format!("/api/v2/friendships/requests?adresse_id={target}"),
                None,
            )
            .await;

        assert_eq!(status, StatusCode::BAD_REQUEST, "{target}");
        assert_eq!(body["errorCode"], json!("VALIDATION_ERROR"), "{target}");
        assert_eq!(body["message"], json!(message), "{target}");
        assert_eq!(body["fieldErrors"], json!({}), "{target}");
    }
}

#[tokio::test]
async fn test_send_request_replaces_a_declined_request() {
    let app = app_with_users().await;
    let old = app.add_friendship("bob", "alice", "DECLINED").await;

    let (status, _) = app
        .post("alice", "/api/v2/friendships/requests?adresse_id=bob", None)
        .await;

    assert_eq!(status, StatusCode::CREATED);
    assert_eq!(app.friendship_status(old).await, None);
    let (_, outgoing) = app
        .get("alice", "/api/v2/friendships/requests/outgoing")
        .await;
    assert_eq!(outgoing.as_array().unwrap().len(), 1);
}

// ===== POST /requests/{id}/accept|decline =====

#[tokio::test]
async fn test_addressee_accepts_request_and_event_is_fired() {
    let app = app_with_users().await;
    let id = app.add_friendship("alice", "bob", "PENDING").await;

    let (status, body) = app
        .post(
            "bob",
            &format!("/api/v2/friendships/requests/{id}/accept"),
            None,
        )
        .await;

    assert_eq!(status, StatusCode::OK);
    assert_eq!(body, Value::Null);
    assert_eq!(app.friendship_status(id).await.as_deref(), Some("ACCEPTED"));
    let events = app.events("FriendshipAcceptedEvent").await;
    assert_eq!(events[0]["request_id"], json!(id));
    assert_eq!(events[0]["requester_id"], json!("alice"));
    assert_eq!(events[0]["addressee_id"], json!("bob"));
}

#[tokio::test]
async fn test_addressee_declines_request_and_event_is_fired() {
    let app = app_with_users().await;
    let id = app.add_friendship("alice", "bob", "PENDING").await;

    let (status, _) = app
        .post(
            "bob",
            &format!("/api/v2/friendships/requests/{id}/decline"),
            None,
        )
        .await;

    assert_eq!(status, StatusCode::OK);
    assert_eq!(app.friendship_status(id).await.as_deref(), Some("DECLINED"));
    assert_eq!(app.events("FriendshipDeclinedEvent").await.len(), 1);
}

#[tokio::test]
async fn test_only_addressee_may_accept_or_decline() {
    let app = app_with_users().await;
    let id = app.add_friendship("alice", "bob", "PENDING").await;

    let accept_uri = format!("/api/v2/friendships/requests/{id}/accept");
    let (status, body) = app.post("alice", &accept_uri, None).await;
    assert_eq!(status, StatusCode::FORBIDDEN);
    assert_error(
        &body,
        403,
        "Forbidden",
        "FORBIDDEN",
        "Du kannst nur deine eigenen Freundschaftsanfragen annehmen",
        &accept_uri,
    );

    let (status, body) = app
        .post(
            "carol",
            &format!("/api/v2/friendships/requests/{id}/decline"),
            None,
        )
        .await;
    assert_eq!(status, StatusCode::FORBIDDEN);
    assert_eq!(
        body["message"],
        json!("Du kannst nur deine eigenen Freundschaftsanfragen ablehnen")
    );
    assert_eq!(app.friendship_status(id).await.as_deref(), Some("PENDING"));
}

#[tokio::test]
async fn test_answering_a_handled_or_unknown_request_fails() {
    let app = app_with_users().await;
    let id = app.add_friendship("alice", "bob", "ACCEPTED").await;

    let (status, body) = app
        .post(
            "bob",
            &format!("/api/v2/friendships/requests/{id}/accept"),
            None,
        )
        .await;
    assert_eq!(status, StatusCode::BAD_REQUEST);
    assert_eq!(body["errorCode"], json!("VALIDATION_ERROR"));
    assert_eq!(
        body["message"],
        json!("Diese Freundschaftsanfrage wurde bereits bearbeitet")
    );

    let (status, body) = app
        .post("bob", "/api/v2/friendships/requests/999999/decline", None)
        .await;
    assert_eq!(status, StatusCode::NOT_FOUND);
    assert_eq!(body["errorCode"], json!("RESOURCE_NOT_FOUND"));
    assert_eq!(
        body["message"],
        json!("Freundschaftsanfrage mit ID 999999 wurde nicht gefunden")
    );
}

// ===== DELETE /requests/{id} =====

#[tokio::test]
async fn test_requester_cancels_pending_request() {
    let app = app_with_users().await;
    let id = app.add_friendship("alice", "bob", "PENDING").await;

    let (status, body) = app
        .delete("alice", &format!("/api/v2/friendships/requests/{id}"))
        .await;

    assert_eq!(status, StatusCode::NO_CONTENT);
    assert_eq!(body, Value::Null);
    assert_eq!(app.friendship_status(id).await, None);
}

#[tokio::test]
async fn test_only_requester_may_cancel_a_pending_request() {
    let app = app_with_users().await;
    let id = app.add_friendship("alice", "bob", "PENDING").await;
    let done = app.add_friendship("alice", "carol", "ACCEPTED").await;

    let (status, body) = app
        .delete("bob", &format!("/api/v2/friendships/requests/{id}"))
        .await;
    assert_eq!(status, StatusCode::FORBIDDEN);
    assert_eq!(
        body["message"],
        json!("Du kannst nur deine eigenen Freundschaftsanfragen zurückziehen")
    );

    let (status, body) = app
        .delete("alice", &format!("/api/v2/friendships/requests/{done}"))
        .await;
    assert_eq!(status, StatusCode::BAD_REQUEST);
    assert_eq!(
        body["message"],
        json!("Diese Freundschaftsanfrage wurde bereits bearbeitet")
    );

    let (status, _) = app
        .delete("alice", "/api/v2/friendships/requests/999999")
        .await;
    assert_eq!(status, StatusCode::NOT_FOUND);
}

// ===== GET /requests/incoming|outgoing =====

#[tokio::test]
async fn test_incoming_and_outgoing_requests_have_the_java_shape() {
    let app = app_with_users().await;
    let incoming_id = app.add_friendship("bob", "alice", "PENDING").await;
    let outgoing_id = app.add_friendship("alice", "carol", "PENDING").await;
    app.add_friendship("alice", "dave", "DECLINED").await;

    let (status, incoming) = app
        .get("alice", "/api/v2/friendships/requests/incoming")
        .await;
    assert_eq!(status, StatusCode::OK);
    let created_at = incoming[0]["createdAt"].as_str().unwrap().to_string();
    assert!(
        created_at.ends_with('Z') && created_at.contains('T'),
        "{created_at}"
    );
    assert_eq!(
        incoming,
        json!([{
            "requestId": incoming_id,
            "userId": "bob",
            "userName": "Bob Becker",
            "userEmail": "bob@example.com",
            "status": "PENDING",
            "createdAt": created_at,
            "incoming": true
        }])
    );

    let (_, outgoing) = app
        .get("alice", "/api/v2/friendships/requests/outgoing")
        .await;
    assert_eq!(outgoing.as_array().unwrap().len(), 1);
    assert_eq!(outgoing[0]["requestId"], json!(outgoing_id));
    assert_eq!(outgoing[0]["userName"], json!("Carol Clark"));
    assert_eq!(outgoing[0]["incoming"], json!(false));
}

#[tokio::test]
async fn test_requests_are_sorted_newest_first_and_unknown_users_are_deleted_users() {
    let app = app_with_users().await;
    let first = app.add_friendship("bob", "alice", "PENDING").await;
    let second = app.add_friendship("ghost", "alice", "PENDING").await;
    sqlx::query(
        "UPDATE friendship_requests SET created_at = NOW() + interval '1 minute' WHERE id = $1",
    )
    .bind(second)
    .execute(app.db.pool())
    .await
    .unwrap();

    let (_, body) = app
        .get("alice", "/api/v2/friendships/requests/incoming")
        .await;

    assert_eq!(body[0]["requestId"], json!(second));
    assert_eq!(body[0]["userName"], json!("Gelöschter Benutzer"));
    assert!(body[0].get("userEmail").is_none(), "{}", body[0]);
    assert_eq!(body[1]["requestId"], json!(first));
}

// ===== GET /friends, DELETE /friends/{id} =====

#[tokio::test]
async fn test_friends_are_listed_sorted_by_name_from_both_directions() {
    let app = app_with_users().await;
    app.add_friendship("carol", "alice", "ACCEPTED").await;
    app.add_friendship("alice", "bob", "ACCEPTED").await;
    app.add_friendship("alice", "dave", "PENDING").await;

    let (status, body) = app.get("alice", "/api/v2/friendships/friends").await;

    assert_eq!(status, StatusCode::OK);
    let friends = body.as_array().unwrap();
    assert_eq!(friends.len(), 2);
    assert_eq!(friends[0]["user_id"], json!("bob"));
    assert_eq!(friends[0]["name"], json!("Bob Becker"));
    assert_eq!(friends[0]["email"], json!("bob@example.com"));
    assert!(friends[0]["friends_since"].as_str().unwrap().ends_with('Z'));
    assert!(friends[0].get("profile_picture_url").is_none());
    assert_eq!(friends[1]["user_id"], json!("carol"));
}

#[tokio::test]
async fn test_remove_friend_deletes_the_friendship_and_fires_event() {
    let app = app_with_users().await;
    let id = app.add_friendship("bob", "alice", "ACCEPTED").await;

    let (status, body) = app.delete("alice", "/api/v2/friendships/friends/bob").await;

    assert_eq!(status, StatusCode::NO_CONTENT);
    assert_eq!(body, Value::Null);
    assert_eq!(app.friendship_status(id).await, None);
    let events = app.events("FriendshipRemovedEvent").await;
    assert_eq!(events[0]["acting_user_id"], json!("alice"));
    assert_eq!(events[0]["friend_id"], json!("bob"));
}

#[tokio::test]
async fn test_remove_friend_who_is_not_a_friend_is_not_found() {
    let app = app_with_users().await;
    app.add_friendship("alice", "bob", "PENDING").await;

    let (status, body) = app.delete("alice", "/api/v2/friendships/friends/bob").await;

    assert_eq!(status, StatusCode::NOT_FOUND);
    assert_error(
        &body,
        404,
        "Not Found",
        "RESOURCE_NOT_FOUND",
        "Ihr seid nicht befreundet",
        "/api/v2/friendships/friends/bob",
    );
}
