use async_trait::async_trait;
use std::sync::Mutex;

use crate::keycloak::{
    Credential, FederatedIdentity, KeycloakAdmin, KeycloakError, KeycloakUser, UserUpdate,
};

/// In-memory [`KeycloakAdmin`] for tests: scripted answers plus a log of every call
#[derive(Default)]
pub struct FakeKeycloak {
    state: Mutex<FakeState>,
}

#[derive(Default)]
struct FakeState {
    user: KeycloakUser,
    /// Realm users for the admin endpoints (`list_users`, `count_users`, lookup by id)
    realm_users: Vec<KeycloakUser>,
    identities: Vec<FederatedIdentity>,
    credentials: Vec<Credential>,
    failure: Option<KeycloakError>,
    calls: Vec<String>,
}

impl FakeKeycloak {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn with_email(self, email: &str) -> Self {
        self.state.lock().unwrap().user.email = Some(email.to_string());
        self
    }

    /// The users of the realm; `get_user` then finds them by id and answers `NotFound` for others
    pub fn with_realm_users(self, users: Vec<KeycloakUser>) -> Self {
        self.state.lock().unwrap().realm_users = users;
        self
    }

    pub fn with_identity(self, provider: &str) -> Self {
        self.state
            .lock()
            .unwrap()
            .identities
            .push(FederatedIdentity {
                identity_provider: provider.to_string(),
            });
        self
    }

    pub fn with_credential(self, credential: Credential) -> Self {
        self.state.lock().unwrap().credentials.push(credential);
        self
    }

    /// Every following call fails with `error`
    pub fn failing_with(self, error: KeycloakError) -> Self {
        self.state.lock().unwrap().failure = Some(error);
        self
    }

    /// Log of the calls received, e.g. `update_user(u1, first=Max)`
    pub fn calls(&self) -> Vec<String> {
        self.state.lock().unwrap().calls.clone()
    }

    fn record(&self, call: String) -> Result<(), KeycloakError> {
        let mut state = self.state.lock().unwrap();
        state.calls.push(call);
        match &state.failure {
            Some(error) => Err(error.clone()),
            None => Ok(()),
        }
    }
}

#[async_trait]
impl KeycloakAdmin for FakeKeycloak {
    async fn get_user(&self, oidc_id: &str) -> Result<KeycloakUser, KeycloakError> {
        self.record(format!("get_user({})", oidc_id))?;
        let state = self.state.lock().unwrap();
        if state.realm_users.is_empty() {
            return Ok(state.user.clone());
        }
        state
            .realm_users
            .iter()
            .find(|user| user.id.as_deref() == Some(oidc_id))
            .cloned()
            .ok_or(KeycloakError::NotFound)
    }

    async fn update_user(&self, oidc_id: &str, update: &UserUpdate) -> Result<(), KeycloakError> {
        self.record(format!(
            "update_user({}, first={:?}, last={:?}, email={:?}, verified={:?})",
            oidc_id, update.first_name, update.last_name, update.email, update.email_verified
        ))
    }

    async fn send_verify_email(
        &self,
        oidc_id: &str,
        redirect_uri: &str,
    ) -> Result<(), KeycloakError> {
        self.record(format!("send_verify_email({}, {})", oidc_id, redirect_uri))
    }

    async fn federated_identities(
        &self,
        oidc_id: &str,
    ) -> Result<Vec<FederatedIdentity>, KeycloakError> {
        self.record(format!("federated_identities({})", oidc_id))?;
        Ok(self.state.lock().unwrap().identities.clone())
    }

    async fn remove_federated_identity(
        &self,
        oidc_id: &str,
        provider_id: &str,
    ) -> Result<(), KeycloakError> {
        self.record(format!(
            "remove_federated_identity({}, {})",
            oidc_id, provider_id
        ))
    }

    async fn credentials(&self, oidc_id: &str) -> Result<Vec<Credential>, KeycloakError> {
        self.record(format!("credentials({})", oidc_id))?;
        Ok(self.state.lock().unwrap().credentials.clone())
    }

    async fn delete_user(&self, oidc_id: &str) -> Result<(), KeycloakError> {
        self.record(format!("delete_user({})", oidc_id))
    }

    async fn list_users(&self, first: i64, max: i64) -> Result<Vec<KeycloakUser>, KeycloakError> {
        self.record(format!("list_users({}, {})", first, max))?;
        Ok(self
            .state
            .lock()
            .unwrap()
            .realm_users
            .iter()
            .skip(first as usize)
            .take(max as usize)
            .cloned()
            .collect())
    }

    async fn search_users(
        &self,
        search: &str,
        max: i64,
    ) -> Result<Vec<KeycloakUser>, KeycloakError> {
        self.record(format!("search_users({:?}, {})", search, max))?;
        let terms: Vec<String> = search.split_whitespace().map(str::to_lowercase).collect();
        Ok(self
            .state
            .lock()
            .unwrap()
            .realm_users
            .iter()
            .filter(|user| {
                let haystack = [&user.first_name, &user.last_name, &user.email]
                    .iter()
                    .filter_map(|v| v.as_deref())
                    .collect::<Vec<_>>()
                    .join("\u{1}")
                    .to_lowercase();
                terms.iter().all(|term| haystack.contains(term))
            })
            .take(max.max(0) as usize)
            .cloned()
            .collect())
    }

    async fn count_users(&self) -> Result<i64, KeycloakError> {
        self.record("count_users()".to_string())?;
        Ok(self.state.lock().unwrap().realm_users.len() as i64)
    }
}

/// A [`UserState`](crate::users::UserState) backed by a [`FakeKeycloak`], for router tests that do not exercise the user endpoints
pub fn user_state_for_tests(pool: sqlx::PgPool) -> std::sync::Arc<crate::users::UserState> {
    std::sync::Arc::new(crate::users::UserState {
        db_pool: pool,
        keycloak: std::sync::Arc::new(FakeKeycloak::new()),
        keycloak_urls: crate::keycloak::KeycloakUrls::new("https://auth.test", "chronos", "api"),
    })
}
