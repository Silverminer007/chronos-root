//! Integration tests for the appointment responses of `GET /api/v2/appointments`,
//! `GET /api/v2/appointments/:id`, `PATCH /api/v2/appointments/:id` and
//! `GET /api/v2/admin/appointments` (ADR-2026-10-20-026).
//!
//! Java is the reference (`AppointmentsResource`, `AppointmentMapper`, `PagedResponse`):
//! the list is `{"data": [...], "meta": {page, size, total}}`, the appointment objects of
//! list, PATCH and admin list carry `participants`, `messages` and `group_participants`.

mod common;

use axum::http::StatusCode;
use common::TestApp;
use serde_json::{json, Value};

const OWNER: &str = "owner";
const GUEST: &str = "guest";

async fn app() -> TestApp {
    let app = TestApp::new().await;
    app.add_user(OWNER, "Olga", "Owner", "olga@example.com")
        .await;
    app.add_user(GUEST, "Gustav", "Guest", "gustav@example.com")
        .await;
    app.add_user("admin", "Ada", "Admin", "ada@example.com")
        .await;
    app.grant_admin("admin");
    app
}

async fn create(app: &TestApp, name: &str, start: &str, end: &str) -> i64 {
    let (status, body) = app
        .post(
            OWNER,
            "/api/v2/appointments",
            Some(json!({"name": name, "description": "Beschreibung", "venue": "Kirche", "start": start, "end": end})),
        )
        .await;
    assert_eq!(status, StatusCode::CREATED, "{body}");
    body["id"].as_i64().unwrap()
}

async fn create_future(app: &TestApp, name: &str) -> i64 {
    create(app, name, "2030-01-01T10:00:00Z", "2030-01-01T11:00:00Z").await
}

fn names(body: &Value) -> Vec<String> {
    body["data"]
        .as_array()
        .unwrap()
        .iter()
        .map(|a| a["name"].as_str().unwrap().to_string())
        .collect()
}

#[tokio::test]
async fn test_list_is_paged_with_java_meta_and_defaults() {
    let app = app().await;
    create_future(&app, "Eins").await;

    let (status, body) = app.get(OWNER, "/api/v2/appointments").await;

    assert_eq!(status, StatusCode::OK, "{body}");
    assert_eq!(body["meta"], json!({"page": 0, "size": 10, "total": 1}));
    assert_eq!(names(&body), vec!["Eins"]);
}

#[tokio::test]
async fn test_list_embeds_participants_messages_and_group_participants() {
    let app = app().await;
    let id = create_future(&app, "Eins").await;
    app.post(
        OWNER,
        &format!("/api/v2/appointments/{id}/participants/{GUEST}"),
        Some(json!({"user_role": "ATTENDANT"})),
    )
    .await;
    app.post(
        OWNER,
        &format!("/api/v2/appointments/{id}/messages"),
        Some(json!({"body": "Hallo"})),
    )
    .await;
    let group = app.add_group(OWNER, "Jugend").await;
    app.add_member(group, GUEST).await;
    app.post(
        OWNER,
        &format!("/api/v2/appointments/{id}/groups/{group}"),
        Some(json!({"user_role": "ATTENDANT"})),
    )
    .await;

    let (_, body) = app.get(OWNER, "/api/v2/appointments").await;

    let appointment = &body["data"][0];
    let participants = appointment["participants"].as_array().unwrap();
    let owner = participants
        .iter()
        .find(|p| p["user_id"] == json!(OWNER))
        .unwrap();
    assert_eq!(owner["name"], json!("Olga Owner"));
    assert_eq!(owner["role"], json!("RESPONSIBLE"));
    assert!(owner["status"].is_string());
    assert!(participants.iter().any(|p| p["user_id"] == json!(GUEST)));
    let messages = appointment["messages"].as_array().unwrap();
    assert_eq!(messages[0]["body"], json!("Hallo"));
    assert_eq!(messages[0]["sender_name"], json!("Olga Owner"));
    assert_eq!(
        appointment["group_participants"],
        json!([{"id": group, "name": "Jugend", "members": [
            {"id": OWNER, "first_name": "Olga", "last_name": "Owner"},
            {"id": GUEST, "first_name": "Gustav", "last_name": "Guest"}
        ]}])
    );
}

#[tokio::test]
async fn test_list_of_an_appointment_without_extras_has_empty_arrays() {
    let app = app().await;
    create_future(&app, "Eins").await;

    let (_, body) = app.get(OWNER, "/api/v2/appointments").await;

    assert_eq!(body["data"][0]["messages"], json!([]));
    assert_eq!(body["data"][0]["group_participants"], json!([]));
}

#[tokio::test]
async fn test_list_only_returns_appointments_of_the_caller_ordered_by_start() {
    let app = app().await;
    create(
        &app,
        "Spaeter",
        "2030-02-01T10:00:00Z",
        "2030-02-01T11:00:00Z",
    )
    .await;
    create(
        &app,
        "Frueher",
        "2030-01-01T10:00:00Z",
        "2030-01-01T11:00:00Z",
    )
    .await;

    let (_, body) = app.get(OWNER, "/api/v2/appointments").await;
    assert_eq!(names(&body), vec!["Frueher", "Spaeter"]);

    let (_, other) = app.get(GUEST, "/api/v2/appointments").await;
    assert_eq!(other["data"], json!([]));
    assert_eq!(other["meta"]["total"], json!(0));
}

#[tokio::test]
async fn test_list_hides_deleted_and_past_appointments_by_default() {
    let app = app().await;
    create_future(&app, "Zukunft").await;
    create(
        &app,
        "Vergangen",
        "2020-01-01T10:00:00Z",
        "2020-01-01T11:00:00Z",
    )
    .await;
    let deleted = create_future(&app, "Geloescht").await;
    app.delete(OWNER, &format!("/api/v2/appointments/{deleted}"))
        .await;

    let (_, body) = app.get(OWNER, "/api/v2/appointments").await;

    assert_eq!(names(&body), vec!["Zukunft"]);
    assert_eq!(body["meta"]["total"], json!(1));
}

