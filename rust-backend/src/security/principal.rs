use axum::extract::FromRequestParts;
use axum::http::request::Parts;
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use std::sync::Arc;

/// Request-scoped principal context holding the current user's OIDC ID
/// Equivalent to the Java backend's PrincipalContext
#[derive(Debug, Clone)]
pub struct PrincipalContext {
    /// The user's OIDC subject (`sub` claim), e.g. a Keycloak user id
    pub user_id: String,
}

impl PrincipalContext {
    /// Create a new principal context with the given user ID
    pub fn new(user_id: impl Into<String>) -> Self {
        Self {
            user_id: user_id.into(),
        }
    }

    /// Get the current user's ID
    pub fn user_id(&self) -> &str {
        &self.user_id
    }
}

/// Error type for principal extraction
#[derive(Debug)]
pub struct PrincipalError;

impl std::fmt::Display for PrincipalError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "Principal not found in request context")
    }
}

impl std::error::Error for PrincipalError {}

impl IntoResponse for PrincipalError {
    fn into_response(self) -> Response {
        StatusCode::UNAUTHORIZED.into_response()
    }
}

/// Extractor for PrincipalContext from request
#[async_trait::async_trait]
impl<S> FromRequestParts<S> for PrincipalContext
where
    S: Send + Sync,
{
    type Rejection = PrincipalError;

    async fn from_request_parts(parts: &mut Parts, _state: &S) -> Result<Self, Self::Rejection> {
        parts
            .extensions
            .get::<Arc<PrincipalContext>>()
            .map(|arc| (**arc).clone())
            .ok_or(PrincipalError)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_principal_context_creation() {
        let user_id = "12345678-1234-1234-1234-123456789012";
        let principal = PrincipalContext::new(user_id);

        assert_eq!(principal.user_id(), user_id);
        assert_eq!(principal.user_id, user_id);
    }

    #[test]
    fn test_principal_context_clone() {
        let principal = PrincipalContext::new("87654321-4321-4321-4321-210987654321");
        let cloned = principal.clone();

        assert_eq!(cloned.user_id(), principal.user_id());
    }

    #[test]
    fn test_principal_context_accepts_non_uuid_subjects() {
        // OIDC subjects are opaque strings (ADR 0013)
        let principal = PrincipalContext::new("keycloak|user-42");
        assert_eq!(principal.user_id(), "keycloak|user-42");
    }
}
