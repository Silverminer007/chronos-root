use axum::{
    body::Body,
    extract::{Request, State},
    http::StatusCode,
    middleware::Next,
    response::Response,
};
use std::sync::Arc;
use tracing::{debug, warn};

use super::{token::TokenValidator, principal::PrincipalContext};

/// Extracts Bearer token from Authorization header
fn extract_bearer_token(auth_header: &str) -> Option<&str> {
    if auth_header.starts_with("Bearer ") {
        Some(&auth_header[7..])
    } else {
        None
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
            // Store the principal context in request extensions
            let principal = PrincipalContext::new(claims.sub);
            request.extensions_mut().insert(Arc::new(principal));

            Ok(next.run(request).await)
        }
        Err(e) => {
            warn!("Token validation failed: {}", e);
            Err(StatusCode::UNAUTHORIZED)
        }
    }
}

#[derive(Clone)]
pub struct AuthMiddleware;

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

    #[test]
    fn test_extract_bearer_token_empty() {
        let token = extract_bearer_token("");
        assert_eq!(token, None);
    }
}
