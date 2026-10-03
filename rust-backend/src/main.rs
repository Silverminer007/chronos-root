use std::net::SocketAddr;
use std::sync::Arc;
use tracing::{error, info};

use chronos_date_api::app::build_router;
use chronos_date_api::appointments::event_listeners::AppointmentParticipationListener;
use chronos_date_api::appointments::handlers::AppState;
use chronos_date_api::database::{init_pool, DatabaseConfig};
use chronos_date_api::event_bus::postgres::PostgresEventBus;
use chronos_date_api::security::TokenValidator;

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

    // Initialize event bus
    let event_bus = Arc::new(PostgresEventBus::new(pool.clone()));

    // Subscribe to appointment events
    let listener = AppointmentParticipationListener::new(pool.clone());
    if let Err(e) = listener.subscribe_to_events(event_bus.as_ref()).await {
        tracing::error!("Failed to subscribe to appointment events: {:?}", e);
    }

    // Create application state
    let app_state = Arc::new(AppState {
        db_pool: pool.clone(),
        event_publisher: event_bus,
    });

    let app = build_router(app_state, validator);

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
