use axum::extract::FromRequestParts;
use axum::http::request::Parts;
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use std::sync::Arc;
use uuid::Uuid;

/// Request-scoped principal context holding the current user's OIDC ID
/// Equivalent to the Java backend's PrincipalContext
#[derive(Debug, Clone)]
pub struct PrincipalContext {
    /// The user's OIDC subject ID as a UUID
    pub user_id: Uuid,
}

impl PrincipalContext {
    /// Create a new principal context with the given user ID
    pub fn new(user_id: Uuid) -> Self {
        Self { user_id }
    }

    /// Get the current user's ID
    pub fn user_id(&self) -> Uuid {
        self.user_id
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
        let user_id = Uuid::parse_str("12345678-1234-1234-1234-123456789012").unwrap();
        let principal = PrincipalContext::new(user_id);

        assert_eq!(principal.user_id(), user_id);
        assert_eq!(principal.user_id, user_id);
    }

    #[test]
    fn test_principal_context_clone() {
        let user_id = Uuid::parse_str("87654321-4321-4321-4321-210987654321").unwrap();
        let principal = PrincipalContext::new(user_id);
        let cloned = principal.clone();

        assert_eq!(cloned.user_id(), principal.user_id());
    }
}
