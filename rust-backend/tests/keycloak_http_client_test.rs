/// `HttpKeycloakAdmin` against a local mock of the Keycloak token and admin endpoints
use axum::{
    extract::{Path, Query, State},
    http::{HeaderMap, StatusCode},
    routing::{delete, get, post, put},
    Form, Json, Router,
};
use chronos_date_api::keycloak::{
    HttpKeycloakAdmin, KeycloakAdmin, KeycloakConfig, KeycloakError, UserUpdate,
};
use serde_json::{json, Value};
use std::collections::HashMap;
use std::sync::{Arc, Mutex};

#[derive(Default)]
struct Recorded {
    token_requests: Vec<HashMap<String, String>>,
    /// `METHOD path?query` plus the JSON body of every admin call
    admin_calls: Vec<(String, Option<Value>)>,
    bearer_tokens: Vec<String>,
}

type Shared = Arc<Mutex<Recorded>>;

async fn token(
    State(rec): State<Shared>,
    Form(form): Form<HashMap<String, String>>,
) -> Json<Value> {
    rec.lock().unwrap().token_requests.push(form);
    Json(json!({"access_token": "admin-token", "expires_in": 300}))
}

async fn record(rec: &Shared, headers: &HeaderMap, call: String, body: Option<Value>) {
    let mut rec = rec.lock().unwrap();
    rec.bearer_tokens.push(
        headers
            .get("authorization")
            .and_then(|v| v.to_str().ok())
            .unwrap_or_default()
            .to_string(),
    );
    rec.admin_calls.push((call, body));
}

async fn start_mock() -> (String, Shared) {
    let rec = Shared::default();
    let app = Router::new()
        .route("/realms/chronos/protocol/openid-connect/token", post(token))
        .route(
            "/admin/realms/chronos/users/:id",
            get(
                |State(rec): State<Shared>, Path(id): Path<String>, h: HeaderMap| async move {
                    record(&rec, &h, format!("GET user {id}"), None).await;
                    if id == "missing" {
                        return Err(StatusCode::NOT_FOUND);
                    }
                    Ok(Json(json!({"id": id, "email": "max@example.org", "enabled": true})))
                },
            )
            .put(
                |State(rec): State<Shared>, Path(id): Path<String>, h: HeaderMap, Json(b): Json<Value>| async move {
                    record(&rec, &h, format!("PUT user {id}"), Some(b)).await;
                    StatusCode::NO_CONTENT
                },
            )
            .delete(
                |State(rec): State<Shared>, Path(id): Path<String>, h: HeaderMap| async move {
                    record(&rec, &h, format!("DELETE user {id}"), None).await;
                    StatusCode::NO_CONTENT
                },
            ),
        )
        .route(
            "/admin/realms/chronos/users",
            get(
                |State(rec): State<Shared>, Query(q): Query<HashMap<String, String>>, h: HeaderMap| async move {
                    let mut q: Vec<_> = q.into_iter().collect();
                    q.sort();
                    record(&rec, &h, format!("GET users {q:?}"), None).await;
                    Json(json!([{"id": "u1", "firstName": "Max", "lastName": "Muster", "email": "max@example.org", "createdTimestamp": 1700000000123i64, "attributes": {"picture": ["https://pics.example/max.png"]}}, {"id": "u2"}]))
                },
            ),
        )
        .route(
            "/admin/realms/chronos/users/count",
            get(|State(rec): State<Shared>, h: HeaderMap| async move {
                record(&rec, &h, "GET users count".to_string(), None).await;
                Json(json!(42))
            }),
        )
        .route(
            "/admin/realms/chronos/users/:id/send-verify-email",
            put(
                |State(rec): State<Shared>, Path(id): Path<String>, Query(q): Query<HashMap<String, String>>, h: HeaderMap| async move {
                    let mut q: Vec<_> = q.into_iter().collect();
                    q.sort();
                    record(&rec, &h, format!("PUT verify {id} {q:?}"), None).await;
                    StatusCode::NO_CONTENT
                },
            ),
        )
        .route(
            "/admin/realms/chronos/users/:id/federated-identity",
            get(|State(rec): State<Shared>, Path(id): Path<String>, h: HeaderMap| async move {
                record(&rec, &h, format!("GET identities {id}"), None).await;
                Json(json!([{"identityProvider": "github", "userId": "1", "userName": "max"}]))
            }),
        )
        .route(
            "/admin/realms/chronos/users/:id/federated-identity/:provider",
            delete(
                |State(rec): State<Shared>, Path((id, provider)): Path<(String, String)>, h: HeaderMap| async move {
                    record(&rec, &h, format!("DELETE identity {id} {provider}"), None).await;
                    StatusCode::NO_CONTENT
                },
            ),
        )
        .route(
            "/admin/realms/chronos/users/:id/credentials",
            get(|State(rec): State<Shared>, Path(id): Path<String>, h: HeaderMap| async move {
                record(&rec, &h, format!("GET credentials {id}"), None).await;
                Json(json!([
                    {"id": "c1", "type": "password", "createdDate": 1},
                    {"id": "c2", "type": "webauthn-passwordless", "userLabel": "Laptop", "createdDate": 1700000000000i64}
                ]))
            }),
        )
        .with_state(rec.clone());
    let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
    let addr = listener.local_addr().unwrap();
    tokio::spawn(async move { axum::serve(listener, app).await.unwrap() });
    (format!("http://{addr}"), rec)
}

