// `/api/v2/push/*` (Java: `PushResource`)
use crate::error::{AppError, AppJson};
use crate::push_notifications::models::{PushSubscriptionRequest, PushSubscriptionStatus};
use crate::push_notifications::repository::PushSubscriptionRepository;
use crate::push_notifications::services::NotificationDispatcher;
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
    /// Sends the test notifications of the admin endpoint
    pub dispatcher: Arc<NotificationDispatcher>,
}

impl PushState {
    /// Without a sender (see [`PushState::with_dispatcher`]) notifications are omitted
    pub fn new(db_pool: PgPool, vapid_public_key: String) -> Self {
        let dispatcher = Arc::new(NotificationDispatcher::new(db_pool.clone(), None));
        Self {
            db_pool,
            vapid_public_key,
            dispatcher,
        }
    }

    pub fn with_dispatcher(mut self, dispatcher: Arc<NotificationDispatcher>) -> Self {
        self.dispatcher = dispatcher;
        self
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
    AppJson(request): AppJson<PushSubscriptionRequest>,
) -> Result<StatusCode, AppError> {
    let (Some(endpoint), Some(keys)) = (request.endpoint, request.keys) else {
        return Err(AppError::unexpected());
    };
    let (Some(p256dh), Some(auth)) = (keys.p256dh, keys.auth) else {
        return Err(AppError::unexpected());
    };
    PushSubscriptionRepository::new(state.db_pool.clone())
        .replace_for_user(principal.user_id(), &endpoint, &p256dh, &auth)
        .await
        .map_err(|e| {
            error!("Failed to save push subscription: {e}");
            AppError::unexpected()
        })?;
    Ok(StatusCode::NO_CONTENT)
}

/// GET /api/v2/push/status?endpoint= - whether any subscription with this endpoint exists
pub async fn status(
    State(state): State<Arc<PushState>>,
    Query(query): Query<EndpointQuery>,
) -> Result<impl IntoResponse, AppError> {
    let exists = match query.endpoint {
        Some(endpoint) => PushSubscriptionRepository::new(state.db_pool.clone())
            .exists_by_endpoint(&endpoint)
            .await
            .map_err(|e| {
                error!("Failed to read push subscription status: {e}");
                AppError::unexpected()
            })?,
        None => false,
    };
    Ok(Json(PushSubscriptionStatus { exists }))
}

/// DELETE /api/v2/push/unsubscribe?endpoint= - remove the endpoint; unknown endpoints are ignored
pub async fn unsubscribe(
    State(state): State<Arc<PushState>>,
    Query(query): Query<EndpointQuery>,
) -> Result<StatusCode, AppError> {
    if let Some(endpoint) = query.endpoint {
        PushSubscriptionRepository::new(state.db_pool.clone())
            .delete_by_endpoint(&endpoint)
            .await
            .map_err(|e| {
                error!("Failed to remove push subscription: {e}");
                AppError::unexpected()
            })?;
    }
    Ok(StatusCode::NO_CONTENT)
}
