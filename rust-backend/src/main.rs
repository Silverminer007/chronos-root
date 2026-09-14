use axum::{
    routing::get,
    Router,
};
use std::net::SocketAddr;

#[tokio::main]
async fn main() {
    // Initialize tracing
    tracing_subscriber::fmt::init();

    // Build router with health check endpoint
    let app = Router::new()
        .route("/q/health/live", get(health_live))
        .route("/q/health/ready", get(health_ready))
        .route("/health", get(health_live));

    // Listen on 0.0.0.0:8080
    let addr = SocketAddr::from(([0, 0, 0, 0], 8080));
    tracing::info!("Starting server on {}", addr);

    let listener = tokio::net::TcpListener::bind(addr)
        .await
        .expect("Failed to bind to port 8080");

    axum::serve(listener, app)
        .await
        .expect("Server error");
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
