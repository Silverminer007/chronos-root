use axum::{
    middleware,
    response::IntoResponse,
    routing::{delete, get, post, put},
    Json, Router,
};
use std::sync::Arc;

use crate::appointments::handlers::{
    add_group_to_appointment, add_participant, cancel_appointment, change_participant_role,
    create_appointment, delete_appointment, get_appointment, get_participation_status,
    list_appointments, remove_participant, rsvp_to_appointment, update_appointment, AppState,
};
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
            "/api/v2/appointments/:id/participation/:userId/role",
            put(change_participant_role),
        )
        .route(
            "/api/v2/appointments/:id/participation",
            post(rsvp_to_appointment),
        )
        .route(
            "/api/v2/appointments/:id/participation/status",
            get(get_participation_status),
        )
        .with_state(app_state)
        .merge(user_routes(user_state.clone()))
        // Inner layer: runs after authentication, so the principal is available
        .layer(middleware::from_fn_with_state(
            UserRepository::new(user_state.db_pool.clone()),
            cache_identity,
        ))
        .layer(middleware::from_fn_with_state(
            validator.clone(),
            crate::security::middleware::auth_middleware,
        ));

    Router::new().merge(public_routes).merge(protected_routes)
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
