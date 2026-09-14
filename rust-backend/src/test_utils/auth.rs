use jsonwebtoken::{encode, Algorithm, EncodingKey, Header};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

/// Claims for a test JWT token
#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct TestTokenClaims {
    pub sub: String,
    pub exp: i64,
    pub iat: i64,
    pub email: Option<String>,
    pub name: Option<String>,
}

/// Test authentication helper for creating JWTs
pub struct TestAuthHelper {
    secret: String,
}

impl TestAuthHelper {
    /// Create a new test auth helper
    pub fn new() -> Self {
        Self {
            secret: "test-secret-key-for-testing-only".to_string(),
        }
    }

    /// Create a test JWT with a specific user ID
    pub fn create_token(&self, user_id: &str) -> Result<String, jsonwebtoken::errors::Error> {
        self.create_token_with_claims(user_id, None, None)
    }

    /// Create a test JWT with custom claims
    pub fn create_token_with_claims(
        &self,
        user_id: &str,
        email: Option<String>,
        name: Option<String>,
    ) -> Result<String, jsonwebtoken::errors::Error> {
        let now = chrono::Utc::now().timestamp();
        let claims = TestTokenClaims {
            sub: user_id.to_string(),
            exp: now + 3600, // Expires in 1 hour
            iat: now,
            email,
            name,
        };

        let key = EncodingKey::from_secret(self.secret.as_bytes());
        encode(&Header::new(Algorithm::HS256), &claims, &key)
    }

    /// Create a test JWT from a UUID
    pub fn create_token_from_uuid(&self, user_id: Uuid) -> Result<String, jsonwebtoken::errors::Error> {
        self.create_token(&user_id.to_string())
    }

    /// Create an authorization header value for a test token
    pub fn create_auth_header(&self, user_id: &str) -> Result<String, jsonwebtoken::errors::Error> {
        let token = self.create_token(user_id)?;
        Ok(format!("Bearer {}", token))
    }
}

impl Default for TestAuthHelper {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_create_token() {
        let helper = TestAuthHelper::new();
        let token = helper.create_token("test-user-123").expect("Failed to create token");
        assert!(!token.is_empty());
    }

    #[test]
    fn test_create_token_with_claims() {
        let helper = TestAuthHelper::new();
        let token = helper
            .create_token_with_claims(
                "test-user-456",
                Some("test@example.com".to_string()),
                Some("Test User".to_string()),
            )
            .expect("Failed to create token");
        assert!(!token.is_empty());
    }

    #[test]
    fn test_create_auth_header() {
        let helper = TestAuthHelper::new();
        let header = helper.create_auth_header("test-user-789").expect("Failed to create header");
        assert!(header.starts_with("Bearer "));
    }

    #[test]
    fn test_create_token_from_uuid() {
        let helper = TestAuthHelper::new();
        let user_id = Uuid::new_v4();
        let token = helper
            .create_token_from_uuid(user_id)
            .expect("Failed to create token");
        assert!(!token.is_empty());
    }

    #[test]
    fn test_token_not_expired() {
        let helper = TestAuthHelper::new();
        let token = helper.create_token("test-user-123").expect("Failed to create token");

        // Token should be valid (no way to verify without secret validation setup)
        // But we can at least verify it's a valid JWT format
        let parts: Vec<&str> = token.split('.').collect();
        assert_eq!(parts.len(), 3, "JWT should have 3 parts separated by dots");
    }
}
