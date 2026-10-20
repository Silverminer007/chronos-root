/// Integration tests for `/api/v2/user*` and `/api/v2/settings`
///
/// Contract source: the Java `UserResource` / `SettingsResource` (see ADR-2026-10-20-017).
/// Token validation needs a live Keycloak JWKS, so the principal is injected the way the auth
/// middleware does after a successful validation. 401 is covered through the real `build_router`.
use axum::{
    body::Body,
    extract::Request,
    http::StatusCode,
    middleware::{self, Next},
    Router,
};
use chronos_date_api::app::build_router;
use chronos_date_api::appointments::handlers::AppState;
use chronos_date_api::event_bus::postgres::PostgresEventBus;
use chronos_date_api::keycloak::{Credential, KeycloakError, KeycloakUrls};
use chronos_date_api::security::{PrincipalContext, TokenValidator};
use chronos_date_api::test_utils::{FakeKeycloak, TestDb};
use chronos_date_api::users::UserState;
use serde_json::{json, Value};
use std::sync::Arc;
use tower::Service;

const USER_ID: &str = "keycloak-user-1";
const REDIRECT: &str = "https://app.example.org/profile?tab=1";
const ENCODED_REDIRECT: &str = "https%3A%2F%2Fapp.example.org%2Fprofile%3Ftab%3D1";

fn urls() -> KeycloakUrls {
    KeycloakUrls::new("https://auth.test", "chronos", "chronos-api")
}

fn user_state(db: &TestDb, keycloak: Arc<FakeKeycloak>) -> Arc<UserState> {
    Arc::new(UserState {
        db_pool: db.pool().clone(),
        keycloak,
        keycloak_urls: urls(),
    })
}

/// The real router including token validation (for the 401 cases)
fn router(db: &TestDb, keycloak: Arc<FakeKeycloak>) -> Router {
    let app_state = Arc::new(AppState {
        db_pool: db.pool().clone(),
        event_publisher: Arc::new(PostgresEventBus::new(db.pool().clone())),
    });
    build_router(
        app_state,
        user_state(db, keycloak),
        Arc::new(TokenValidator::new(
            "http://127.0.0.1:1/realms/chronos".to_string(),
        )),
    )
}

/// Sends one request to `router` and returns status, parsed JSON body (if any) and raw body
async fn call(
    router: Router,
    method: &str,
    uri: &str,
    body: Option<&str>,
) -> (StatusCode, Option<Value>, String) {
    let mut request = Request::builder().method(method).uri(uri);
    if body.is_some() {
        request = request.header("content-type", "application/json");
    }
    let request = request
        .body(body.map_or_else(Body::empty, |b| Body::from(b.to_string())))
        .unwrap();
    let mut router = router;
    let response = router.call(request).await.expect("router call failed");
    let status = response.status();
    let bytes = axum::body::to_bytes(response.into_body(), usize::MAX)
        .await
        .unwrap();
    let text = String::from_utf8(bytes.to_vec()).unwrap();
    (status, serde_json::from_str(&text).ok(), text)
}

/// Routes of the user module with a fixed principal (token validation replaced)
fn authenticated(db: &TestDb, keycloak: Arc<FakeKeycloak>, principal: PrincipalContext) -> Router {
    chronos_date_api::app::user_routes(user_state(db, keycloak)).layer(middleware::from_fn(
        move |mut request: Request, next: Next| {
            let principal = principal.clone();
            async move {
                request.extensions_mut().insert(Arc::new(principal));
                next.run(request).await
            }
        },
    ))
}

fn max() -> PrincipalContext {
    PrincipalContext::new(USER_ID).with_profile(
        Some("Max".into()),
        Some("Muster".into()),
        Some("max@example.org".into()),
        Some("https://img.example.org/max.png".into()),
    )
}

async fn db() -> TestDb {
    TestDb::new()
        .await
        .expect("Failed to initialize test database")
}

