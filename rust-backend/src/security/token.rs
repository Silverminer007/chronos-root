use jsonwebtoken::{decode, decode_header, DecodingKey, Validation};
use serde::{Deserialize, Serialize};
use std::sync::Arc;
use tokio::sync::RwLock;

/// Claims extracted from a JWT token
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TokenClaims {
    /// Subject (user OIDC ID)
    pub sub: String,
    /// Issued at
    pub iat: i64,
    /// Expires at
    pub exp: i64,
}

/// Keycloak public key information
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct KeySet {
    pub keys: Vec<Key>,
}

/// JWK (JSON Web Key)
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Key {
    pub kid: String,
    pub kty: String,
    pub n: String,
    pub e: String,
}

/// Token validation error
#[derive(Debug)]
pub enum TokenError {
    InvalidToken(String),
    InvalidSignature,
    TokenExpired,
    MissingKey,
    KeyFetchError(String),
}

impl std::fmt::Display for TokenError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            TokenError::InvalidToken(msg) => write!(f, "Invalid token: {}", msg),
            TokenError::InvalidSignature => write!(f, "Invalid token signature"),
            TokenError::TokenExpired => write!(f, "Token expired"),
            TokenError::MissingKey => write!(f, "Missing key ID in token"),
            TokenError::KeyFetchError(msg) => write!(f, "Failed to fetch keys: {}", msg),
        }
    }
}

impl std::error::Error for TokenError {}

/// Validates JWT tokens against a Keycloak instance
pub struct TokenValidator {
    keycloak_url: String,
    key_cache: Arc<RwLock<Option<KeySet>>>,
    http_client: reqwest::Client,
}

impl TokenValidator {
    /// Create a new token validator for a Keycloak instance
    pub fn new(keycloak_url: String) -> Self {
        Self {
            keycloak_url,
            key_cache: Arc::new(RwLock::new(None)),
            http_client: reqwest::Client::new(),
        }
    }

    /// Fetch the public key set from Keycloak
    async fn fetch_keyset(&self) -> Result<KeySet, TokenError> {
        let url = format!("{}/protocol/openid-connect/certs", self.keycloak_url);

        let response = self
            .http_client
            .get(&url)
            .send()
            .await
            .map_err(|e| TokenError::KeyFetchError(e.to_string()))?;

        response
            .json::<KeySet>()
            .await
            .map_err(|e| TokenError::KeyFetchError(e.to_string()))
    }

    /// Get the key set, fetching from Keycloak if needed
    async fn get_keyset(&self) -> Result<KeySet, TokenError> {
        {
            let cache = self.key_cache.read().await;
            if let Some(keyset) = cache.as_ref() {
                return Ok(keyset.clone());
            }
        }

        let keyset = self.fetch_keyset().await?;
        *self.key_cache.write().await = Some(keyset.clone());
        Ok(keyset)
    }

    /// Validate a JWT token and extract claims
    pub async fn validate_token(&self, token: &str) -> Result<TokenClaims, TokenError> {
        // Decode the header to get the key ID
        let header = decode_header(token).map_err(|e| TokenError::InvalidToken(e.to_string()))?;

        let kid = header
            .kid
            .ok_or(TokenError::MissingKey)?;

        // Fetch the key set
        let keyset = self.get_keyset().await?;

        // Find the matching key
        let key = keyset
            .keys
            .iter()
            .find(|k| k.kid == kid)
            .ok_or(TokenError::MissingKey)?;

        // Construct the decoding key from the JWK
        let decoding_key = DecodingKey::from_rsa_components(&key.n, &key.e)
            .map_err(|e| TokenError::InvalidToken(e.to_string()))?;

        // Validate the token
        let validation = Validation::new(jsonwebtoken::Algorithm::RS256);

        let token_data = decode::<TokenClaims>(token, &decoding_key, &validation)
            .map_err(|e| {
                if e.to_string().contains("ExpiredSignature") {
                    TokenError::TokenExpired
                } else if e.to_string().contains("InvalidSignature") {
                    TokenError::InvalidSignature
                } else {
                    TokenError::InvalidToken(e.to_string())
                }
            })?;

        Ok(token_data.claims)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_token_claims_deserialize() {
        let json = r#"{"sub":"user123","iat":1234567890,"exp":1234567900}"#;
        let claims: TokenClaims = serde_json::from_str(json).unwrap();

        assert_eq!(claims.sub, "user123");
        assert_eq!(claims.iat, 1234567890);
        assert_eq!(claims.exp, 1234567900);
    }

    #[test]
    fn test_keyset_deserialize() {
        let json = r#"{"keys":[{"kid":"key1","kty":"RSA","n":"n_value","e":"e_value"}]}"#;
        let keyset: KeySet = serde_json::from_str(json).unwrap();

        assert_eq!(keyset.keys.len(), 1);
        assert_eq!(keyset.keys[0].kid, "key1");
    }

    #[test]
    fn test_token_error_display() {
        let error = TokenError::InvalidToken("test".to_string());
        assert_eq!(error.to_string(), "Invalid token: test");

        let error = TokenError::InvalidSignature;
        assert_eq!(error.to_string(), "Invalid token signature");

        let error = TokenError::TokenExpired;
        assert_eq!(error.to_string(), "Token expired");
    }

    #[tokio::test]
    async fn test_token_validator_creation() {
        let validator = TokenValidator::new("http://keycloak:8080/realms/chronos".to_string());
        assert_eq!(validator.keycloak_url, "http://keycloak:8080/realms/chronos");
    }
}
