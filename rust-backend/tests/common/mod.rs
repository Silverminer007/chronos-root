//! Shared helpers for the endpoint tests that go through the production router
//! (`build_router`, auth middleware included) against a PostgreSQL test database.
#![allow(dead_code)]

use axum::{body::Body, http::StatusCode, routing::get, Json, Router};
use chronos_date_api::app::build_router;
use chronos_date_api::appointments::handlers::AppState;
use chronos_date_api::event_bus::postgres::PostgresEventBus;
use chronos_date_api::keycloak::KeycloakUrls;
use chronos_date_api::push_notifications::{NotificationDispatcher, PushSender, PushState};
use chronos_date_api::security::TokenValidator;
use chronos_date_api::test_utils::{FakeKeycloak, TestDb};
use chronos_date_api::users::UserState;
use jsonwebtoken::{encode, Algorithm, EncodingKey, Header};
use serde_json::Value;
use std::sync::Arc;
use tower::Service;

/// Test-only RSA key pair. The public half is served from a local mock JWKS endpoint.
const TEST_PRIVATE_KEY_PEM: &str = include_str!("../fixtures/rs256_test_key.pem");
const TEST_JWK_KID: &str = "endpoint-test";
const TEST_JWK_MODULUS: &str = "915TXanmgE10B-CUnu4gKx24VaNysPwL0PTTu59SiJ9PEwRL2NHdx44BC_G0_vACwXOtrFhE6oLHpezfZnNb2NwM8Jcxh1XWNRRjuzBwEWtfcbb27U0cHnjwSu7gMoWVAuUq0Uj1j2vI1GBdFGJSoZpdFrIwiuvpo5hu5kWJYbu-kYKFxzdPmaQoVTGryxecwZCndLSOoY2Lal36BvFtnknGj_i32P1ll4lND7l3yYpdjGOuEXfrfUX9PkPfCHmK2_vAFnA1b54uW0xoDIfZbP6cS0BIaEZsq-0xqOIMdr1Jj3l-tSlhtJxairnmH02eujLRA1abGtTUc4jI7-p8sw";
const TEST_JWK_EXPONENT: &str = "AQAB";

/// The production router on a fresh test database, authenticated through a mock Keycloak
pub struct TestApp {
    pub db: TestDb,
    router: Router,
    /// Profile claims per user id; the auth middleware caches them into `user_profiles` on every request
    profiles: std::sync::Mutex<std::collections::HashMap<String, Value>>,
}

impl TestApp {
    pub async fn new() -> Self {
        Self::with_collaborators(FakeKeycloak::new(), None).await
    }

    /// Like [`TestApp::new`] with scripted Keycloak answers and a fake push service
    pub async fn with_collaborators(
        keycloak: FakeKeycloak,
        push_sender: Option<Arc<dyn PushSender>>,
    ) -> Self {
        let db = TestDb::new()
            .await
            .expect("Failed to initialize test database");
        let state = Arc::new(AppState {
            db_pool: db.pool().clone(),
            event_publisher: Arc::new(PostgresEventBus::new(db.pool().clone())),
        });
        let user_state = Arc::new(UserState {
            db_pool: db.pool().clone(),
            keycloak: Arc::new(keycloak),
            keycloak_urls: KeycloakUrls::new("https://auth.test", "chronos", "chronos-api"),
        });
        let validator = Arc::new(TokenValidator::new(start_mock_keycloak().await));
        Self {
            router: build_router(
                state,
                user_state,
                Arc::new(
                    PushState::new(db.pool().clone(), String::new()).with_dispatcher(Arc::new(
                        NotificationDispatcher::new(db.pool().clone(), push_sender),
                    )),
                ),
                validator,
            ),
            db,
            profiles: Default::default(),
        }
    }

    fn token(&self, user_id: &str) -> String {
        let profile = self.profiles.lock().unwrap().get(user_id).cloned();
        sign_token(user_id, profile)
    }

    /// Sends a request as `user_id` (`None` = no token); returns status and JSON body (`Null` if empty)
    pub async fn call(
        &self,
        user_id: Option<&str>,
        method: &str,
        uri: &str,
        body: Option<Value>,
    ) -> (StatusCode, Value) {
        let (status, _, body) = self.call_typed(user_id, method, uri, body).await;
        (status, body)
    }

