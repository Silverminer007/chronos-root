//! Profile logic on top of the Keycloak boundary (Java: `KeycloakProfileService`)

use chrono::{DateTime, SecondsFormat, Utc};

use crate::keycloak::{
    Credential, FederatedIdentity, KeycloakAdmin, KeycloakError, KeycloakUrls, UserUpdate,
    PASSKEY_CREDENTIAL_TYPE,
};
use crate::users::models::{PasskeyDto, PrincipalDto, UpdatedUserDto};

/// Failure of a profile operation; the handlers map it to the Java responses
#[derive(Debug, PartialEq, Eq)]
pub enum ProfileError {
    /// Java `BadRequestException` (400, errorCode `BAD_REQUEST`)
    BadRequest(String),
    /// A Java `NullPointerException` (missing `redirectUri`) – an unhandled exception, hence 500
    MissingRedirectUri,
    Keycloak(KeycloakError),
}

impl From<KeycloakError> for ProfileError {
    fn from(error: KeycloakError) -> Self {
        ProfileError::Keycloak(error)
    }
}

/// Java: `URLEncoder.encode(redirectUri)` throws a NullPointerException for null
pub fn require_redirect_uri(redirect_uri: Option<&str>) -> Result<&str, ProfileError> {
    redirect_uri.ok_or(ProfileError::MissingRedirectUri)
}

pub struct ProfileService<'a> {
    keycloak: &'a dyn KeycloakAdmin,
    urls: &'a KeycloakUrls,
}

impl<'a> ProfileService<'a> {
    pub fn new(keycloak: &'a dyn KeycloakAdmin, urls: &'a KeycloakUrls) -> Self {
        Self { keycloak, urls }
    }

    /// Java: `updateUser`. The response echoes the request values (not Keycloak's state); the
    /// verify-email URL is built last, so a missing `redirectUri` fails after Keycloak was updated.
    pub async fn update_user(
        &self,
        oidc_id: &str,
        request: &PrincipalDto,
        redirect_uri: Option<&str>,
    ) -> Result<UpdatedUserDto, ProfileError> {
        let email_changed = match request.email.as_deref() {
            Some(email) => {
                let current = self.keycloak.get_user(oidc_id).await?.email;
                current.is_none_or(|c| c.to_lowercase() != email.to_lowercase())
            }
            None => false,
        };
        if email_changed && redirect_uri.is_none() {
            return Err(ProfileError::BadRequest(
                "Redirect Url must be set to change email address".to_string(),
            ));
        }
        let update = UserUpdate {
            first_name: request.first_name.clone(),
            last_name: request.last_name.clone(),
            email: email_changed.then(|| request.email.clone()).flatten(),
            email_verified: email_changed.then_some(false),
        };
        self.keycloak.update_user(oidc_id, &update).await?;
        if email_changed {
            // redirect_uri is present: checked above
            if let Some(redirect_uri) = redirect_uri {
                self.keycloak
                    .send_verify_email(oidc_id, redirect_uri)
                    .await?;
            }
        }
        let verify_email_url = self
            .urls
            .verify_email_url(require_redirect_uri(redirect_uri)?);
        Ok(UpdatedUserDto {
            user: PrincipalDto {
                id: Some(oidc_id.to_string()),
                first_name: request.first_name.clone(),
                last_name: request.last_name.clone(),
                email: request.email.clone(),
            },
            verify_email_url,
        })
    }

    pub async fn linked_accounts(
        &self,
        oidc_id: &str,
    ) -> Result<Vec<FederatedIdentity>, KeycloakError> {
        self.keycloak.federated_identities(oidc_id).await
    }

    pub async fn unlink_account(
        &self,
        oidc_id: &str,
        provider_id: &str,
    ) -> Result<(), KeycloakError> {
        self.keycloak
            .remove_federated_identity(oidc_id, provider_id)
            .await
    }

    /// Java: `getPasskeys` – only `webauthn-passwordless` credentials
    pub async fn passkeys(&self, oidc_id: &str) -> Result<Vec<PasskeyDto>, KeycloakError> {
        Ok(self
            .keycloak
            .credentials(oidc_id)
            .await?
            .into_iter()
            .filter(|c| c.credential_type == PASSKEY_CREDENTIAL_TYPE)
            .map(passkey_dto)
            .collect())
    }
}

fn passkey_dto(credential: Credential) -> PasskeyDto {
    PasskeyDto {
        id: credential.id,
        name: credential.user_label,
        created_at: credential.created_date.and_then(instant_to_string),
        last_used_at: None,
    }
}

