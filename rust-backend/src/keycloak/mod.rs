//! Keycloak anti-corruption layer (ADR-2026-10-20-017).
//!
//! This is the only module that knows the Keycloak admin API and its URL scheme. The rest of the
//! codebase depends on the [`KeycloakAdmin`] trait and on [`KeycloakUrls`]. The Java reference is
//! `KeycloakProfileService` (admin client with the confidential client's client-credentials grant).

mod http;
mod urls;

pub use http::HttpKeycloakAdmin;
pub use urls::{java_url_encode, KeycloakUrls};

use async_trait::async_trait;

/// Credential type of passkeys in Keycloak (Java: `"webauthn-passwordless".equals(c.getType())`)
pub const PASSKEY_CREDENTIAL_TYPE: &str = "webauthn-passwordless";

/// Lifespan of the verify-email link in seconds (Java: `60 * 60`)
pub const VERIFY_EMAIL_LIFESPAN_SECONDS: u32 = 60 * 60;

const DEFAULT_REALM: &str = "master";

/// Failure of a Keycloak admin call
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum KeycloakError {
    /// Keycloak answered 404 (e.g. user missing). The API still answers 500 (ticket answer 5)
    NotFound,
    /// Any other failure (other status, network, malformed answer)
    Failed(String),
}

impl std::fmt::Display for KeycloakError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            KeycloakError::NotFound => write!(f, "Keycloak resource not found"),
            KeycloakError::Failed(msg) => write!(f, "Keycloak call failed: {}", msg),
        }
    }
}

impl std::error::Error for KeycloakError {}

/// The part of a Keycloak user representation the API needs
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct KeycloakUser {
    pub id: Option<String>,
    pub first_name: Option<String>,
    pub last_name: Option<String>,
    pub email: Option<String>,
    /// Creation time in epoch milliseconds (`createdTimestamp`)
    pub created_timestamp: Option<i64>,
    /// First value of the `picture` attribute (Java: `rep.getAttributes().get("picture")`)
    pub picture: Option<String>,
}

/// Partial update of a Keycloak user; `None` fields are left untouched (Java: `UserRepresentation`)
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct UserUpdate {
    pub first_name: Option<String>,
    pub last_name: Option<String>,
    pub email: Option<String>,
    pub email_verified: Option<bool>,
}

/// A federated identity (linked social/IdP account)
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct FederatedIdentity {
    pub identity_provider: String,
}

/// A user credential (password, passkey, ...)
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Credential {
    pub id: String,
    pub credential_type: String,
    pub user_label: Option<String>,
    /// Creation time in epoch milliseconds
    pub created_date: Option<i64>,
}

/// Keycloak admin operations used by the API
#[async_trait]
pub trait KeycloakAdmin: Send + Sync {
    async fn get_user(&self, oidc_id: &str) -> Result<KeycloakUser, KeycloakError>;
    async fn update_user(&self, oidc_id: &str, update: &UserUpdate) -> Result<(), KeycloakError>;
    async fn send_verify_email(
        &self,
        oidc_id: &str,
        redirect_uri: &str,
    ) -> Result<(), KeycloakError>;
    async fn federated_identities(
        &self,
        oidc_id: &str,
    ) -> Result<Vec<FederatedIdentity>, KeycloakError>;
    async fn remove_federated_identity(
        &self,
        oidc_id: &str,
        provider_id: &str,
    ) -> Result<(), KeycloakError>;
    async fn credentials(&self, oidc_id: &str) -> Result<Vec<Credential>, KeycloakError>;
    /// Deletes the user in Keycloak (admin endpoint `DELETE /api/v2/admin/users/{id}`)
    async fn delete_user(&self, oidc_id: &str) -> Result<(), KeycloakError>;
    /// One page of the realm's users (`first` is the offset, `max` the page size)
    async fn list_users(&self, first: i64, max: i64) -> Result<Vec<KeycloakUser>, KeycloakError>;
    /// Users matching `search` (Keycloak's own search, ordered by username), the first `max` of them.
    /// Java: `users().search(query, 0, limit)`
    async fn search_users(
        &self,
        search: &str,
        max: i64,
    ) -> Result<Vec<KeycloakUser>, KeycloakError>;
    /// Number of users in the realm
    async fn count_users(&self) -> Result<i64, KeycloakError>;
}

/// Connection settings of the confidential client (same variable names as the Helm chart)
#[derive(Clone)]
pub struct KeycloakConfig {
    pub base_url: String,
    pub realm: String,
    pub client_id: String,
    pub client_secret: String,
}

