//! Keycloak access tokens of users with default roles carry `"aud": "account"` (#174).
//! Java (Quarkus OIDC) accepts them; the Rust validator must too, unless an audience is configured.

use axum::{routing::get, Json, Router};
use chronos_date_api::security::token::{TokenError, TokenValidator};
use jsonwebtoken::{encode, Algorithm, EncodingKey, Header};
use serde_json::{json, Value};

const TEST_PRIVATE_KEY_PEM: &str = include_str!("fixtures/rs256_test_key.pem");
const TEST_JWK_KID: &str = "audience-test";
const TEST_JWK_MODULUS: &str = "915TXanmgE10B-CUnu4gKx24VaNysPwL0PTTu59SiJ9PEwRL2NHdx44BC_G0_vACwXOtrFhE6oLHpezfZnNb2NwM8Jcxh1XWNRRjuzBwEWtfcbb27U0cHnjwSu7gMoWVAuUq0Uj1j2vI1GBdFGJSoZpdFrIwiuvpo5hu5kWJYbu-kYKFxzdPmaQoVTGryxecwZCndLSOoY2Lal36BvFtnknGj_i32P1ll4lND7l3yYpdjGOuEXfrfUX9PkPfCHmK2_vAFnA1b54uW0xoDIfZbP6cS0BIaEZsq-0xqOIMdr1Jj3l-tSlhtJxairnmH02eujLRA1abGtTUc4jI7-p8sw";
const TEST_JWK_EXPONENT: &str = "AQAB";

async fn start_mock_keycloak() -> String {
    let jwks = json!({"keys": [{
        "kid": TEST_JWK_KID, "kty": "RSA", "n": TEST_JWK_MODULUS, "e": TEST_JWK_EXPONENT,
    }]});
    let app = Router::new().route(
        "/realms/chronos/protocol/openid-connect/certs",
        get(move || {
            let jwks = jwks.clone();
            async move { Json(jwks) }
        }),
    );
    let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
    let addr = listener.local_addr().unwrap();
    tokio::spawn(async move { axum::serve(listener, app).await.unwrap() });
    format!("http://{}/realms/chronos", addr)
}

fn sign_token(aud: Option<Value>) -> String {
    let now = chrono::Utc::now().timestamp();
    let mut claims = json!({ "sub": "user-1", "iat": now, "exp": now + 3600 });
    if let Some(aud) = aud {
        claims["aud"] = aud;
    }
    let mut header = Header::new(Algorithm::RS256);
    header.kid = Some(TEST_JWK_KID.to_string());
    let key = EncodingKey::from_rsa_pem(TEST_PRIVATE_KEY_PEM.as_bytes()).unwrap();
    encode(&header, &claims, &key).unwrap()
}

#[tokio::test]
async fn token_without_aud_is_accepted() {
    let validator = TokenValidator::new(start_mock_keycloak().await);
    let claims = validator.validate_token(&sign_token(None)).await.unwrap();
    assert_eq!(claims.sub, "user-1");
}

#[tokio::test]
async fn token_with_aud_account_is_accepted_by_default() {
    let validator = TokenValidator::new(start_mock_keycloak().await);
    let claims = validator
        .validate_token(&sign_token(Some(json!("account"))))
        .await
        .unwrap();
    assert_eq!(claims.sub, "user-1");
}

#[tokio::test]
async fn token_with_aud_array_is_accepted_by_default() {
    let validator = TokenValidator::new(start_mock_keycloak().await);
    let token = sign_token(Some(json!(["account", "chronos-backend"])));
    assert!(validator.validate_token(&token).await.is_ok());
}

#[tokio::test]
async fn configured_audience_must_be_contained_in_aud() {
    let url = start_mock_keycloak().await;
    let validator = TokenValidator::new(url).with_audience(Some("chronos-backend".to_string()));

    let ok = sign_token(Some(json!(["account", "chronos-backend"])));
    assert!(validator.validate_token(&ok).await.is_ok());

    let wrong = sign_token(Some(json!("account")));
    assert!(matches!(
        validator.validate_token(&wrong).await,
        Err(TokenError::InvalidToken(_))
    ));
}
