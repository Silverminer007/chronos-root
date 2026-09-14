use axum::{
    extract::Path, http::StatusCode, middleware, response::IntoResponse, routing::get, Json, Router,
};
use std::net::SocketAddr;
use std::sync::Arc;

use chronos_date_api::database::init_pool;
use chronos_date_api::security::{PrincipalContext, TokenValidator};
use chronos_date_api::appointments::handlers::{AppState, get_appointment, list_appointments};

#[tokio::main]
async fn main() {
    // Initialize tracing
    tracing_subscriber::fmt::init();

    // Initialize database pool
    let pool = init_pool(Default::default())
        .await
        .expect("Failed to initialize database pool");

    // Initialize token validator with Keycloak URL from environment
    let keycloak_url = std::env::var("KEYCLOAK_URL")
        .unwrap_or_else(|_| "http://localhost:8080/realms/chronos".to_string());
    let validator = Arc::new(TokenValidator::new(keycloak_url));

    // Create application state
    let app_state = Arc::new(AppState {
        db_pool: pool.clone(),
    });

    // Build router with health check endpoints (public)
    let public_routes = Router::new()
        .route("/q/health/live", get(health_live))
        .route("/q/health/ready", get(health_ready))
        .route("/health", get(health_live));

    // Protected routes require authentication
    let protected_routes = Router::new()
        .route("/api/v2/me", get(get_user_info))
        .route("/api/v2/appointments", get(list_appointments))
        .route("/api/v2/appointments/:id", get(get_appointment))
        .with_state(app_state)
        .layer(
            middleware::from_fn_with_state(
                validator.clone(),
                chronos_date_api::security::middleware::auth_middleware,
            ),
        );

    // Combine all routes
    let app = Router::new().merge(public_routes).merge(protected_routes);

    // Listen on 0.0.0.0:8080
    let addr = SocketAddr::from(([0, 0, 0, 0], 8080));
    tracing::info!("Starting server on {}", addr);

    let listener = tokio::net::TcpListener::bind(addr)
        .await
        .expect("Failed to bind to port 8080");

    axum::serve(listener, app).await.expect("Server error");
}

/// Liveness probe - indicates the process is alive
/// Used by Kubernetes to determine if the pod should be restarted
async fn health_live() -> &'static str {
    "OK"
}

/// Readiness probe - indicates the service is ready to handle traffic
/// Checks that the service is ready to accept and process requests
async fn health_ready() -> &'static str {
    // In the future, this can check database connectivity, dependencies, etc.
    // For now, we just verify the service is up and responsive
    "OK"
}

/// Get current user information - requires authentication
async fn get_user_info(principal: PrincipalContext) -> impl IntoResponse {
    Json(serde_json::json!({
        "user_id": principal.user_id(),
    }))
}