#[tokio::test]
async fn test_list_start_and_end_widen_or_narrow_the_window() {
    let app = app().await;
    create(
        &app,
        "Vergangen",
        "2020-01-01T10:00:00Z",
        "2020-01-01T11:00:00Z",
    )
    .await;
    create_future(&app, "Zukunft").await;

    let (_, from_2019) = app
        .get(OWNER, "/api/v2/appointments?start=2019-01-01T00:00:00Z")
        .await;
    assert_eq!(names(&from_2019), vec!["Vergangen", "Zukunft"]);

    let (_, window) = app
        .get(
            OWNER,
            "/api/v2/appointments?start=2019-01-01T00:00:00Z&end=2021-01-01T00:00:00Z",
        )
        .await;
    assert_eq!(names(&window), vec!["Vergangen"]);
}

#[tokio::test]
async fn test_list_search_matches_name_description_and_venue_case_insensitively() {
    let app = app().await;
    create_future(&app, "Gruppenstunde").await;
    create_future(&app, "Zeltlager").await;

    let (_, by_name) = app.get(OWNER, "/api/v2/appointments?search=GRUPPEN").await;
    assert_eq!(names(&by_name), vec!["Gruppenstunde"]);
    assert_eq!(by_name["meta"]["total"], json!(1));

    let (_, by_venue) = app.get(OWNER, "/api/v2/appointments?search=kirche").await;
    assert_eq!(by_venue["meta"]["total"], json!(2));

    let (_, none) = app
        .get(OWNER, "/api/v2/appointments?search=gibtesnicht")
        .await;
    assert_eq!(none["data"], json!([]));
}

#[tokio::test]
async fn test_list_pages_with_page_and_size() {
    let app = app().await;
    for (i, name) in ["A", "B", "C"].iter().enumerate() {
        create(
            &app,
            name,
            &format!("2030-01-0{}T10:00:00Z", i + 1),
            &format!("2030-01-0{}T11:00:00Z", i + 1),
        )
        .await;
    }

    let (_, page) = app.get(OWNER, "/api/v2/appointments?page=1&size=2").await;

    assert_eq!(names(&page), vec!["C"]);
    assert_eq!(page["meta"], json!({"page": 1, "size": 2, "total": 3}));
}

#[tokio::test]
async fn test_list_rejects_invalid_paging_and_instants() {
    let app = app().await;
    for query in ["size=0", "page=-1", "start=gestern", "end=morgen"] {
        let (status, body) = app
            .get(OWNER, &format!("/api/v2/appointments?{query}"))
            .await;
        assert_eq!(status, StatusCode::BAD_REQUEST, "{query}: {body}");
        assert_eq!(body["status"], json!(400), "{query}: {body}");
    }
    // Jakarta REST: a value that is no int is a 404
    let (status, _) = app.get(OWNER, "/api/v2/appointments?page=abc").await;
    assert_eq!(status, StatusCode::NOT_FOUND);
}

#[tokio::test]
async fn test_get_embeds_only_what_the_flags_ask_for() {
    let app = app().await;
    let id = create_future(&app, "Eins").await;
    let uri = format!("/api/v2/appointments/{id}");

    let (_, plain) = app.get(OWNER, &uri).await;
    for field in ["participants", "messages", "group_participants"] {
        assert!(plain.get(field).is_none(), "{field}: {plain}");
    }

    let (_, with) = app
        .get(
            OWNER,
            &format!("{uri}?participants=true&messages=true&group_participants=true"),
        )
        .await;
    assert_eq!(with["participants"][0]["user_id"], json!(OWNER));
    assert_eq!(with["messages"], json!([]));
    assert_eq!(with["group_participants"], json!([]));
}

#[tokio::test]
async fn test_patch_embeds_participants_messages_and_group_participants() {
    let app = app().await;
    let id = create_future(&app, "Eins").await;

    let (status, body) = app
        .patch(
            OWNER,
            &format!("/api/v2/appointments/{id}"),
            Some(json!({"venue": "Heim"})),
        )
        .await;

    assert_eq!(status, StatusCode::OK, "{body}");
    assert_eq!(body["venue"], json!("Heim"));
    assert_eq!(body["participants"][0]["user_id"], json!(OWNER));
    assert_eq!(body["messages"], json!([]));
    assert_eq!(body["group_participants"], json!([]));
}

#[tokio::test]
async fn test_create_does_not_embed() {
    let app = app().await;
    let (_, body) = app
        .post(
            OWNER,
            "/api/v2/appointments",
            Some(json!({"name": "Neu", "start": "2030-01-01T10:00:00Z", "end": "2030-01-01T11:00:00Z"})),
        )
        .await;

    for field in ["participants", "messages", "group_participants"] {
        assert!(body.get(field).is_none(), "{field}: {body}");
    }
}

#[tokio::test]
async fn test_admin_list_embeds_participants_messages_and_group_participants() {
    let app = app().await;
    create_future(&app, "Eins").await;

    let (status, body) = app
        .get("admin", "/api/v2/admin/appointments?page=0&size=5")
        .await;

    assert_eq!(status, StatusCode::OK, "{body}");
    let appointment = &body["data"][0];
    assert_eq!(appointment["participants"][0]["user_id"], json!(OWNER));
    assert_eq!(appointment["participants"][0]["name"], json!("Olga Owner"));
    assert_eq!(appointment["messages"], json!([]));
    assert_eq!(appointment["group_participants"], json!([]));
}
