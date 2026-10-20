use async_trait::async_trait;
use chronos_date_api::app::build_router;
use chronos_date_api::appointments::handlers::AppState;
use chronos_date_api::event_bus::{Event, EventPublisher};
use chronos_date_api::security::TokenValidator;
use chronos_date_api::test_utils::user_state_for_tests;
use http::StatusCode;
use std::sync::Arc;
use std::time::Duration;
use testcontainers::runners::AsyncRunner;
use testcontainers_modules::postgres::Postgres;
use tower::Service;

type AnyError = Box<dyn std::error::Error + Send + Sync>;

struct MockEventPublisher;

#[async_trait]
impl EventPublisher for MockEventPublisher {
    async fn fire(&self, _event: Event) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        Ok(())
    }
}

#[tokio::test]
async fn test_readiness_returns_503_when_database_unreachable() -> Result<(), AnyError> {
    // Create a pool pointing to an unreachable address
    // connect_lazy does not immediately check connectivity
    let pool = sqlx::postgres::PgPoolOptions::new()
        .max_connections(1)
        .min_connections(0)
        .acquire_timeout(Duration::from_millis(100))
        .connect_lazy("postgres://user:pass@127.0.0.1:1/unreachable")?;

    let app_state = Arc::new(AppState {
        db_pool: pool,
        event_publisher: Arc::new(MockEventPublisher),
    });
    let validator = Arc::new(TokenValidator::new(
        "http://localhost:8080/realms/chronos".to_string(),
    ));

    let mut router = build_router(
        app_state.clone(),
        user_state_for_tests(app_state.db_pool.clone()),
        Arc::new(chronos_date_api::push_notifications::PushState::new(
            app_state.db_pool.clone(),
            String::new(),
        )),
        validator,
    );

    // Create a request to /q/health/ready
    let request = http::Request::builder()
        .uri("/q/health/ready")
        .method("GET")
        .body(axum::body::Body::empty())?;

    // Call the router as a service
    let response = router.call(request).await?;

    // Should return 503 SERVICE_UNAVAILABLE
    assert_eq!(response.status(), StatusCode::SERVICE_UNAVAILABLE);

    Ok(())
}

#[tokio::test]
async fn test_readiness_returns_200_when_database_available() -> Result<(), AnyError> {
    // Start a fresh PostgreSQL container
    let container = Postgres::default().start().await?;
    let host_port = container.get_host_port_ipv4(5432).await?;

    // Create a pool pointing to the test database
    let database_url = format!(
        "postgres://postgres:postgres@127.0.0.1:{}/postgres",
        host_port
    );

    let pool = sqlx::postgres::PgPoolOptions::new()
        .max_connections(5)
        .min_connections(1)
        .acquire_timeout(Duration::from_secs(5))
        .connect(&database_url)
        .await?;

    let app_state = Arc::new(AppState {
        db_pool: pool,
        event_publisher: Arc::new(MockEventPublisher),
    });
    let validator = Arc::new(TokenValidator::new(
        "http://localhost:8080/realms/chronos".to_string(),
    ));

    let mut router = build_router(
        app_state.clone(),
        user_state_for_tests(app_state.db_pool.clone()),
        Arc::new(chronos_date_api::push_notifications::PushState::new(
            app_state.db_pool.clone(),
            String::new(),
        )),
        validator,
    );

    // Create a request to /q/health/ready
    let request = http::Request::builder()
        .uri("/q/health/ready")
        .method("GET")
        .body(axum::body::Body::empty())?;

    // Call the router as a service
    let response = router.call(request).await?;

    // Should return 200 OK
    assert_eq!(response.status(), StatusCode::OK);
    let body = axum::body::to_bytes(response.into_body(), usize::MAX).await?;
    let body: serde_json::Value = serde_json::from_slice(&body)?;
    // SmallRye/Agroal: the check carries the state per datasource (`<default>`)
    assert_eq!(body["checks"][0]["data"]["<default>"], "UP");

    Ok(())
}

