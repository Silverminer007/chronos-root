use axum::{middleware, response::IntoResponse, routing::get, Json, Router};
use std::net::SocketAddr;
use std::sync::Arc;
use tracing::{error, info};

use chronos_date_api::appointments::handlers::{get_appointment, list_appointments, AppState};
use chronos_date_api::database::{init_pool, DatabaseConfig};
use chronos_date_api::security::{PrincipalContext, TokenValidator};

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt::init();

    let config = match DatabaseConfig::from_env() {
        Ok(cfg) => cfg,
        Err(e) => {
            error!("Database configuration error: {}", e);
            std::process::exit(1);
        }
    };

    let pool = match init_pool(config).await {
        Ok(p) => p,
        Err(e) => {
            error!("Failed to initialize database pool: {}", e);
            std::process::exit(1);
        }
    };

    let keycloak_url = std::env::var("KEYCLOAK_URL")
        .unwrap_or_else(|_| "http://localhost:8080/realms/chronos".to_string());
    let validator = Arc::new(TokenValidator::new(keycloak_url));

    let app_state = Arc::new(AppState {
        db_pool: pool.clone(),
    });

    let public_routes = Router::new()
        .route("/q/health/live", get(health_live))
        .route("/q/health/ready", get(health_ready).with_state(pool.clone()))
        .route("/health", get(health_live));

    let protected_routes = Router::new()
        .route("/api/v2/me", get(get_user_info))
        .route("/api/v2/appointments", get(list_appointments))
        .route("/api/v2/appointments/:id", get(get_appointment))
        .with_state(app_state)
        .layer(middleware::from_fn_with_state(
            validator.clone(),
            chronos_date_api::security::middleware::auth_middleware,
        ));

    let app = Router::new().merge(public_routes).merge(protected_routes);

    let port = std::env::var("PORT")
        .ok()
        .and_then(|p| p.parse::<u16>().ok())
        .unwrap_or(8080);

    let addr = SocketAddr::from(([0, 0, 0, 0], port));
    info!("Starting server on {}", addr);

    let listener = tokio::net::TcpListener::bind(addr)
        .await
        .expect("Failed to bind to port");

    axum::serve(listener, app).await.expect("Server error");
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

async fn get_user_info(principal: PrincipalContext) -> impl IntoResponse {
    Json(serde_json::json!({
        "user_id": principal.user_id(),
    }))
}
