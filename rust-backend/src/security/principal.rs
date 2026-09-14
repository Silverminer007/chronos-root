use axum::extract::FromRequestParts;
use axum::http::request::Parts;
use std::sync::Arc;

/// Request-scoped principal context holding the current user's OIDC ID
/// Equivalent to the Java backend's PrincipalContext
#[derive(Debug, Clone)]
pub struct PrincipalContext {
    /// The user's OIDC subject ID
    pub user_id: String,
}

impl PrincipalContext {
    /// Create a new principal context with the given user ID
    pub fn new(user_id: String) -> Self {
        Self { user_id }
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
            .cloned()
            .map(|arc| (*arc).clone())
            .ok_or(PrincipalError)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_principal_context_creation() {
        let user_id = "user-123".to_string();
        let principal = PrincipalContext::new(user_id.clone());

        assert_eq!(principal.user_id(), "user-123");
        assert_eq!(principal.user_id, user_id);
    }

    #[test]
    fn test_principal_context_clone() {
        let principal = PrincipalContext::new("user-456".to_string());
        let cloned = principal.clone();

        assert_eq!(cloned.user_id(), principal.user_id());
    }
}
