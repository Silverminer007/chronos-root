//! User identities resolved from the `user_profiles` cache (ADR-2026-10-20-019).
//!
//! The Java backend reads `user_profiles` first and falls back to the Keycloak Admin API. The Rust
//! backend has no Keycloak admin client, so `user_profiles` is the only source: a user without a
//! profile row is treated like a user that Keycloak reports as deleted.
//!
//! `cache_identity` keeps the cache fresh on every authenticated request
//! (Java: `PrincipalContextFilter` + `LocalDbIdentityAdapter#upsert`).

use axum::{
    extract::{Request, State},
    middleware::Next,
    response::Response,
};
use sqlx::PgExecutor;
use std::collections::HashMap;
use std::sync::Arc;
use tracing::warn;

use super::repository::UserRepository;
use crate::appointments::models::UserProfile;
use crate::security::PrincipalContext;

/// Display name of a user that does not exist (any more)
const DELETED_USER_NAME: &str = "Gelöschter Benutzer";

/// Column list of `user_profiles` as read by [`UserProfile`]
const PROFILE_COLUMNS: &str =
    "oidc_id, first_name, last_name, email, profile_picture_url, updated_at";

/// A resolved user (Java: `UserIdentity`)
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct UserIdentity {
    pub oidc_id: String,
    pub first_name: Option<String>,
    pub last_name: Option<String>,
    pub email: Option<String>,
    pub profile_picture_url: Option<String>,
}

impl UserIdentity {
    /// Sentinel for a user without a profile
    pub fn deleted(oidc_id: &str) -> Self {
        Self {
            oidc_id: oidc_id.to_string(),
            first_name: None,
            last_name: None,
            email: None,
            profile_picture_url: None,
        }
    }

    /// True for the [`UserIdentity::deleted`] sentinel (no names and no email)
    pub fn is_deleted(&self) -> bool {
        self.first_name.is_none() && self.last_name.is_none() && self.email.is_none()
    }

    /// "First Last", or "Gelöschter Benutzer" for a deleted user
    pub fn name(&self) -> String {
        if self.is_deleted() {
            return DELETED_USER_NAME.to_string();
        }
        format!(
            "{} {}",
            self.first_name.as_deref().unwrap_or(""),
            self.last_name.as_deref().unwrap_or("")
        )
        .trim()
        .to_string()
    }
}

/// The caller as described by the claims of the access token (Java: the principal is a `UserIdentity`)
impl From<&PrincipalContext> for UserIdentity {
    fn from(principal: &PrincipalContext) -> Self {
        Self {
            oidc_id: principal.user_id.clone(),
            first_name: principal.first_name.clone(),
            last_name: principal.last_name.clone(),
            email: principal.email.clone(),
            profile_picture_url: principal.profile_picture_url.clone(),
        }
    }
}

impl From<UserProfile> for UserIdentity {
    fn from(profile: UserProfile) -> Self {
        Self {
            oidc_id: profile.oidc_id,
            first_name: profile.first_name,
            last_name: profile.last_name,
            email: profile.email,
            profile_picture_url: profile.profile_picture_url,
        }
    }
}

/// Reads of `user_profiles` (Java: `IdentityPort`)
pub struct IdentityRepository;

