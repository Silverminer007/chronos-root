/// Integration tests for `/api/v2/push/*`
///
/// Contract source: the Java `PushResource` (see ADR-2026-10-20-019). Token validation needs a
/// live Keycloak JWKS, so the principal is injected the way the auth middleware does after a
/// successful validation. 401 is covered through the real `build_router`.
use axum::{
    body::Body,
    extract::Request,
    http::StatusCode,
    middleware::{self, Next},
    Router,
};
use chronos_date_api::app::{build_router, push_routes};
use chronos_date_api::appointments::handlers::AppState;
use chronos_date_api::event_bus::postgres::PostgresEventBus;
use chronos_date_api::keycloak::KeycloakUrls;
use chronos_date_api::push_notifications::PushState;
use chronos_date_api::security::{PrincipalContext, TokenValidator};
use chronos_date_api::test_utils::{FakeKeycloak, TestDb};
use chronos_date_api::users::UserState;
use serde_json::{json, Value};
use std::sync::Arc;
use tower::Service;

const PUBLIC_KEY: &str = "BPublicKeyForTests";

fn push_state(db: &TestDb) -> Arc<PushState> {
    Arc::new(PushState::new(db.pool().clone(), PUBLIC_KEY.to_string()))
}

async fn call(
    router: &mut Router,
    method: &str,
    uri: &str,
    body: Option<&str>,
) -> (StatusCode, Option<String>, String) {
    let mut request = Request::builder().method(method).uri(uri);
    if body.is_some() {
        request = request.header("content-type", "application/json");
    }
    let request = request
        .body(body.map_or_else(Body::empty, |b| Body::from(b.to_string())))
        .unwrap();
    let response = router.call(request).await.expect("router call failed");
    let status = response.status();
    let content_type = response
        .headers()
        .get("content-type")
        .map(|v| v.to_str().unwrap().to_string());
    let bytes = axum::body::to_bytes(response.into_body(), usize::MAX)
        .await
        .unwrap();
    (
        status,
        content_type,
        String::from_utf8(bytes.to_vec()).unwrap(),
    )
}

fn authenticated(db: &TestDb, user: &str) -> Router {
    let principal = PrincipalContext::new(user);
    push_routes(push_state(db)).layer(middleware::from_fn(
        move |mut request: Request, next: Next| {
            let principal = principal.clone();
            async move {
                request.extensions_mut().insert(Arc::new(principal));
                next.run(request).await
            }
        },
    ))
}

fn subscription(endpoint: &str) -> String {
    json!({"endpoint": endpoint, "keys": {"p256dh": "p256", "auth": "auth"}}).to_string()
}

async fn db() -> TestDb {
    TestDb::new().await.expect("test database")
}

#[tokio::test]
async fn public_key_is_returned_as_plain_json_content() {
    let db = db().await;
    let mut router = authenticated(&db, "u1");
    let (status, content_type, body) =
        call(&mut router, "GET", "/api/v2/push/public-key", None).await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(content_type.as_deref(), Some("application/json"));
    assert_eq!(body, PUBLIC_KEY);
}

#[tokio::test]
async fn subscribe_stores_the_subscription_and_status_reports_it() {
    let db = db().await;
    let mut router = authenticated(&db, "u1");
    let endpoint = "https://push.example.org/abc";

    let (status, _, body) = call(
        &mut router,
        "GET",
        "/api/v2/push/status?endpoint=https%3A%2F%2Fpush.example.org%2Fabc",
        None,
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(
        serde_json::from_str::<Value>(&body).unwrap(),
        json!({"exists": false})
    );

    let (status, _, body) = call(
        &mut router,
        "POST",
        "/api/v2/push/subscribe",
        Some(&subscription(endpoint)),
    )
    .await;
    assert_eq!(status, StatusCode::NO_CONTENT);
    assert!(body.is_empty());

    let (_, _, body) = call(
        &mut router,
        "GET",
        "/api/v2/push/status?endpoint=https%3A%2F%2Fpush.example.org%2Fabc",
        None,
    )
    .await;
    assert_eq!(
        serde_json::from_str::<Value>(&body).unwrap(),
        json!({"exists": true})
    );

    let row: (String, String, String) = sqlx::query_as(
        "SELECT user_oidcid, p256dh, auth FROM pushsubscription WHERE endpoint = $1",
    )
    .bind(endpoint)
    .fetch_one(db.pool())
    .await
    .unwrap();
    assert_eq!(row, ("u1".into(), "p256".into(), "auth".into()));
}

#[tokio::test]
async fn subscribing_an_existing_endpoint_replaces_it() {
    let db = db().await;
    let endpoint = "https://push.example.org/shared";
    let mut first = authenticated(&db, "u1");
    let mut second = authenticated(&db, "u2");

    call(
        &mut first,
        "POST",
        "/api/v2/push/subscribe",
        Some(&subscription(endpoint)),
    )
    .await;
    let (status, _, _) = call(
        &mut second,
        "POST",
        "/api/v2/push/subscribe",
        Some(&subscription(endpoint)),
    )
    .await;
    assert_eq!(status, StatusCode::NO_CONTENT);

    let owners: Vec<String> =
        sqlx::query_scalar("SELECT user_oidcid FROM pushsubscription WHERE endpoint = $1")
            .bind(endpoint)
            .fetch_all(db.pool())
            .await
            .unwrap();
    assert_eq!(owners, vec!["u2".to_string()]);
}

#[tokio::test]
async fn unsubscribe_removes_the_endpoint_and_ignores_unknown_ones() {
    let db = db().await;
    let mut router = authenticated(&db, "u1");
    call(
        &mut router,
        "POST",
        "/api/v2/push/subscribe",
        Some(&subscription("https://push.example.org/abc")),
    )
    .await;

    let (status, _, body) = call(
        &mut router,
        "DELETE",
        "/api/v2/push/unsubscribe?endpoint=https%3A%2F%2Fpush.example.org%2Fabc",
        None,
    )
    .await;
    assert_eq!(status, StatusCode::NO_CONTENT);
    assert!(body.is_empty());
    let (status, _, _) = call(
        &mut router,
        "DELETE",
        "/api/v2/push/unsubscribe?endpoint=https%3A%2F%2Fpush.example.org%2Fabc",
        None,
    )
    .await;
    assert_eq!(status, StatusCode::NO_CONTENT);

    let count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM pushsubscription")
        .fetch_one(db.pool())
        .await
        .unwrap();
    assert_eq!(count, 0);
}

#[tokio::test]
async fn all_push_routes_require_a_token() {
    let db = db().await;
    let app_state = Arc::new(AppState {
        db_pool: db.pool().clone(),
        event_publisher: Arc::new(PostgresEventBus::new(db.pool().clone())),
    });
    let user_state = Arc::new(UserState {
        db_pool: db.pool().clone(),
        keycloak: Arc::new(FakeKeycloak::default()),
        keycloak_urls: KeycloakUrls::new("https://auth.test", "chronos", "chronos-api"),
    });
    let mut router = build_router(
        app_state,
        user_state,
        push_state(&db),
        Arc::new(TokenValidator::new(
            "http://127.0.0.1:1/realms/chronos".to_string(),
        )),
    );
    for (method, uri) in [
        ("GET", "/api/v2/push/public-key"),
        ("POST", "/api/v2/push/subscribe"),
        ("GET", "/api/v2/push/status"),
        ("DELETE", "/api/v2/push/unsubscribe"),
    ] {
        let (status, _, _) = call(&mut router, method, uri, None).await;
        assert_eq!(status, StatusCode::UNAUTHORIZED, "{method} {uri}");
    }
}