    /// Like [`Self::call`], but also returns the `Content-Type` header of the answer
    pub async fn call_typed(
        &self,
        user_id: Option<&str>,
        method: &str,
        uri: &str,
        body: Option<Value>,
    ) -> (StatusCode, Option<String>, Value) {
        let mut builder = http::Request::builder().uri(uri).method(method);
        if let Some(user_id) = user_id {
            builder = builder.header("authorization", format!("Bearer {}", self.token(user_id)));
        }
        let request = match body {
            Some(body) => builder
                .header("content-type", "application/json")
                .body(Body::from(body.to_string())),
            None => builder.body(Body::empty()),
        }
        .expect("Failed to build request");

        let response = self
            .router
            .clone()
            .call(request)
            .await
            .expect("Router call failed");
        let status = response.status();
        let content_type = response
            .headers()
            .get("content-type")
            .map(|v| v.to_str().unwrap().to_string());
        let bytes = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .expect("Failed to read body");
        let body = if bytes.is_empty() {
            Value::Null
        } else {
            // Plain-text answers (the appointment endpoints) come back as a JSON string
            serde_json::from_slice(&bytes)
                .unwrap_or_else(|_| Value::String(String::from_utf8_lossy(&bytes).into_owned()))
        };
        (status, content_type, body)
    }

    /// Sends a GET as `user_id`; returns status and the raw body (for non-JSON responses)
    pub async fn get_raw(&self, user_id: &str, uri: &str) -> (StatusCode, String) {
        let request = http::Request::builder()
            .uri(uri)
            .method("GET")
            .header("authorization", format!("Bearer {}", self.token(user_id)))
            .body(Body::empty())
            .expect("Failed to build request");
        let response = self
            .router
            .clone()
            .call(request)
            .await
            .expect("Router call failed");
        let status = response.status();
        let bytes = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .expect("Failed to read body");
        (status, String::from_utf8_lossy(&bytes).into_owned())
    }

    pub async fn get(&self, user: &str, uri: &str) -> (StatusCode, Value) {
        self.call(Some(user), "GET", uri, None).await
    }

    pub async fn post(&self, user: &str, uri: &str, body: Option<Value>) -> (StatusCode, Value) {
        self.call(Some(user), "POST", uri, body).await
    }

    pub async fn patch(&self, user: &str, uri: &str, body: Option<Value>) -> (StatusCode, Value) {
        self.call(Some(user), "PATCH", uri, body).await
    }

    pub async fn delete(&self, user: &str, uri: &str) -> (StatusCode, Value) {
        self.call(Some(user), "DELETE", uri, None).await
    }

    /// Creates a `user_profiles` row
    pub async fn add_user(&self, oidc_id: &str, first: &str, last: &str, email: &str) {
        self.profiles.lock().unwrap().insert(
            oidc_id.to_string(),
            serde_json::json!({ "given_name": first, "family_name": last, "email": email }),
        );
        sqlx::query(
            "INSERT INTO user_profiles (oidc_id, first_name, last_name, email) VALUES ($1, $2, $3, $4)",
        )
        .bind(oidc_id)
        .bind(first)
        .bind(last)
        .bind(email)
        .execute(self.db.pool())
        .await
        .expect("Failed to insert user profile");
    }

    /// Gives `user_id` the realm role `ADMIN_API` in its access tokens
    pub fn grant_admin(&self, user_id: &str) {
        let mut profiles = self.profiles.lock().unwrap();
        let profile = profiles
            .entry(user_id.to_string())
            .or_insert_with(|| serde_json::json!({}));
        profile["realm_access"] = serde_json::json!({ "roles": ["offline_access", "ADMIN_API"] });
    }

    /// Creates a group owned by `owner`; the owner is also a member (as the Java backend does on creation)
    pub async fn add_group(&self, owner: &str, name: &str) -> i64 {
        let id = sqlx::query_scalar::<_, i64>(
            "INSERT INTO groups (id, groupname, owner_oidcid) VALUES (nextval('groups_seq'), $1, $2) RETURNING id",
        )
        .bind(name)
        .bind(owner)
        .fetch_one(self.db.pool())
        .await
        .expect("Failed to insert group");
        self.add_member(id, owner).await;
        id
    }

