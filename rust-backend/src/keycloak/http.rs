//! HTTP implementation of [`KeycloakAdmin`] using the client-credentials grant of the confidential client.

use async_trait::async_trait;
use reqwest::{Method, RequestBuilder, Response, StatusCode};
use serde::Deserialize;
use serde_json::json;
use std::time::{Duration, Instant};
use tokio::sync::Mutex;

use super::{
    Credential, FederatedIdentity, KeycloakAdmin, KeycloakConfig, KeycloakError, KeycloakUser,
    UserUpdate, VERIFY_EMAIL_LIFESPAN_SECONDS,
};

/// Renew the cached access token this long before it expires
const TOKEN_EXPIRY_MARGIN: Duration = Duration::from_secs(30);

struct CachedToken {
    access_token: String,
    valid_until: Instant,
}

#[derive(Deserialize)]
struct TokenResponse {
    access_token: String,
    expires_in: u64,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct UserRepresentation {
    id: Option<String>,
    first_name: Option<String>,
    last_name: Option<String>,
    email: Option<String>,
    created_timestamp: Option<i64>,
    attributes: Option<std::collections::HashMap<String, Vec<String>>>,
}

impl From<UserRepresentation> for KeycloakUser {
    fn from(rep: UserRepresentation) -> Self {
        Self {
            id: rep.id,
            first_name: rep.first_name,
            last_name: rep.last_name,
            email: rep.email,
            created_timestamp: rep.created_timestamp,
            picture: rep
                .attributes
                .and_then(|mut attributes| attributes.remove("picture"))
                .and_then(|values| values.into_iter().next()),
        }
    }
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct FederatedIdentityRepresentation {
    identity_provider: String,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct CredentialRepresentation {
    id: String,
    #[serde(rename = "type")]
    credential_type: String,
    user_label: Option<String>,
    created_date: Option<i64>,
}

/// Keycloak admin REST client
pub struct HttpKeycloakAdmin {
    config: KeycloakConfig,
    client: reqwest::Client,
    token: Mutex<Option<CachedToken>>,
}

impl HttpKeycloakAdmin {
    pub fn new(config: KeycloakConfig) -> Self {
        Self {
            config,
            client: reqwest::Client::new(),
            token: Mutex::new(None),
        }
    }

    fn token_url(&self) -> String {
        format!(
            "{}/realms/{}/protocol/openid-connect/token",
            self.config.base_url, self.config.realm
        )
    }

    /// `{base}/admin/realms/{realm}/users/{segments...}` with every segment percent-encoded
    fn users_url(&self, segments: &[&str]) -> Result<url::Url, KeycloakError> {
        let mut url = url::Url::parse(&self.config.base_url)
            .map_err(|e| KeycloakError::Failed(format!("invalid AUTH_SERVER_BASE_URL: {}", e)))?;
        url.path_segments_mut()
            .map_err(|_| KeycloakError::Failed("AUTH_SERVER_BASE_URL cannot be a base".into()))?
            .pop_if_empty()
            .extend(["admin", "realms", self.config.realm.as_str(), "users"])
            .extend(segments);
        Ok(url)
    }

    /// `{base}/admin/realms/{realm}/users/{id}/{segments...}`
    fn user_url(&self, oidc_id: &str, segments: &[&str]) -> Result<url::Url, KeycloakError> {
        let mut all = vec![oidc_id];
        all.extend_from_slice(segments);
        self.users_url(&all)
    }

    async fn access_token(&self) -> Result<String, KeycloakError> {
        let mut cached = self.token.lock().await;
        if let Some(token) = cached.as_ref() {
            if token.valid_until > Instant::now() {
                return Ok(token.access_token.clone());
            }
        }
        let response = self
            .client
            .post(self.token_url())
            .form(&[
                ("grant_type", "client_credentials"),
                ("client_id", self.config.client_id.as_str()),
                ("client_secret", self.config.client_secret.as_str()),
            ])
            .send()
            .await
            .map_err(|e| KeycloakError::Failed(e.to_string()))?;
        let token: TokenResponse = Self::check(response)?
            .json()
            .await
            .map_err(|e| KeycloakError::Failed(e.to_string()))?;
        let lifetime = Duration::from_secs(token.expires_in).saturating_sub(TOKEN_EXPIRY_MARGIN);
        *cached = Some(CachedToken {
            access_token: token.access_token.clone(),
            valid_until: Instant::now() + lifetime,
        });
        Ok(token.access_token)
    }

    fn check(response: Response) -> Result<Response, KeycloakError> {
        match response.status() {
            status if status.is_success() => Ok(response),
            StatusCode::NOT_FOUND => Err(KeycloakError::NotFound),
            status => Err(KeycloakError::Failed(format!("HTTP {}", status))),
        }
    }

    async fn send(&self, request: RequestBuilder) -> Result<Response, KeycloakError> {
        let token = self.access_token().await?;
        let response = request
            .bearer_auth(token)
            .send()
            .await
            .map_err(|e| KeycloakError::Failed(e.to_string()))?;
        Self::check(response)
    }

    async fn send_json<T: for<'de> Deserialize<'de>>(
        &self,
        request: RequestBuilder,
    ) -> Result<T, KeycloakError> {
        self.send(request)
            .await?
            .json()
            .await
            .map_err(|e| KeycloakError::Failed(e.to_string()))
    }

    fn request(&self, method: Method, url: url::Url) -> RequestBuilder {
        self.client.request(method, url)
    }
}

#[async_trait]
impl KeycloakAdmin for HttpKeycloakAdmin {
    async fn get_user(&self, oidc_id: &str) -> Result<KeycloakUser, KeycloakError> {
        let rep: UserRepresentation = self
            .send_json(self.request(Method::GET, self.user_url(oidc_id, &[])?))
            .await?;
        Ok(rep.into())
    }

    async fn update_user(&self, oidc_id: &str, update: &UserUpdate) -> Result<(), KeycloakError> {
        let mut body = serde_json::Map::new();
        if let Some(first_name) = &update.first_name {
            body.insert("firstName".into(), json!(first_name));
        }
        if let Some(last_name) = &update.last_name {
            body.insert("lastName".into(), json!(last_name));
        }
        if let Some(email) = &update.email {
            body.insert("email".into(), json!(email));
        }
        if let Some(verified) = update.email_verified {
            body.insert("emailVerified".into(), json!(verified));
        }
        self.send(
            self.request(Method::PUT, self.user_url(oidc_id, &[])?)
                .json(&body),
        )
        .await?;
        Ok(())
    }

    async fn send_verify_email(
        &self,
        oidc_id: &str,
        redirect_uri: &str,
    ) -> Result<(), KeycloakError> {
        let lifespan = VERIFY_EMAIL_LIFESPAN_SECONDS.to_string();
        self.send(
            self.request(Method::PUT, self.user_url(oidc_id, &["send-verify-email"])?)
                .query(&[
                    ("client_id", self.config.client_id.as_str()),
                    ("redirect_uri", redirect_uri),
                    ("lifespan", lifespan.as_str()),
                ]),
        )
        .await?;
        Ok(())
    }

    async fn federated_identities(
        &self,
        oidc_id: &str,
    ) -> Result<Vec<FederatedIdentity>, KeycloakError> {
        let reps: Vec<FederatedIdentityRepresentation> = self
            .send_json(self.request(
                Method::GET,
                self.user_url(oidc_id, &["federated-identity"])?,
            ))
            .await?;
        Ok(reps
            .into_iter()
            .map(|r| FederatedIdentity {
                identity_provider: r.identity_provider,
            })
            .collect())
    }

    async fn remove_federated_identity(
        &self,
        oidc_id: &str,
        provider_id: &str,
    ) -> Result<(), KeycloakError> {
        self.send(self.request(
            Method::DELETE,
            self.user_url(oidc_id, &["federated-identity", provider_id])?,
        ))
        .await?;
        Ok(())
    }

    async fn credentials(&self, oidc_id: &str) -> Result<Vec<Credential>, KeycloakError> {
        let reps: Vec<CredentialRepresentation> = self
            .send_json(self.request(Method::GET, self.user_url(oidc_id, &["credentials"])?))
            .await?;
        Ok(reps
            .into_iter()
            .map(|r| Credential {
                id: r.id,
                credential_type: r.credential_type,
                user_label: r.user_label,
                created_date: r.created_date,
            })
            .collect())
    }

    async fn delete_user(&self, oidc_id: &str) -> Result<(), KeycloakError> {
        self.send(self.request(Method::DELETE, self.user_url(oidc_id, &[])?))
            .await?;
        Ok(())
    }

    async fn list_users(&self, first: i64, max: i64) -> Result<Vec<KeycloakUser>, KeycloakError> {
        let reps: Vec<UserRepresentation> = self
            .send_json(
                self.request(Method::GET, self.users_url(&[])?)
                    .query(&[("first", first), ("max", max)]),
            )
            .await?;
        Ok(reps.into_iter().map(Into::into).collect())
    }

    async fn search_users(
        &self,
        search: &str,
        max: i64,
    ) -> Result<Vec<KeycloakUser>, KeycloakError> {
        let reps: Vec<UserRepresentation> = self
            .send_json(
                self.request(Method::GET, self.users_url(&[])?)
                    .query(&[("search", search)])
                    .query(&[("first", 0), ("max", max)]),
            )
            .await?;
        Ok(reps.into_iter().map(Into::into).collect())
    }

    async fn count_users(&self) -> Result<i64, KeycloakError> {
        self.send_json(self.request(Method::GET, self.users_url(&["count"])?))
            .await
    }
}
