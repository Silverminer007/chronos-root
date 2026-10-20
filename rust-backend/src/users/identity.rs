//! Keeps the `user_profiles` cache fresh on every authenticated request
//! (Java: `PrincipalContextFilter` + `LocalDbIdentityAdapter#upsert`).

use axum::{
    extract::{Request, State},
    middleware::Next,
    response::Response,
};
use std::sync::Arc;
use tracing::warn;

use super::repository::UserRepository;
use crate::security::PrincipalContext;

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