    pub async fn add_member(&self, group_id: i64, user: &str) {
        sqlx::query(
            "INSERT INTO group_member (id, group_id, user_oidcid) VALUES (nextval('group_member_seq'), $1, $2)",
        )
        .bind(group_id)
        .bind(user)
        .execute(self.db.pool())
        .await
        .expect("Failed to insert group member");
    }

    pub async fn members_of(&self, group_id: i64) -> Vec<String> {
        sqlx::query_scalar::<_, String>(
            "SELECT user_oidcid FROM group_member WHERE group_id = $1 ORDER BY id",
        )
        .bind(group_id)
        .fetch_all(self.db.pool())
        .await
        .expect("Failed to read members")
    }

    /// Inserts a friendship request row with the given status; returns its id
    pub async fn add_friendship(&self, requester: &str, addressee: &str, status: &str) -> i64 {
        sqlx::query_scalar::<_, i64>(
            "INSERT INTO friendship_requests (id, created_at, responded_at, status, requester_oidcid, addressee_oidcid)
             VALUES (nextval('friendship_requests_seq'), NOW(), CASE WHEN $1 = 'PENDING' THEN NULL ELSE NOW() END, $1, $2, $3)
             RETURNING id",
        )
        .bind(status)
        .bind(requester)
        .bind(addressee)
        .fetch_one(self.db.pool())
        .await
        .expect("Failed to insert friendship")
    }

    pub async fn friendship_status(&self, id: i64) -> Option<String> {
        sqlx::query_scalar::<_, String>("SELECT status FROM friendship_requests WHERE id = $1")
            .bind(id)
            .fetch_optional(self.db.pool())
            .await
            .expect("Failed to read friendship")
    }

    /// Payloads of all fired events of `event_type`
    pub async fn events(&self, event_type: &str) -> Vec<Value> {
        sqlx::query_scalar::<_, String>(
            "SELECT payload FROM events WHERE event_type = $1 ORDER BY timestamp, created_at",
        )
        .bind(event_type)
        .fetch_all(self.db.pool())
        .await
        .expect("Failed to read events")
        .iter()
        .map(|p| serde_json::from_str(p).expect("Event payload is not JSON"))
        .collect()
    }
}

/// Starts a local mock Keycloak JWKS endpoint and returns the realm URL the validator should use.
async fn start_mock_keycloak() -> String {
    let jwks = serde_json::json!({
        "keys": [{
            "kid": TEST_JWK_KID,
            "kty": "RSA",
            "n": TEST_JWK_MODULUS,
            "e": TEST_JWK_EXPONENT,
        }]
    });
    let app = Router::new().route(
        "/realms/chronos/protocol/openid-connect/certs",
        get(move || {
            let jwks = jwks.clone();
            async move { Json(jwks) }
        }),
    );
    let listener = tokio::net::TcpListener::bind("127.0.0.1:0")
        .await
        .expect("Failed to bind mock Keycloak");
    let addr = listener
        .local_addr()
        .expect("Failed to read mock Keycloak address");
    tokio::spawn(async move {
        axum::serve(listener, app)
            .await
            .expect("Mock Keycloak server failed")
    });
    format!("http://{}/realms/chronos", addr)
}

/// Signs an RS256 access token for `user_id` that the mock JWKS endpoint can verify
fn sign_token(user_id: &str, profile: Option<Value>) -> String {
    let now = chrono::Utc::now().timestamp();
    let mut claims = serde_json::json!({ "sub": user_id, "iat": now, "exp": now + 3600 });
    if let (Some(Value::Object(extra)), Some(map)) = (profile, claims.as_object_mut()) {
        map.extend(extra);
    }
    let mut header = Header::new(Algorithm::RS256);
    header.kid = Some(TEST_JWK_KID.to_string());
    let key = EncodingKey::from_rsa_pem(TEST_PRIVATE_KEY_PEM.as_bytes())
        .expect("Invalid test private key");
    encode(&header, &claims, &key).expect("Failed to sign test token")
}