fn client(base_url: &str) -> HttpKeycloakAdmin {
    HttpKeycloakAdmin::new(KeycloakConfig {
        base_url: base_url.to_string(),
        realm: "chronos".into(),
        client_id: "chronos-api".into(),
        client_secret: "s3cret".into(),
    })
}

#[tokio::test]
async fn authenticates_with_client_credentials_once_and_sends_the_bearer_token() {
    let (base, rec) = start_mock().await;
    let keycloak = client(&base);

    keycloak.get_user("u1").await.unwrap();
    keycloak.federated_identities("u1").await.unwrap();

    let rec = rec.lock().unwrap();
    assert_eq!(rec.token_requests.len(), 1, "the token must be cached");
    let form = &rec.token_requests[0];
    assert_eq!(form["grant_type"], "client_credentials");
    assert_eq!(form["client_id"], "chronos-api");
    assert_eq!(form["client_secret"], "s3cret");
    assert!(rec.bearer_tokens.iter().all(|t| t == "Bearer admin-token"));
}

#[tokio::test]
async fn reads_users_identities_and_credentials() {
    let (base, _) = start_mock().await;
    let keycloak = client(&base);

    assert_eq!(
        keycloak.get_user("u1").await.unwrap().email.as_deref(),
        Some("max@example.org")
    );
    assert_eq!(
        keycloak.federated_identities("u1").await.unwrap()[0].identity_provider,
        "github"
    );
    let credentials = keycloak.credentials("u1").await.unwrap();
    assert_eq!(credentials.len(), 2);
    assert_eq!(credentials[1].credential_type, "webauthn-passwordless");
    assert_eq!(credentials[1].user_label.as_deref(), Some("Laptop"));
    assert_eq!(credentials[1].created_date, Some(1_700_000_000_000));
}

#[tokio::test]
async fn writes_use_the_keycloak_representation_names() {
    let (base, rec) = start_mock().await;
    let keycloak = client(&base);

    keycloak
        .update_user(
            "u1",
            &UserUpdate {
                first_name: Some("Max".into()),
                last_name: None,
                email: Some("new@example.org".into()),
                email_verified: Some(false),
            },
        )
        .await
        .unwrap();
    keycloak
        .send_verify_email("u1", "https://app/cb")
        .await
        .unwrap();
    keycloak
        .remove_federated_identity("u1", "github")
        .await
        .unwrap();
    keycloak.delete_user("u1").await.unwrap();

    let calls = rec.lock().unwrap().admin_calls.clone();
    assert_eq!(
        calls[0],
        (
            "PUT user u1".to_string(),
            Some(json!({"firstName": "Max", "email": "new@example.org", "emailVerified": false}))
        )
    );
    assert_eq!(
        calls[1].0,
        r#"PUT verify u1 [("client_id", "chronos-api"), ("lifespan", "3600"), ("redirect_uri", "https://app/cb")]"#
    );
    assert_eq!(calls[2].0, "DELETE identity u1 github");
    assert_eq!(calls[3].0, "DELETE user u1");
}

#[tokio::test]
async fn maps_404_to_not_found_and_everything_else_to_failed() {
    let (base, _) = start_mock().await;
    assert_eq!(
        client(&base).get_user("missing").await.unwrap_err(),
        KeycloakError::NotFound
    );

    // credentials/identity endpoints of an unknown route answer 404 as well; an unreachable server is a failure
    let unreachable = client("http://127.0.0.1:1");
    assert!(matches!(
        unreachable.get_user("u1").await.unwrap_err(),
        KeycloakError::Failed(_)
    ));
}

#[tokio::test]
async fn lists_and_counts_the_users_of_the_realm() {
    let (base, rec) = start_mock().await;
    let client = client(&base);

    let users = client.list_users(40, 20).await.unwrap();
    assert_eq!(client.count_users().await.unwrap(), 42);

    assert_eq!(users.len(), 2);
    assert_eq!(users[0].id.as_deref(), Some("u1"));
    assert_eq!(users[0].first_name.as_deref(), Some("Max"));
    assert_eq!(users[0].last_name.as_deref(), Some("Muster"));
    assert_eq!(users[0].email.as_deref(), Some("max@example.org"));
    assert_eq!(users[0].created_timestamp, Some(1_700_000_000_123));
    assert_eq!(users[1].first_name, None);
    let calls = rec.lock().unwrap().admin_calls.clone();
    assert_eq!(calls[0].0, r#"GET users [("first", "40"), ("max", "20")]"#);
    assert_eq!(calls[1].0, "GET users count");
}

#[tokio::test]
async fn searches_users_with_the_first_page_of_max_results() {
    let (base, rec) = start_mock().await;
    let client = client(&base);

    let users = client.search_users("max mus", 15).await.unwrap();

    assert_eq!(users.len(), 2);
    assert_eq!(users[0].id.as_deref(), Some("u1"));
    assert_eq!(
        users[0].picture.as_deref(),
        Some("https://pics.example/max.png")
    );
    assert_eq!(users[1].picture, None);
    let calls = rec.lock().unwrap().admin_calls.clone();
    assert_eq!(
        calls[0].0,
        r#"GET users [("first", "0"), ("max", "15"), ("search", "max mus")]"#
    );
}
