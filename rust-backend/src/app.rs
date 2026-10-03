use axum::{middleware, response::IntoResponse, routing::get, Json, Router};
use std::sync::Arc;

use crate::appointments::handlers::{get_appointment, list_appointments, AppState};
use crate::security::TokenValidator;

pub fn build_router(
    app_state: Arc<AppState>,
    validator: Arc<TokenValidator>,
) -> Router {
    let public_routes = Router::new()
        .route("/q/health/live", get(health_live))
        .route("/q/health/ready", get(health_ready).with_state(app_state.db_pool.clone()))
        .route("/health", get(health_live));

    let protected_routes = Router::new()
        .route("/api/v2/me", get(get_user_info))
        .route("/api/v2/appointments", get(list_appointments))
        .route("/api/v2/appointments/:id", get(get_appointment))
        .with_state(app_state)
        .layer(middleware::from_fn_with_state(
            validator.clone(),
            crate::security::middleware::auth_middleware,
        ));

    Router::new().merge(public_routes).merge(protected_routes)
}

async fn health_live() -> &'static str {
    "OK"
}

async fn health_ready(
    axum::extract::State(pool): axum::extract::State<sqlx::PgPool>,
) -> impl IntoResponse {
    match sqlx::query_scalar::<_, i32>("SELECT 1").fetch_one(&pool).await {
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