/// Java: `Instant.ofEpochMilli(ms).toString()` – ISO-8601 UTC, fraction only when non-zero
fn instant_to_string(epoch_millis: i64) -> Option<String> {
    let instant: DateTime<Utc> = DateTime::from_timestamp_millis(epoch_millis)?;
    Some(instant.to_rfc3339_opts(SecondsFormat::AutoSi, true))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::test_utils::FakeKeycloak;

    fn urls() -> KeycloakUrls {
        KeycloakUrls::new("https://auth.test", "chronos", "api")
    }

    fn request(first: Option<&str>, last: Option<&str>, email: Option<&str>) -> PrincipalDto {
        PrincipalDto {
            id: None,
            first_name: first.map(String::from),
            last_name: last.map(String::from),
            email: email.map(String::from),
        }
    }

    #[test]
    fn instant_matches_java_to_string() {
        assert_eq!(
            instant_to_string(1_700_000_000_000).unwrap(),
            "2023-11-14T22:13:20Z"
        );
        assert_eq!(
            instant_to_string(1_700_000_000_123).unwrap(),
            "2023-11-14T22:13:20.123Z"
        );
    }

    #[tokio::test]
    async fn update_without_email_only_touches_names() {
        let keycloak = FakeKeycloak::new().with_email("old@example.org");
        let urls = urls();
        let service = ProfileService::new(&keycloak, &urls);

        let result = service
            .update_user(
                "u1",
                &request(Some("Max"), None, None),
                Some("https://app/cb"),
            )
            .await
            .unwrap();

        assert_eq!(
            keycloak.calls(),
            vec!["update_user(u1, first=Some(\"Max\"), last=None, email=None, verified=None)"]
        );
        assert_eq!(result.user.id.as_deref(), Some("u1"));
        assert_eq!(result.user.first_name.as_deref(), Some("Max"));
        assert_eq!(result.user.email, None);
        assert!(result.verify_email_url.ends_with("&kc_action=VERIFY_EMAIL"));
    }

    #[tokio::test]
    async fn update_with_changed_email_resets_verification_and_sends_mail() {
        let keycloak = FakeKeycloak::new().with_email("old@example.org");
        let urls = urls();
        let service = ProfileService::new(&keycloak, &urls);

        service
            .update_user(
                "u1",
                &request(None, None, Some("new@example.org")),
                Some("https://app/cb"),
            )
            .await
            .unwrap();

        assert_eq!(
            keycloak.calls(),
            vec![
                "get_user(u1)",
                "update_user(u1, first=None, last=None, email=Some(\"new@example.org\"), verified=Some(false))",
                "send_verify_email(u1, https://app/cb)",
            ]
        );
    }

    #[tokio::test]
    async fn update_with_same_email_ignoring_case_is_not_a_change() {
        let keycloak = FakeKeycloak::new().with_email("Max@Example.org");
        let urls = urls();
        let service = ProfileService::new(&keycloak, &urls);

        service
            .update_user(
                "u1",
                &request(None, None, Some("max@example.ORG")),
                Some("https://app/cb"),
            )
            .await
            .unwrap();

        assert_eq!(keycloak.calls().len(), 2); // get_user + update_user, no mail
        assert!(!keycloak
            .calls()
            .iter()
            .any(|c| c.starts_with("send_verify")));
    }

    #[tokio::test]
    async fn changed_email_without_redirect_uri_is_a_bad_request_and_changes_nothing() {
        let keycloak = FakeKeycloak::new().with_email("old@example.org");
        let urls = urls();
        let service = ProfileService::new(&keycloak, &urls);

        let error = service
            .update_user("u1", &request(None, None, Some("new@example.org")), None)
            .await
            .unwrap_err();

        assert_eq!(
            error,
            ProfileError::BadRequest("Redirect Url must be set to change email address".into())
        );
        assert_eq!(keycloak.calls(), vec!["get_user(u1)"]);
    }

    #[tokio::test]
    async fn missing_redirect_uri_with_unchanged_email_fails_after_the_update_like_java() {
        let keycloak = FakeKeycloak::new();
        let urls = urls();
        let service = ProfileService::new(&keycloak, &urls);

        let error = service
            .update_user("u1", &request(Some("Max"), None, None), None)
            .await
            .unwrap_err();

        assert_eq!(error, ProfileError::MissingRedirectUri);
        assert_eq!(keycloak.calls().len(), 1); // the update already happened
    }

    #[tokio::test]
    async fn passkeys_keep_only_webauthn_passwordless_credentials() {
        let keycloak = FakeKeycloak::new()
            .with_credential(Credential {
                id: "pw".into(),
                credential_type: "password".into(),
                user_label: None,
                created_date: Some(1),
            })
            .with_credential(Credential {
                id: "pk".into(),
                credential_type: PASSKEY_CREDENTIAL_TYPE.into(),
                user_label: Some("Laptop".into()),
                created_date: None,
            });
        let urls = urls();
        let service = ProfileService::new(&keycloak, &urls);

        let passkeys = service.passkeys("u1").await.unwrap();

        assert_eq!(
            passkeys,
            vec![PasskeyDto {
                id: "pk".into(),
                name: Some("Laptop".into()),
                created_at: None,
                last_used_at: None
            }]
        );
    }
}