// ---------------------------------------------------------------- authentication (401)

#[tokio::test]
async fn all_user_and_settings_routes_require_a_token() {
    let db = db().await;
    for (method, uri) in [
        ("GET", "/api/v2/user"),
        ("POST", "/api/v2/user"),
        ("PATCH", "/api/v2/user"),
        ("DELETE", "/api/v2/user"),
        ("GET", "/api/v2/user/linked"),
        ("DELETE", "/api/v2/user/linked/github"),
        ("GET", "/api/v2/user/link/github"),
        ("GET", "/api/v2/user/link/passkey"),
        ("GET", "/api/v2/user/unlink/passkey"),
        ("GET", "/api/v2/user/passkeys"),
        ("GET", "/api/v2/user/change-password"),
        ("GET", "/api/v2/settings"),
        ("PUT", "/api/v2/settings"),
    ] {
        let (status, _, _) = call(
            router(&db, Arc::new(FakeKeycloak::new())),
            method,
            uri,
            None,
        )
        .await;
        assert_eq!(status, StatusCode::UNAUTHORIZED, "{method} {uri}");
    }
}

// ---------------------------------------------------------------- GET/POST/DELETE /user

#[tokio::test]
async fn get_and_post_user_return_the_principal_from_the_token() {
    let db = db().await;
    for method in ["GET", "POST"] {
        let app = authenticated(&db, Arc::new(FakeKeycloak::new()), max());
        let (status, body, _) = call(app, method, "/api/v2/user", None).await;
        assert_eq!(status, StatusCode::OK);
        assert_eq!(
            body.unwrap(),
            json!({"id": USER_ID, "first_name": "Max", "last_name": "Muster", "email": "max@example.org"})
        );
    }
}

#[tokio::test]
async fn get_user_serialises_missing_claims_as_null() {
    let db = db().await;
    let app = authenticated(
        &db,
        Arc::new(FakeKeycloak::new()),
        PrincipalContext::new(USER_ID),
    );
    let (_, body, _) = call(app, "GET", "/api/v2/user", None).await;
    assert_eq!(
        body.unwrap(),
        json!({"id": USER_ID, "first_name": null, "last_name": null, "email": null})
    );
}

#[tokio::test]
async fn delete_user_is_an_empty_405_like_java() {
    let db = db().await;
    let keycloak = Arc::new(FakeKeycloak::new());
    let app = authenticated(&db, keycloak.clone(), max());
    let (status, _, text) = call(app, "DELETE", "/api/v2/user", None).await;
    assert_eq!(status, StatusCode::METHOD_NOT_ALLOWED);
    assert_eq!(text, "");
    assert!(keycloak.calls().is_empty(), "nothing may be deleted");
}

// ---------------------------------------------------------------- PATCH /user

