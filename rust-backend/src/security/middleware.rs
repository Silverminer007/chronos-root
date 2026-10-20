use axum::{
    extract::{Request, State},
    http::StatusCode,
    middleware::Next,
    response::Response,
};
use std::sync::Arc;
use tracing::{debug, warn};

use super::{
    principal::PrincipalContext,
    token::{TokenClaims, TokenValidator},
};

const BEARER_PREFIX: &str = "Bearer ";

/// Extracts Bearer token from Authorization header
fn extract_bearer_token(auth_header: &str) -> Option<&str> {
    auth_header.strip_prefix(BEARER_PREFIX)
}

/// Builds the principal from the token claims; `sub` is an opaque OIDC subject, not necessarily a UUID
fn principal_from_claims(claims: &TokenClaims) -> Option<PrincipalContext> {
    if claims.sub.trim().is_empty() {
        None
    } else {
        Some(PrincipalContext::new(claims.sub.as_str()).with_profile(
            claims.given_name.clone(),
            claims.family_name.clone(),
            claims.email.clone(),
            claims.picture.clone(),
        ))
    }
}

/// Authentication middleware that validates JWT tokens
pub async fn auth_middleware(
    State(validator): State<Arc<TokenValidator>>,
    mut request: Request,
    next: Next,
) -> Result<Response, StatusCode> {
    // Get the Authorization header
    let auth_header = match request.headers().get("authorization") {
        Some(header) => match header.to_str() {
            Ok(header_str) => header_str,
            Err(_) => {
                warn!("Invalid Authorization header format");
                return Err(StatusCode::UNAUTHORIZED);
            }
        },
        None => {
            debug!("Missing Authorization header");
            return Err(StatusCode::UNAUTHORIZED);
        }
    };

    // Extract the Bearer token
    let token = match extract_bearer_token(auth_header) {
        Some(token) => token,
        None => {
            warn!("Missing or invalid Bearer token");
            return Err(StatusCode::UNAUTHORIZED);
        }
    };

    // Validate the token
    match validator.validate_token(token).await {
        Ok(claims) => {
            debug!("Token validated for user: {}", claims.sub);
            // The OIDC subject identifies the user; a blank subject is rejected
            match principal_from_claims(&claims) {
                Some(principal) => {
                    // Store the principal context in request extensions
                    request.extensions_mut().insert(Arc::new(principal));

                    Ok(next.run(request).await)
                }
                None => {
                    warn!("Token has a blank subject");
                    Err(StatusCode::UNAUTHORIZED)
                }
            }
        }
        Err(e) => {
            warn!("Token validation failed: {}", e);
            Err(StatusCode::UNAUTHORIZED)
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_extract_bearer_token() {
        let token = extract_bearer_token("Bearer abc123def456");
        assert_eq!(token, Some("abc123def456"));
    }

    #[test]
    fn test_extract_bearer_token_no_prefix() {
        let token = extract_bearer_token("abc123def456");
        assert_eq!(token, None);
    }

    #[test]
    fn test_extract_bearer_token_wrong_prefix() {
        let token = extract_bearer_token("Basic abc123def456");
        assert_eq!(token, None);
    }

    fn claims(sub: &str) -> TokenClaims {
        TokenClaims {
            sub: sub.to_string(),
            iat: 0,
            exp: 0,
            given_name: Some("Max".to_string()),
            family_name: None,
            email: Some("max@example.org".to_string()),
            picture: None,
        }
    }

    #[test]
    fn test_principal_from_claims_accepts_any_non_blank_subject() {
        let uuid_like =
            principal_from_claims(&claims("12345678-1234-1234-1234-123456789012")).unwrap();
        assert_eq!(uuid_like.user_id(), "12345678-1234-1234-1234-123456789012");
        let opaque = principal_from_claims(&claims("user123")).unwrap();
        assert_eq!(opaque.user_id(), "user123");
    }

    #[test]
    fn test_principal_from_claims_rejects_blank_subject() {
        assert!(principal_from_claims(&claims("")).is_none());
        assert!(principal_from_claims(&claims("   ")).is_none());
    }

    #[test]
    fn test_principal_from_claims_copies_profile_claims() {
        let principal = principal_from_claims(&claims("sub-1")).unwrap();
        assert_eq!(principal.first_name.as_deref(), Some("Max"));
        assert_eq!(principal.last_name, None);
        assert_eq!(principal.email.as_deref(), Some("max@example.org"));
    }

    #[test]
    fn test_extract_bearer_token_empty() {
        let token = extract_bearer_token("");
        assert_eq!(token, None);
    }
}