impl std::fmt::Debug for KeycloakConfig {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("KeycloakConfig")
            .field("base_url", &self.base_url)
            .field("realm", &self.realm)
            .field("client_id", &self.client_id)
            .field("client_secret", &"<redacted>")
            .finish()
    }
}

impl KeycloakConfig {
    /// Reads `AUTH_SERVER_BASE_URL`, `AUTH_SERVER_REALM` (default `master`, as in Java),
    /// `AUTH_SERVER_CLIENT_ID` and `AUTH_SERVER_CLIENT_SECRET`
    pub fn from_env() -> Result<Self, String> {
        Self::from_lookup(|name| std::env::var(name).ok())
    }

    fn from_lookup(lookup: impl Fn(&str) -> Option<String>) -> Result<Self, String> {
        let required = |name: &str| {
            lookup(name)
                .filter(|v| !v.trim().is_empty())
                .ok_or_else(|| format!("{} must be set", name))
        };
        Ok(Self {
            base_url: required("AUTH_SERVER_BASE_URL")?
                .trim_end_matches('/')
                .to_string(),
            realm: lookup("AUTH_SERVER_REALM")
                .filter(|v| !v.trim().is_empty())
                .unwrap_or_else(|| DEFAULT_REALM.to_string()),
            client_id: required("AUTH_SERVER_CLIENT_ID")?,
            client_secret: required("AUTH_SERVER_CLIENT_SECRET")?,
        })
    }

    /// URL builder for the browser-facing Keycloak flows of this client
    pub fn urls(&self) -> KeycloakUrls {
        KeycloakUrls::new(&self.base_url, &self.realm, &self.client_id)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashMap;

    fn lookup_from(pairs: &[(&str, &str)]) -> impl Fn(&str) -> Option<String> {
        let map: HashMap<String, String> = pairs
            .iter()
            .map(|(k, v)| (k.to_string(), v.to_string()))
            .collect();
        move |name| map.get(name).cloned()
    }

    #[test]
    fn config_reads_all_variables_and_trims_trailing_slash() {
        let config = KeycloakConfig::from_lookup(lookup_from(&[
            ("AUTH_SERVER_BASE_URL", "https://auth.example.org/"),
            ("AUTH_SERVER_REALM", "chronos"),
            ("AUTH_SERVER_CLIENT_ID", "api"),
            ("AUTH_SERVER_CLIENT_SECRET", "s3cret"),
        ]))
        .unwrap();
        assert_eq!(config.base_url, "https://auth.example.org");
        assert_eq!(config.realm, "chronos");
        assert_eq!(config.client_id, "api");
        assert_eq!(config.client_secret, "s3cret");
    }

    #[test]
    fn config_realm_defaults_to_master_like_java() {
        let config = KeycloakConfig::from_lookup(lookup_from(&[
            ("AUTH_SERVER_BASE_URL", "https://auth.example.org"),
            ("AUTH_SERVER_CLIENT_ID", "api"),
            ("AUTH_SERVER_CLIENT_SECRET", "s3cret"),
        ]))
        .unwrap();
        assert_eq!(config.realm, "master");
    }

    #[test]
    fn config_requires_base_url_client_id_and_secret() {
        for missing in [
            "AUTH_SERVER_BASE_URL",
            "AUTH_SERVER_CLIENT_ID",
            "AUTH_SERVER_CLIENT_SECRET",
        ] {
            let pairs: Vec<(&str, &str)> = [
                ("AUTH_SERVER_BASE_URL", "https://auth.example.org"),
                ("AUTH_SERVER_CLIENT_ID", "api"),
                ("AUTH_SERVER_CLIENT_SECRET", "s3cret"),
            ]
            .into_iter()
            .filter(|(k, _)| *k != missing)
            .collect();
            let err = KeycloakConfig::from_lookup(lookup_from(&pairs)).unwrap_err();
            assert!(err.contains(missing), "{err}");
        }
    }

    #[test]
    fn config_debug_does_not_leak_the_secret() {
        let config = KeycloakConfig::from_lookup(lookup_from(&[
            ("AUTH_SERVER_BASE_URL", "https://auth.example.org"),
            ("AUTH_SERVER_CLIENT_ID", "api"),
            ("AUTH_SERVER_CLIENT_SECRET", "s3cret"),
        ]))
        .unwrap();
        assert!(!format!("{:?}", config).contains("s3cret"));
    }
}