#[tokio::test]
async fn patch_user_updates_keycloak_and_echoes_the_request() {
    let db = db().await;
    let keycloak = Arc::new(FakeKeycloak::new().with_email("max@example.org"));
    let app = authenticated(&db, keycloak.clone(), max());
    let (status, body, _) = call(
        app,
        "PATCH",
        &format!("/api/v2/user?redirectUri={ENCODED_REDIRECT}"),
        Some(r#"{"first_name":"Maximilian","last_name":null}"#),
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(
        body.unwrap(),
        json!({
            "user": {"id": USER_ID, "first_name": "Maximilian", "last_name": null, "email": null},
            "verifyEmailUrl": format!("https://auth.test/realms/chronos/protocol/openid-connect/auth?client_id=chronos-api&redirect_uri={ENCODED_REDIRECT}&response_type=code&scope=openid&kc_action=VERIFY_EMAIL")
        })
    );
    assert_eq!(
        keycloak.calls(),
        vec![format!(
            "update_user({USER_ID}, first=Some(\"Maximilian\"), last=None, email=None, verified=None)"
        )]
    );
}

#[tokio::test]
async fn patch_user_with_new_email_sends_the_verification_mail() {
    let db = db().await;
    let keycloak = Arc::new(FakeKeycloak::new().with_email("old@example.org"));
    let app = authenticated(&db, keycloak.clone(), max());
    let (status, _, _) = call(
        app,
        "PATCH",
        &format!("/api/v2/user?redirectUri={ENCODED_REDIRECT}"),
        Some(r#"{"email":"new@example.org"}"#),
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    assert!(keycloak
        .calls()
        .contains(&format!("send_verify_email({USER_ID}, {REDIRECT})")));
}

#[tokio::test]
async fn patch_user_with_new_email_and_no_redirect_uri_is_400() {
    let db = db().await;
    let keycloak = Arc::new(FakeKeycloak::new().with_email("old@example.org"));
    let app = authenticated(&db, keycloak, max());
    let (status, body, _) = call(
        app,
        "PATCH",
        "/api/v2/user",
        Some(r#"{"email":"new@example.org"}"#),
    )
    .await;
    assert_eq!(status, StatusCode::BAD_REQUEST);
    let body = body.unwrap();
    assert_eq!(body["status"], 400);
    assert_eq!(body["error"], "Bad Request");
    assert_eq!(body["errorCode"], "BAD_REQUEST");
    assert_eq!(
        body["message"],
        "Redirect Url must be set to change email address"
    );
    assert_eq!(body["path"], "/api/v2/user");
    assert!(body["timestamp"].is_string());
    assert!(body.get("fieldErrors").is_none());
}

/// Java: `getVerifyEmailUrl(null)` throws a NullPointerException → GenericExceptionMapper (500),
/// after Keycloak was already updated. Documented in ADR-2026-10-20-017.
#[tokio::test]
async fn patch_user_without_redirect_uri_and_unchanged_email_is_500_after_the_update() {
    let db = db().await;
    let keycloak = Arc::new(FakeKeycloak::new());
    let app = authenticated(&db, keycloak.clone(), max());
    let (status, body, _) = call(
        app,
        "PATCH",
        "/api/v2/user",
        Some(r#"{"first_name":"Max"}"#),
    )
    .await;
    assert_eq!(status, StatusCode::INTERNAL_SERVER_ERROR);
    assert_internal_error(&body.unwrap(), "/api/v2/user");
    assert_eq!(keycloak.calls().len(), 1);
}

#[tokio::test]
async fn patch_user_with_an_unreadable_body_is_500() {
    let db = db().await;
    for body in [None, Some("null"), Some("{not json")] {
        let app = authenticated(&db, Arc::new(FakeKeycloak::new()), max());
        let (status, _, _) = call(
            app,
            "PATCH",
            &format!("/api/v2/user?redirectUri={ENCODED_REDIRECT}"),
            body,
        )
        .await;
        assert_eq!(status, StatusCode::INTERNAL_SERVER_ERROR, "{body:?}");
    }
}

// ---------------------------------------------------------------- Keycloak failures

#[tokio::test]
async fn keycloak_404_maps_to_java_not_found_and_other_failures_to_500() {
    let db = db().await;
    let app = authenticated(
        &db,
        Arc::new(FakeKeycloak::new().failing_with(KeycloakError::NotFound)),
        max(),
    );
    let (status, body, _) = call(app, "GET", "/api/v2/user/linked", None).await;
    assert_eq!(status, StatusCode::NOT_FOUND);
    let body = body.unwrap();
    assert_eq!(body["status"], 404);
    assert_eq!(body["error"], "Not Found");
    assert_eq!(body["message"], "HTTP 404 Not Found");
    assert_eq!(body["path"], "/api/v2/user/linked");
    assert!(
        body.get("errorCode").is_none(),
        "NotFoundExceptionMapper sets no errorCode"
    );

    let app = authenticated(
        &db,
        Arc::new(FakeKeycloak::new().failing_with(KeycloakError::Failed("boom".into()))),
        max(),
    );
    let (status, body, _) = call(app, "GET", "/api/v2/user/linked", None).await;
    assert_eq!(status, StatusCode::INTERNAL_SERVER_ERROR);
    assert_eq!(body.unwrap()["errorCode"], "INTERNAL_ERROR");
}

// ---------------------------------------------------------------- linked accounts

#[tokio::test]
async fn linked_accounts_lists_the_identity_providers() {
    let db = db().await;
    let keycloak = Arc::new(
        FakeKeycloak::new()
            .with_identity("github")
            .with_identity("google"),
    );
    let app = authenticated(&db, keycloak, max());
    let (status, body, _) = call(app, "GET", "/api/v2/user/linked", None).await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(
        body.unwrap(),
        json!([{"provider": "github"}, {"provider": "google"}])
    );
}

#[tokio::test]
async fn unlink_account_is_204_and_calls_keycloak() {
    let db = db().await;
    let keycloak = Arc::new(FakeKeycloak::new());
    let app = authenticated(&db, keycloak.clone(), max());
    let (status, _, text) = call(app, "DELETE", "/api/v2/user/linked/github", None).await;
    assert_eq!(status, StatusCode::NO_CONTENT);
    assert_eq!(text, "");
    assert_eq!(
        keycloak.calls(),
        vec![format!("remove_federated_identity({USER_ID}, github)")]
    );
}

// ---------------------------------------------------------------- URL endpoints

fn auth_url(redirect: &str, action: &str) -> String {
    format!("https://auth.test/realms/chronos/protocol/openid-connect/auth?client_id=chronos-api&redirect_uri={redirect}&response_type=code&scope=openid&kc_action={action}")
}

#[tokio::test]
async fn url_endpoints_build_the_keycloak_action_urls() {
    let db = db().await;
    let q = format!("redirectUri={ENCODED_REDIRECT}");
    for (uri, action) in [
        (format!("/api/v2/user/link/github?{q}"), "idp_link:github"),
        (
            format!("/api/v2/user/link/passkey?{q}"),
            "webauthn-register-passwordless",
        ),
        (
            format!("/api/v2/user/unlink/passkey?{q}&passkeyId=pk-1"),
            "delete_credential:pk-1",
        ),
        (
            format!("/api/v2/user/change-password?{q}"),
            "UPDATE_PASSWORD",
        ),
    ] {
        let app = authenticated(&db, Arc::new(FakeKeycloak::new()), max());
        let (status, body, _) = call(app, "GET", &uri, None).await;
        assert_eq!(status, StatusCode::OK, "{uri}");
        assert_eq!(
            body.unwrap(),
            json!({"url": auth_url(ENCODED_REDIRECT, action)}),
            "{uri}"
        );
    }
}

#[tokio::test]
async fn unlink_passkey_without_passkey_id_uses_the_literal_null_like_java() {
    let db = db().await;
    let app = authenticated(&db, Arc::new(FakeKeycloak::new()), max());
    let (_, body, _) = call(
        app,
        "GET",
        &format!("/api/v2/user/unlink/passkey?redirectUri={ENCODED_REDIRECT}"),
        None,
    )
    .await;
    assert_eq!(
        body.unwrap(),
        json!({"url": auth_url(ENCODED_REDIRECT, "delete_credential:null")})
    );
}

/// Java: `URLEncoder.encode(null)` → NullPointerException → GenericExceptionMapper (500)
#[tokio::test]
async fn url_endpoints_without_redirect_uri_are_500() {
    let db = db().await;
    for uri in [
        "/api/v2/user/link/github",
        "/api/v2/user/link/passkey",
        "/api/v2/user/unlink/passkey",
        "/api/v2/user/change-password",
    ] {
        let app = authenticated(&db, Arc::new(FakeKeycloak::new()), max());
        let (status, body, _) = call(app, "GET", uri, None).await;
        assert_eq!(status, StatusCode::INTERNAL_SERVER_ERROR, "{uri}");
        assert_internal_error(&body.unwrap(), uri);
    }
}

/// The Java `GenericExceptionMapper` body (the timestamp differs per call)
fn assert_internal_error(body: &Value, path: &str) {
    assert_eq!(body["status"], 500);
    assert_eq!(body["error"], "Internal Server Error");
    assert_eq!(body["errorCode"], "INTERNAL_ERROR");
    assert_eq!(body["message"], "Ein interner Fehler ist aufgetreten");
    assert_eq!(body["path"], path);
    assert!(body["timestamp"].is_string());
}

// ---------------------------------------------------------------- passkeys

#[tokio::test]
async fn passkeys_lists_only_webauthn_passwordless_credentials() {
    let db = db().await;
    let keycloak = Arc::new(
        FakeKeycloak::new()
            .with_credential(Credential {
                id: "pw".into(),
                credential_type: "password".into(),
                user_label: None,
                created_date: Some(1),
            })
            .with_credential(Credential {
                id: "pk-1".into(),
                credential_type: "webauthn-passwordless".into(),
                user_label: Some("Laptop".into()),
                created_date: Some(1_700_000_000_000),
            }),
    );
    let app = authenticated(&db, keycloak, max());
    let (status, body, _) = call(app, "GET", "/api/v2/user/passkeys", None).await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(
        body.unwrap(),
        json!([{"id": "pk-1", "name": "Laptop", "created_at": "2023-11-14T22:13:20Z", "last_used_at": null}])
    );
}

// ---------------------------------------------------------------- settings

fn defaults() -> Value {
    json!({
        "appointment_moved": "ALL",
        "appointment_message": "ATTENDANT",
        "appointment_cancelled": "ALL",
        "appointment_participant_added": "RESPONSIBLE",
        "appointment_participation_status_changed": "RESPONSIBLE",
        "appointment_participation_invalid": "ATTENDANT",
        "appointment_participation_status_pending": "ATTENDANT",
        "appointment_reminder": "ALL",
        "group_member_added": "DISABLED"
    })
}

fn custom() -> Value {
    json!({
        "appointment_moved": "disabled",
        "appointment_message": " HELPER ",
        "appointment_cancelled": "RESPONSIBLE",
        "appointment_participant_added": "ALL",
        "appointment_participation_status_changed": "ATTENDANT",
        "appointment_participation_invalid": "ALL",
        "appointment_participation_status_pending": "DISABLED",
        "appointment_reminder": "HELPER",
        "group_member_added": "enabled"
    })
}

#[tokio::test]
async fn get_settings_creates_the_flyway_defaults() {
    let db = db().await;
    let app = authenticated(&db, Arc::new(FakeKeycloak::new()), max());
    let (status, body, _) = call(app.clone(), "GET", "/api/v2/settings", None).await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(body.unwrap(), defaults());

    let rows: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM settings WHERE user_oidcid = $1")
        .bind(USER_ID)
        .fetch_one(db.pool())
        .await
        .unwrap();
    assert_eq!(rows, 1);
    let (_, again, _) = call(app, "GET", "/api/v2/settings", None).await;
    assert_eq!(again.unwrap(), defaults());
    let rows: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM settings WHERE user_oidcid = $1")
        .bind(USER_ID)
        .fetch_one(db.pool())
        .await
        .unwrap();
    assert_eq!(rows, 1, "a second read must not create a second row");
}

#[tokio::test]
async fn put_settings_normalises_values_and_get_returns_them() {
    let db = db().await;
    let app = authenticated(&db, Arc::new(FakeKeycloak::new()), max());
    let (status, _, text) = call(
        app.clone(),
        "PUT",
        "/api/v2/settings",
        Some(&custom().to_string()),
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(text, "");

    let (_, body, _) = call(app, "GET", "/api/v2/settings", None).await;
    assert_eq!(
        body.unwrap(),
        json!({
            "appointment_moved": "DISABLED",
            "appointment_message": "HELPER",
            "appointment_cancelled": "RESPONSIBLE",
            "appointment_participant_added": "ALL",
            "appointment_participation_status_changed": "ATTENDANT",
            "appointment_participation_invalid": "ALL",
            "appointment_participation_status_pending": "DISABLED",
            "appointment_reminder": "HELPER",
            "group_member_added": "ENABLED"
        })
    );
}

#[tokio::test]
async fn put_settings_creates_the_row_when_missing_and_keeps_users_apart() {
    let db = db().await;
    let app = authenticated(&db, Arc::new(FakeKeycloak::new()), max());
    call(app, "PUT", "/api/v2/settings", Some(&custom().to_string())).await;

    let other = authenticated(
        &db,
        Arc::new(FakeKeycloak::new()),
        PrincipalContext::new("someone-else"),
    );
    let (_, body, _) = call(other, "GET", "/api/v2/settings", None).await;
    assert_eq!(body.unwrap(), defaults());
}

#[tokio::test]
async fn put_settings_with_missing_blank_or_unknown_values_is_500_and_changes_nothing() {
    let db = db().await;
    let app = authenticated(&db, Arc::new(FakeKeycloak::new()), max());
    call(
        app.clone(),
        "PUT",
        "/api/v2/settings",
        Some(&custom().to_string()),
    )
    .await;

    let mut missing = custom();
    missing
        .as_object_mut()
        .unwrap()
        .remove("appointment_reminder");
    let mut blank = custom();
    blank["appointment_reminder"] = json!("  ");
    let mut unknown = custom();
    unknown["appointment_reminder"] = json!("SOMETIMES");
    let mut wrong_enum = custom();
    wrong_enum["group_member_added"] = json!("ALL");
    for body in [missing, blank, unknown, wrong_enum, json!(null)] {
        let (status, response, _) = call(
            app.clone(),
            "PUT",
            "/api/v2/settings",
            Some(&body.to_string()),
        )
        .await;
        assert_eq!(status, StatusCode::INTERNAL_SERVER_ERROR, "{body}");
        assert_eq!(response.unwrap()["errorCode"], "INTERNAL_ERROR");
    }

    let (_, body, _) = call(app, "GET", "/api/v2/settings", None).await;
    assert_eq!(body.unwrap()["appointment_reminder"], "HELPER");
}

// ---------------------------------------------------------------- identity cache

#[tokio::test]
async fn authenticated_requests_refresh_the_cached_profile() {
    let db = db().await;
    let user_state = user_state(&db, Arc::new(FakeKeycloak::new()));
    // Same layer order as build_router: cache_identity runs inside authentication
    let app = Router::new()
        .merge(chronos_date_api::app::user_routes(user_state.clone()))
        .layer(middleware::from_fn_with_state(
            chronos_date_api::users::repository::UserRepository::new(db.pool().clone()),
            chronos_date_api::users::identity::cache_identity,
        ))
        .layer(middleware::from_fn(
            |mut request: Request, next: Next| async move {
                request.extensions_mut().insert(Arc::new(max()));
                next.run(request).await
            },
        ));

    let (status, _, _) = call(app, "GET", "/api/v2/user", None).await;
    assert_eq!(status, StatusCode::OK);

    let row: (Option<String>, Option<String>, Option<String>, Option<String>) = sqlx::query_as(
        "SELECT first_name, last_name, email, profile_picture_url FROM user_profiles WHERE oidc_id = $1",
    )
    .bind(USER_ID)
    .fetch_one(db.pool())
    .await
    .unwrap();
    assert_eq!(
        row,
        (
            Some("Max".into()),
            Some("Muster".into()),
            Some("max@example.org".into()),
            Some("https://img.example.org/max.png".into())
        )
    );
}
