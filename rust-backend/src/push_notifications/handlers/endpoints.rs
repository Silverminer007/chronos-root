// `/api/v2/push/*` (Java: `PushResource`)
use crate::push_notifications::models::{PushSubscriptionRequest, PushSubscriptionStatus};
use crate::push_notifications::repository::PushSubscriptionRepository;
use crate::security::PrincipalContext;
use axum::{
    extract::{Query, State},
    http::{header, StatusCode},
    response::IntoResponse,
    Json,
};
use serde::Deserialize;
use sqlx::PgPool;
use std::sync::Arc;
use tracing::error;

/// Shared state of the push endpoints
#[derive(Clone)]
pub struct PushState {
    pub db_pool: PgPool,
    /// VAPID public key handed to browsers; empty when push is not configured (like Java)
    pub vapid_public_key: String,
}

impl PushState {
    pub fn new(db_pool: PgPool, vapid_public_key: String) -> Self {
        Self {
            db_pool,
            vapid_public_key,
        }
    }
}

#[derive(Debug, Deserialize)]
pub struct EndpointQuery {
    pub endpoint: Option<String>,
}

/// GET /api/v2/push/public-key - the key as raw body, labelled `application/json` like Java
pub async fn get_public_key(State(state): State<Arc<PushState>>) -> impl IntoResponse {
    (
        [(header::CONTENT_TYPE, "application/json")],
        state.vapid_public_key.clone(),
    )
}

/// POST /api/v2/push/subscribe - store the subscription of the caller, replacing a known endpoint.
/// Java fails with 500 (NullPointerException / constraint violation) when endpoint or keys are missing.
pub async fn subscribe(
    State(state): State<Arc<PushState>>,
    principal: PrincipalContext,
    Json(request): Json<PushSubscriptionRequest>,
) -> Result<StatusCode, StatusCode> {
    let (Some(endpoint), Some(keys)) = (request.endpoint, request.keys) else {
        return Err(StatusCode::INTERNAL_SERVER_ERROR);
    };
    let (Some(p256dh), Some(auth)) = (keys.p256dh, keys.auth) else {
        return Err(StatusCode::INTERNAL_SERVER_ERROR);
    };
    PushSubscriptionRepository::new(state.db_pool.clone())
        .replace_for_user(principal.user_id(), &endpoint, &p256dh, &auth)
        .await
        .map_err(|e| {
            error!("Failed to save push subscription: {e}");
            StatusCode::INTERNAL_SERVER_ERROR
        })?;
    Ok(StatusCode::NO_CONTENT)
}

/// GET /api/v2/push/status?endpoint= - whether any subscription with this endpoint exists
pub async fn status(
    State(state): State<Arc<PushState>>,
    Query(query): Query<EndpointQuery>,
) -> Result<impl IntoResponse, StatusCode> {
    let exists = match query.endpoint {
        Some(endpoint) => PushSubscriptionRepository::new(state.db_pool.clone())
            .exists_by_endpoint(&endpoint)
            .await
            .map_err(|e| {
                error!("Failed to read push subscription status: {e}");
                StatusCode::INTERNAL_SERVER_ERROR
            })?,
        None => false,
    };
    Ok(Json(PushSubscriptionStatus { exists }))
}

/// DELETE /api/v2/push/unsubscribe?endpoint= - remove the endpoint; unknown endpoints are ignored
pub async fn unsubscribe(
    State(state): State<Arc<PushState>>,
    Query(query): Query<EndpointQuery>,
) -> Result<StatusCode, StatusCode> {
    if let Some(endpoint) = query.endpoint {
        PushSubscriptionRepository::new(state.db_pool.clone())
            .delete_by_endpoint(&endpoint)
            .await
            .map_err(|e| {
                error!("Failed to remove push subscription: {e}");
                StatusCode::INTERNAL_SERVER_ERROR
            })?;
    }
    Ok(StatusCode::NO_CONTENT)
}
