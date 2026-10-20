use axum::{
    middleware,
    response::IntoResponse,
    routing::{delete, get, post},
    Json, Router,
};
use std::sync::Arc;

use crate::appointments::handlers::{
    add_group_to_appointment, add_participant, cancel_appointment, create_appointment,
    delete_appointment, get_appointment, get_participation_status, list_appointments,
    remove_participant, rsvp_to_appointment, update_appointment, AppState,
};
use crate::error::fill_error_path;
use crate::groups::handlers::{
    accept_friendship_request, add_group_member, cancel_friendship_request, create_group,
    decline_friendship_request, delete_group, friendship_suggestions, incoming_friendship_requests,
    list_friends, list_group_users, list_groups, outgoing_friendship_requests, remove_friend,
    remove_group_member, send_friendship_request, update_group,
};
use crate::push_notifications::handlers::endpoints::{
    get_public_key, status as push_status, subscribe, unsubscribe,
};
use crate::push_notifications::PushState;
use crate::security::TokenValidator;
use crate::users::{
    handlers::{
        delete_user, get_change_password_url, get_link_url, get_linked_accounts,
        get_passkey_deletion_url, get_passkey_registration_url, get_passkeys, get_settings,
        get_user, patch_user, unlink_account, update_settings,
    },
    identity::cache_identity,
    repository::UserRepository,
    UserState,
};

pub fn build_router(
    app_state: Arc<AppState>,
    user_state: Arc<UserState>,
    push_state: Arc<PushState>,
    validator: Arc<TokenValidator>,
) -> Router {
    let public_routes = Router::new()
        .route("/q/health/live", get(health_live))
        .route(
            "/q/health/ready",
            get(health_ready).with_state(app_state.db_pool.clone()),
        )
        .route("/health", get(health_live));

    let protected_routes = Router::new()
        .route("/api/v2/me", get(get_user_info))
        .route(
            "/api/v2/appointments",
            get(list_appointments).post(create_appointment),
        )
        .route(
            "/api/v2/appointments/:id",
            get(get_appointment)
                .patch(update_appointment)
                .delete(delete_appointment),
        )
        .route("/api/v2/appointments/:id/cancel", post(cancel_appointment))
        .route(
            "/api/v2/appointments/:id/groups/:groupId",
            post(add_group_to_appointment),
        )
        .route(
            "/api/v2/appointments/:id/participants/:userId",
            post(add_participant).delete(remove_participant),
        )
        .route(
            "/api/v2/appointments/:id/participation",
            post(rsvp_to_appointment),
        )
        .route(
            "/api/v2/appointments/:id/participation/status",
            get(get_participation_status),
        )
        .route("/api/v2/groups", get(list_groups).post(create_group))
        .route(
            "/api/v2/groups/:group",
            delete(delete_group).patch(update_group),
        )
        .route("/api/v2/groups/:group/users", get(list_group_users))
        .route(
            "/api/v2/groups/:group_id/user/:user_id",
            post(add_group_member).delete(remove_group_member),
        )
        .route(
            "/api/v2/friendships/suggestions",
            get(friendship_suggestions),
        )
        .route(
            "/api/v2/friendships/requests",
            post(send_friendship_request),
        )
        .route(
            "/api/v2/friendships/requests/incoming",
            get(incoming_friendship_requests),
        )
        .route(
            "/api/v2/friendships/requests/outgoing",
            get(outgoing_friendship_requests),
        )
        .route(
            "/api/v2/friendships/requests/:request_id",
            delete(cancel_friendship_request),
        )
        .route(
            "/api/v2/friendships/requests/:request_id/accept",
            post(accept_friendship_request),
        )
        .route(
            "/api/v2/friendships/requests/:request_id/decline",
            post(decline_friendship_request),
        )
        .route("/api/v2/friendships/friends", get(list_friends))
        .route(
            "/api/v2/friendships/friends/:friend_id",
            delete(remove_friend),
        )
        .with_state(app_state)
        .merge(user_routes(user_state.clone()))
        .merge(push_routes(push_state))
        // Inner layer: runs after authentication, so the principal is available
        .layer(middleware::from_fn_with_state(
            UserRepository::new(user_state.db_pool.clone()),
            cache_identity,
        ))
        .layer(middleware::from_fn_with_state(
            validator.clone(),
            crate::security::middleware::auth_middleware,
        ));

    Router::new()
        .merge(public_routes)
        .merge(protected_routes)
        .layer(middleware::from_fn(fill_error_path))
}

/// `/api/v2/user*` and `/api/v2/settings` (Java: `UserResource`, `SettingsResource`)
pub fn user_routes(user_state: Arc<UserState>) -> Router {
    Router::new()
        .route(
            "/api/v2/user",
            get(get_user)
                .post(get_user)
                .patch(patch_user)
                .delete(delete_user),
        )
        .route("/api/v2/user/linked", get(get_linked_accounts))
        .route("/api/v2/user/linked/:providerId", delete(unlink_account))
        .route(
            "/api/v2/user/link/passkey",
            get(get_passkey_registration_url),
        )
        .route("/api/v2/user/link/:provider", get(get_link_url))
        .route("/api/v2/user/unlink/passkey", get(get_passkey_deletion_url))
        .route("/api/v2/user/passkeys", get(get_passkeys))
        .route("/api/v2/user/change-password", get(get_change_password_url))
        .route("/api/v2/settings", get(get_settings).put(update_settings))
        .with_state(user_state)
}

/// `/api/v2/push/*` (Java: `PushResource`)
pub fn push_routes(push_state: Arc<PushState>) -> Router {
    Router::new()
        .route("/api/v2/push/public-key", get(get_public_key))
        .route("/api/v2/push/subscribe", post(subscribe))
        .route("/api/v2/push/status", get(push_status))
        .route("/api/v2/push/unsubscribe", delete(unsubscribe))
        .with_state(push_state)
}

async fn health_live() -> &'static str {
    "OK"
}

async fn health_ready(
    axum::extract::State(pool): axum::extract::State<sqlx::PgPool>,
) -> impl IntoResponse {
    match sqlx::query_scalar::<_, i32>("SELECT 1")
        .fetch_one(&pool)
        .await
    {
        Ok(_) => (axum::http::StatusCode::OK, "OK"),
        Err(_) => (
            axum::http::StatusCode::SERVICE_UNAVAILABLE,
            "Database unavailable",
        ),
    }
}

async fn get_user_info(principal: crate::security::PrincipalContext) -> impl IntoResponse {
    Json(serde_json::json!({
        "user_id": principal.user_id(),
    }))
}
