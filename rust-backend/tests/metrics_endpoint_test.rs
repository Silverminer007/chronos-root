//! `/metrics` parity with Micrometer (ADR-2026-10-20-020) and Quarkus-style health bodies.
use async_trait::async_trait;
use axum::body::to_bytes;
use chronos_date_api::app::build_router;
use chronos_date_api::appointments::handlers::AppState;
use chronos_date_api::event_bus::{Event, EventPublisher};
use chronos_date_api::security::TokenValidator;
use chronos_date_api::test_utils::user_state_for_tests;
use http::StatusCode;
use std::sync::Arc;
use std::time::Duration;
use tower::Service;

type AnyError = Box<dyn std::error::Error + Send + Sync>;

struct NoopPublisher;

#[async_trait]
impl EventPublisher for NoopPublisher {
    async fn fire(&self, _event: Event) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        Ok(())
    }
}

fn router() -> Result<axum::Router, AnyError> {
    let pool = sqlx::postgres::PgPoolOptions::new()
        .max_connections(3)
        .min_connections(0)
        .acquire_timeout(Duration::from_millis(100))
        .connect_lazy("postgres://user:pass@127.0.0.1:1/unreachable")?;
    let app_state = Arc::new(AppState {
        db_pool: pool,
        event_publisher: Arc::new(NoopPublisher),
    });
    let validator = Arc::new(TokenValidator::new(
        "http://localhost:8080/realms/chronos".to_string(),
    ));
    Ok(build_router(
        app_state.clone(),
        user_state_for_tests(app_state.db_pool.clone()),
        validator,
    ))
}

async fn get(router: &mut axum::Router, uri: &str) -> Result<(StatusCode, String), AnyError> {
    let request = http::Request::builder()
        .uri(uri)
        .method("GET")
        .body(axum::body::Body::empty())?;
    let response = router.call(request).await?;
    let status = response.status();
    let body = to_bytes(response.into_body(), usize::MAX).await?;
    Ok((status, String::from_utf8(body.to_vec())?))
}

#[tokio::test]
async fn metrics_expose_http_server_requests_with_micrometer_labels() -> Result<(), AnyError> {
    let mut router = router()?;
    get(&mut router, "/q/health/live").await?;
    get(&mut router, "/q/health/live").await?;
    // Protected route without token: 401 and labelled with the route template
    let (status, _) = get(&mut router, "/api/v2/appointments/123").await?;
    assert_eq!(status, StatusCode::UNAUTHORIZED);
    let (status, _) = get(&mut router, "/does/not/exist").await?;
    // Unmatched paths hit the auth layer first (as in Quarkus), so they are 401
    assert_eq!(status, StatusCode::UNAUTHORIZED);

    let (status, body) = get(&mut router, "/metrics").await?;
    assert_eq!(status, StatusCode::OK);

    assert!(
        body.contains("# TYPE http_server_requests_seconds histogram"),
        "{body}"
    );
    assert!(
        body.contains("http_server_requests_seconds_count{exception=\"none\",method=\"GET\",outcome=\"SUCCESS\",status=\"200\",uri=\"/q/health/live\"} 2"),
        "{body}"
    );
    assert!(
        body.contains("http_server_requests_seconds_sum{exception=\"none\",method=\"GET\",outcome=\"SUCCESS\",status=\"200\",uri=\"/q/health/live\"} "),
        "{body}"
    );
    assert!(
        body.contains("http_server_requests_seconds_bucket{exception=\"none\",method=\"GET\",outcome=\"SUCCESS\",status=\"200\",uri=\"/q/health/live\",le=\"+Inf\"} 2"),
        "{body}"
    );
    assert!(
        body.contains("http_server_requests_seconds_count{exception=\"none\",method=\"GET\",outcome=\"CLIENT_ERROR\",status=\"401\",uri=\"/api/v2/appointments/{id}\"} 1"),
        "{body}"
    );
    assert!(
        body.contains("http_server_requests_seconds_count{exception=\"none\",method=\"GET\",outcome=\"CLIENT_ERROR\",status=\"401\",uri=\"NOT_FOUND\"} 1"),
        "{body}"
    );
    assert!(body.contains("http_server_requests_seconds_max{"), "{body}");
    // /metrics itself is ignored (Java: ignore-patterns=^/metrics$)
    assert!(!body.contains("uri=\"/metrics\""), "{body}");
    Ok(())
}

#[tokio::test]
async fn metrics_expose_process_and_db_pool_series() -> Result<(), AnyError> {
    let mut router = router()?;
    let (_, body) = get(&mut router, "/metrics").await?;
    for series in [
        "process_uptime_seconds ",
        "process_start_time_seconds ",
        "system_cpu_count ",
        "agroal_active_count{datasource=\"default\"} 0",
        "agroal_available_count{datasource=\"default\"} 0",
        "agroal_max_size{datasource=\"default\"} 3",
    ] {
        assert!(body.contains(series), "missing {series}\n{body}");
    }
    Ok(())
}

#[tokio::test]
async fn metrics_content_type_is_prometheus_text() -> Result<(), AnyError> {
    let mut router = router()?;
    let request = http::Request::builder()
        .uri("/metrics")
        .body(axum::body::Body::empty())?;
    let response = router.call(request).await?;
    let content_type = response.headers()[http::header::CONTENT_TYPE].to_str()?;
    assert!(content_type.starts_with("text/plain"), "{content_type}");
    Ok(())
}

#[tokio::test]
async fn health_endpoints_use_quarkus_json_bodies() -> Result<(), AnyError> {
    let mut router = router()?;
    let (status, body) = get(&mut router, "/q/health/live").await?;
    assert_eq!(status, StatusCode::OK);
    let json: serde_json::Value = serde_json::from_str(&body)?;
    assert_eq!(json["status"], "UP");

    // Database unreachable: readiness is DOWN with 503
    let (status, body) = get(&mut router, "/q/health/ready").await?;
    assert_eq!(status, StatusCode::SERVICE_UNAVAILABLE);
    let json: serde_json::Value = serde_json::from_str(&body)?;
    assert_eq!(json["status"], "DOWN");
    assert_eq!(json["checks"][0]["status"], "DOWN");

    // /health is kept as is
    let (status, body) = get(&mut router, "/health").await?;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(body, "OK");
    Ok(())
}