impl IdentityRepository {
    /// Resolves the given users in one query; users without a profile map to [`UserIdentity::deleted`]
    pub async fn find_by_ids<'e>(
        db: impl PgExecutor<'e>,
        oidc_ids: &[String],
    ) -> Result<HashMap<String, UserIdentity>, sqlx::Error> {
        let profiles = sqlx::query_as::<_, UserProfile>(&format!(
            "SELECT {PROFILE_COLUMNS} FROM user_profiles WHERE oidc_id = ANY($1)"
        ))
        .bind(oidc_ids)
        .fetch_all(db)
        .await?;

        let mut users: HashMap<String, UserIdentity> = profiles
            .into_iter()
            .map(|p| (p.oidc_id.clone(), UserIdentity::from(p)))
            .collect();
        for id in oidc_ids {
            users
                .entry(id.clone())
                .or_insert_with(|| UserIdentity::deleted(id));
        }
        Ok(users)
    }

    /// Resolves one user; without a profile this is [`UserIdentity::deleted`]
    pub async fn find_by_id<'e>(
        db: impl PgExecutor<'e>,
        oidc_id: &str,
    ) -> Result<UserIdentity, sqlx::Error> {
        let mut users = Self::find_by_ids(db, &[oidc_id.to_string()]).await?;
        Ok(users
            .remove(oidc_id)
            .unwrap_or_else(|| UserIdentity::deleted(oidc_id)))
    }

    /// True if a profile exists for the user
    pub async fn exists<'e>(db: impl PgExecutor<'e>, oidc_id: &str) -> Result<bool, sqlx::Error> {
        sqlx::query_scalar::<_, bool>(
            "SELECT EXISTS (SELECT 1 FROM user_profiles WHERE oidc_id = $1)",
        )
        .bind(oidc_id)
        .fetch_one(db)
        .await
    }

    /// The user whose email equals `email`, ignoring case
    pub async fn find_by_email<'e>(
        db: impl PgExecutor<'e>,
        email: &str,
    ) -> Result<Option<UserIdentity>, sqlx::Error> {
        let profile = sqlx::query_as::<_, UserProfile>(&format!(
            "SELECT {PROFILE_COLUMNS} FROM user_profiles WHERE lower(email) = lower($1)
             ORDER BY oidc_id LIMIT 1"
        ))
        .bind(email)
        .fetch_optional(db)
        .await?;
        Ok(profile.map(UserIdentity::from))
    }

    /// Users matching `query` (Keycloak semantics: every whitespace separated term must be contained,
    /// case-insensitively, in the first name, last name or email), ordered by name
    pub async fn search<'e>(
        db: impl PgExecutor<'e>,
        query: &str,
        limit: i64,
    ) -> Result<Vec<UserIdentity>, sqlx::Error> {
        let terms: Vec<String> = query.split_whitespace().map(str::to_string).collect();
        let profiles = sqlx::query_as::<_, UserProfile>(&format!(
            "SELECT {PROFILE_COLUMNS} FROM user_profiles
             WHERE NOT EXISTS (
                 SELECT 1 FROM unnest($1::text[]) AS term
                 WHERE strpos(
                     lower(concat_ws(E'\\x01', first_name, last_name, email)),
                     lower(term)
                 ) = 0
             )
             ORDER BY lower(coalesce(last_name, '')), lower(coalesce(first_name, '')), oidc_id
             LIMIT $2"
        ))
        .bind(&terms)
        .bind(limit)
        .fetch_all(db)
        .await?;
        Ok(profiles.into_iter().map(UserIdentity::from).collect())
    }
}

/// Upserts the principal's profile. A transient DB failure must not abort a valid request (as in Java).
pub async fn cache_identity(
    State(repository): State<UserRepository>,
    request: Request,
    next: Next,
) -> Response {
    if let Some(principal) = request.extensions().get::<Arc<PrincipalContext>>() {
        if let Err(e) = repository.upsert_profile(principal).await {
            warn!(
                "Failed to upsert identity cache for user {}: {}",
                principal.user_id(),
                e
            );
        }
    }
    next.run(request).await
}

#[cfg(test)]
mod tests {
    use super::*;

    fn identity(first: Option<&str>, last: Option<&str>, email: Option<&str>) -> UserIdentity {
        UserIdentity {
            oidc_id: "sub".to_string(),
            first_name: first.map(str::to_string),
            last_name: last.map(str::to_string),
            email: email.map(str::to_string),
            profile_picture_url: None,
        }
    }

    #[test]
    fn test_deleted_sentinel_has_the_deleted_user_name() {
        let deleted = UserIdentity::deleted("sub");
        assert!(deleted.is_deleted());
        assert_eq!(deleted.name(), "Gelöschter Benutzer");
    }

    #[test]
    fn test_identity_from_principal_uses_token_claims() {
        let principal = PrincipalContext::new("sub").with_profile(
            Some("Erika".into()),
            Some("Muster".into()),
            None,
            None,
        );
        let user = UserIdentity::from(&principal);
        assert_eq!(user.oidc_id, "sub");
        assert_eq!(user.name(), "Erika Muster");
        assert!(UserIdentity::from(&PrincipalContext::new("sub")).is_deleted());
    }

    #[test]
    fn test_name_joins_first_and_last_name() {
        let user = identity(Some("Alice"), Some("Anders"), None);
        assert_eq!(user.name(), "Alice Anders");
    }

    #[test]
    fn test_name_is_stripped_when_a_part_is_missing() {
        assert_eq!(identity(Some("Alice"), None, None).name(), "Alice");
        assert_eq!(identity(None, Some("Anders"), None).name(), "Anders");
        // Only an email: not deleted, but nameless (Java: empty string)
        assert_eq!(identity(None, None, Some("a@example.com")).name(), "");
    }
}