#[tokio::test]
async fn test_health_live_always_returns_200() -> Result<(), AnyError> {
    // Create a pool pointing to an unreachable address
    let pool = sqlx::postgres::PgPoolOptions::new()
        .max_connections(1)
        .min_connections(0)
        .acquire_timeout(Duration::from_millis(100))
        .connect_lazy("postgres://user:pass@127.0.0.1:1/unreachable")?;

    let app_state = Arc::new(AppState {
        db_pool: pool,
        event_publisher: Arc::new(MockEventPublisher),
    });
    let validator = Arc::new(TokenValidator::new(
        "http://localhost:8080/realms/chronos".to_string(),
    ));

    let mut router = build_router(
        app_state.clone(),
        user_state_for_tests(app_state.db_pool.clone()),
        Arc::new(chronos_date_api::push_notifications::PushState::new(
            app_state.db_pool.clone(),
            String::new(),
        )),
        validator,
    );

    // Create a request to /q/health/live
    let request = http::Request::builder()
        .uri("/q/health/live")
        .method("GET")
        .body(axum::body::Body::empty())?;

    // Call the router as a service
    let response = router.call(request).await?;

    // Should return 200 OK regardless of database state
    assert_eq!(response.status(), StatusCode::OK);

    Ok(())
}

#[tokio::test]
async fn test_readiness_reports_the_datasource_as_down_when_database_unreachable(
) -> Result<(), AnyError> {
    let pool = sqlx::postgres::PgPoolOptions::new()
        .max_connections(1)
        .min_connections(0)
        .acquire_timeout(Duration::from_millis(100))
        .connect_lazy("postgres://user:pass@127.0.0.1:1/unreachable")?;
    let app_state = Arc::new(AppState {
        db_pool: pool,
        event_publisher: Arc::new(MockEventPublisher),
    });
    let mut router = build_router(
        app_state.clone(),
        user_state_for_tests(app_state.db_pool.clone()),
        Arc::new(chronos_date_api::push_notifications::PushState::new(
            app_state.db_pool.clone(),
            String::new(),
        )),
        Arc::new(TokenValidator::new(
            "http://localhost:8080/realms/chronos".to_string(),
        )),
    );
    let request = http::Request::builder()
        .uri("/q/health/ready")
        .body(axum::body::Body::empty())?;
    let response = router.call(request).await?;
    let body = axum::body::to_bytes(response.into_body(), usize::MAX).await?;
    let body: serde_json::Value = serde_json::from_slice(&body)?;
    assert_eq!(body["checks"][0]["data"]["<default>"], "DOWN");
    Ok(())
}

/// Java maps `@Path("/")` on the collection resources, so a trailing slash answers like none.
/// The public health routes are not behind the auth layer, so 200 proves the route matched.
/// (Authenticated collection routes: `groups_integration_test`.)
#[tokio::test]
async fn test_health_routes_answer_with_a_trailing_slash() -> Result<(), AnyError> {
    let pool = sqlx::postgres::PgPoolOptions::new()
        .max_connections(1)
        .min_connections(0)
        .acquire_timeout(Duration::from_millis(100))
        .connect_lazy("postgres://user:pass@127.0.0.1:1/unreachable")?;
    let app_state = Arc::new(AppState {
        db_pool: pool,
        event_publisher: Arc::new(MockEventPublisher),
    });
    let mut router = build_router(
        app_state.clone(),
        user_state_for_tests(app_state.db_pool.clone()),
        Arc::new(chronos_date_api::push_notifications::PushState::new(
            app_state.db_pool.clone(),
            String::new(),
        )),
        Arc::new(TokenValidator::new(
            "http://localhost:8080/realms/chronos".to_string(),
        )),
    );
    let response = router
        .call(
            http::Request::builder()
                .uri("/q/health/live/")
                .body(axum::body::Body::empty())?,
        )
        .await?;
    assert_eq!(response.status(), StatusCode::OK);
    Ok(())
}
