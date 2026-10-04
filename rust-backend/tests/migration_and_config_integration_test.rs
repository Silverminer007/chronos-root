use std::sync::{Mutex, OnceLock};
use std::time::Duration;
use testcontainers::runners::AsyncRunner;
use testcontainers_modules::postgres::Postgres;
use tracing_subscriber::layer::SubscriberExt;
use tracing_subscriber::util::SubscriberInitExt;

type AnyError = Box<dyn std::error::Error + Send + Sync>;

fn env_lock() -> &'static Mutex<()> {
    static LOCK: OnceLock<Mutex<()>> = OnceLock::new();
    LOCK.get_or_init(|| Mutex::new(()))
}

#[test]
fn test_database_url_from_environment() -> Result<(), AnyError> {
    let _guard = env_lock().lock().unwrap();
    let test_url = "postgres://user:pass@localhost:5432/testdb";
    std::env::set_var("DATABASE_URL", test_url);
    std::env::set_var("APP_ENV", "production");

    let config = chronos_date_api::database::DatabaseConfig::from_env()?;

    assert_eq!(config.url, test_url);

    std::env::remove_var("DATABASE_URL");
    std::env::remove_var("APP_ENV");

    Ok(())
}

#[test]
fn test_password_not_in_log_format() -> Result<(), AnyError> {
    let logs = std::sync::Arc::new(Mutex::new(String::new()));
    let logs_clone = logs.clone();

    let layer = tracing_subscriber::fmt::layer()
        .without_time()
        .with_writer(move || {
            let logs = logs_clone.clone();
            MockWriter { logs }
        });

    let _guard = tracing_subscriber::registry().with(layer).set_default();

    let config = chronos_date_api::database::DatabaseConfig {
        url: "postgres://user:secret_password@localhost:5432/mydb".to_string(),
        max_connections: 10,
        min_connections: 2,
        acquire_timeout: Duration::from_secs(5),
        max_lifetime: Duration::from_secs(1800),
        run_migrations: false,
    };

    config.log_connection_info();

    let captured_logs = logs.lock().unwrap();

    // Password should NOT be in the logs
    assert!(
        !captured_logs.contains("secret_password"),
        "Password should not appear in logs. Log output: {}",
        captured_logs
    );

    // Expected connection info SHOULD be in the logs
    assert!(
        captured_logs.contains("host="),
        "Host info should be in logs"
    );
    assert!(
        captured_logs.contains("port="),
        "Port info should be in logs"
    );
    assert!(
        captured_logs.contains("database="),
        "Database info should be in logs"
    );
    assert!(
        captured_logs.contains("mydb"),
        "Database name should be in logs"
    );
    assert!(
        captured_logs.contains("localhost"),
        "Host should be in logs"
    );

    Ok(())
}

struct MockWriter {
    logs: std::sync::Arc<Mutex<String>>,
}

impl std::io::Write for MockWriter {
    fn write(&mut self, buf: &[u8]) -> std::io::Result<usize> {
        if let Ok(s) = std::str::from_utf8(buf) {
            self.logs.lock().unwrap().push_str(s);
        }
        Ok(buf.len())
    }

    fn flush(&mut self) -> std::io::Result<()> {
        Ok(())
    }
}

#[test]
fn test_configuration_environment_variables() -> Result<(), AnyError> {
    let _guard = env_lock().lock().unwrap();
    std::env::set_var("DATABASE_URL", "postgres://user:pass@localhost:5432/test");
    std::env::set_var("DATABASE_MAX_CONNECTIONS", "32");
    std::env::set_var("DATABASE_MIN_CONNECTIONS", "4");
    std::env::set_var("DATABASE_ACQUIRE_TIMEOUT_SECS", "10");
    std::env::set_var("DATABASE_RUN_MIGRATIONS", "false");
    std::env::set_var("APP_ENV", "production");

    let config = chronos_date_api::database::DatabaseConfig::from_env()?;

    assert_eq!(config.max_connections, 32);
    assert_eq!(config.min_connections, 4);
    assert_eq!(config.acquire_timeout, Duration::from_secs(10));
    assert!(!config.run_migrations);

    std::env::remove_var("DATABASE_URL");
    std::env::remove_var("DATABASE_MAX_CONNECTIONS");
    std::env::remove_var("DATABASE_MIN_CONNECTIONS");
    std::env::remove_var("DATABASE_ACQUIRE_TIMEOUT_SECS");
    std::env::remove_var("DATABASE_RUN_MIGRATIONS");
    std::env::remove_var("APP_ENV");

    Ok(())
}

#[test]
fn test_development_mode_default_url() -> Result<(), AnyError> {
    let _guard = env_lock().lock().unwrap();
    std::env::set_var("APP_ENV", "development");
    std::env::remove_var("DATABASE_URL");

    let config = chronos_date_api::database::DatabaseConfig::from_env()?;

    assert!(config.url.contains("localhost"));

    std::env::remove_var("APP_ENV");

    Ok(())
}

#[test]
fn test_production_mode_requires_url() -> Result<(), AnyError> {
    let _guard = env_lock().lock().unwrap();
    std::env::set_var("APP_ENV", "production");
    std::env::remove_var("DATABASE_URL");

    let result = chronos_date_api::database::DatabaseConfig::from_env();

    assert!(result.is_err());

    std::env::remove_var("APP_ENV");

    Ok(())
}

#[test]
fn test_run_migrations_default_true() -> Result<(), AnyError> {
    let _guard = env_lock().lock().unwrap();
    std::env::remove_var("DATABASE_RUN_MIGRATIONS");
    std::env::set_var("APP_ENV", "development");

    let config = chronos_date_api::database::DatabaseConfig::from_env()?;

    assert!(config.run_migrations);

    std::env::remove_var("APP_ENV");

    Ok(())
}

#[test]
fn test_run_migrations_false_parses() -> Result<(), AnyError> {
    let _guard = env_lock().lock().unwrap();
    std::env::set_var("DATABASE_RUN_MIGRATIONS", "false");
    std::env::set_var("DATABASE_URL", "postgres://localhost");
    std::env::set_var("APP_ENV", "production");

    let config = chronos_date_api::database::DatabaseConfig::from_env()?;

    assert!(!config.run_migrations);

    std::env::remove_var("DATABASE_URL");
    std::env::remove_var("DATABASE_RUN_MIGRATIONS");
    std::env::remove_var("APP_ENV");

    Ok(())
}

#[tokio::test]
async fn test_run_migrations_false_skips_migrations() -> Result<(), AnyError> {
    let container = Postgres::default().start().await;
    let host_port = container.get_host_port_ipv4(5432).await;
    let database_url = format!(
        "postgres://postgres:postgres@127.0.0.1:{}/postgres",
        host_port
    );

    let config = chronos_date_api::database::DatabaseConfig {
        url: database_url,
        max_connections: 5,
        min_connections: 1,
        acquire_timeout: Duration::from_secs(10),
        max_lifetime: Duration::from_secs(1800),
        run_migrations: false,
    };

    let pool = chronos_date_api::database::init_pool(config)
        .await
        .map_err(|e| format!("Failed to initialize pool without migrations: {}", e))?;

    let result: (i32,) = sqlx::query_as("SELECT 1")
        .fetch_one(&pool)
        .await
        .map_err(|e| format!("Failed to query database: {}", e))?;

    assert_eq!(result.0, 1);

    let tables: (i64,) = sqlx::query_as(
        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public'",
    )
    .fetch_one(&pool)
    .await
    .map_err(|e| format!("Failed to count tables: {}", e))?;

    assert_eq!(
        tables.0, 0,
        "No tables should exist when migrations are skipped"
    );

    Ok(())
}

#[test]
fn test_port_environment_variable_reading() {
    let _guard = env_lock().lock().unwrap();
    std::env::set_var("PORT", "9000");

    let port = std::env::var("PORT")
        .ok()
        .and_then(|p| p.parse::<u16>().ok())
        .unwrap_or(8080);

    assert_eq!(port, 9000);

    std::env::remove_var("PORT");
}

#[test]
fn test_port_environment_variable_default() {
    let _guard = env_lock().lock().unwrap();
    std::env::remove_var("PORT");

    let port = std::env::var("PORT")
        .ok()
        .and_then(|p| p.parse::<u16>().ok())
        .unwrap_or(8080);

    assert_eq!(port, 8080);
}

#[test]
fn test_config_acquire_timeout_default() -> Result<(), AnyError> {
    let _guard = env_lock().lock().unwrap();
    std::env::set_var("APP_ENV", "development");
    std::env::remove_var("DATABASE_ACQUIRE_TIMEOUT_SECS");

    let config = chronos_date_api::database::DatabaseConfig::from_env()?;

    assert_eq!(config.acquire_timeout, Duration::from_secs(5));

    std::env::remove_var("APP_ENV");

    Ok(())
}

#[test]
fn test_default_config_structure() -> Result<(), AnyError> {
    let config = chronos_date_api::database::DatabaseConfig::default();

    assert_eq!(config.max_connections, 16);
    assert_eq!(config.min_connections, 2);
    assert_eq!(config.acquire_timeout, Duration::from_secs(5));
    assert_eq!(config.max_lifetime, Duration::from_secs(1800));
    assert!(config.run_migrations);

    Ok(())
}

#[tokio::test]
async fn test_migrations_apply_to_empty_database() -> Result<(), AnyError> {
    // Start a fresh PostgreSQL container
    let container = Postgres::default().start().await;
    let host_port = container.get_host_port_ipv4(5432).await;

    // Build the connection string from the container
    let database_url = format!(
        "postgres://postgres:postgres@127.0.0.1:{}/postgres",
        host_port
    );

    // Create config with migrations enabled
    let config = chronos_date_api::database::DatabaseConfig {
        url: database_url,
        max_connections: 5,
        min_connections: 1,
        acquire_timeout: Duration::from_secs(10),
        max_lifetime: Duration::from_secs(1800),
        run_migrations: true,
    };

    // This should succeed — all migrations should apply to empty database
    let pool = chronos_date_api::database::init_pool(config)
        .await
        .map_err(|e| format!("Failed to initialize pool with migrations: {}", e))?;

    // Verify we can query the database (basic sanity check)
    let result: (i32,) = sqlx::query_as("SELECT 1")
        .fetch_one(&pool)
        .await
        .map_err(|e| format!("Failed to query database: {}", e))?;

    assert_eq!(result.0, 1);

    // Verify migrations created tables
    let tables: (i64,) = sqlx::query_as(
        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public'",
    )
    .fetch_one(&pool)
    .await
    .map_err(|e| format!("Failed to count tables: {}", e))?;

    assert!(
        tables.0 > 0,
        "Migrations should have created tables in the schema"
    );

    Ok(())
}

#[tokio::test]
async fn test_concurrent_migrators_on_same_database() -> Result<(), AnyError> {
    // Start a fresh PostgreSQL container
    let container = Postgres::default().start().await;
    let host_port = container.get_host_port_ipv4(5432).await;

    // Build the connection string from the container
    let database_url = format!(
        "postgres://postgres:postgres@127.0.0.1:{}/postgres",
        host_port
    );

    // Create two concurrent tasks that both initialize the pool with migrations enabled.
    // sqlx's advisory locks should ensure both succeed without conflicts.
    let url1 = database_url.clone();
    let url2 = database_url.clone();

    let handle1 = tokio::spawn(async move {
        let config = chronos_date_api::database::DatabaseConfig {
            url: url1,
            max_connections: 5,
            min_connections: 1,
            acquire_timeout: Duration::from_secs(10),
            max_lifetime: Duration::from_secs(1800),
            run_migrations: true,
        };

        chronos_date_api::database::init_pool(config)
            .await
            .map_err(|e| format!("Task 1 failed: {}", e))
    });

    let handle2 = tokio::spawn(async move {
        let config = chronos_date_api::database::DatabaseConfig {
            url: url2,
            max_connections: 5,
            min_connections: 1,
            acquire_timeout: Duration::from_secs(10),
            max_lifetime: Duration::from_secs(1800),
            run_migrations: true,
        };

        chronos_date_api::database::init_pool(config)
            .await
            .map_err(|e| format!("Task 2 failed: {}", e))
    });

    // Wait for both tasks to complete
    let result1 = handle1
        .await
        .map_err(|e| format!("Task 1 panicked: {}", e))??;
    let result2 = handle2
        .await
        .map_err(|e| format!("Task 2 panicked: {}", e))??;

    // Verify both pools can query the database
    let r1: (i32,) = sqlx::query_as("SELECT 1")
        .fetch_one(&result1)
        .await
        .map_err(|e| format!("Pool 1 query failed: {}", e))?;

    let r2: (i32,) = sqlx::query_as("SELECT 1")
        .fetch_one(&result2)
        .await
        .map_err(|e| format!("Pool 2 query failed: {}", e))?;

    assert_eq!(r1.0, 1);
    assert_eq!(r2.0, 1);

    Ok(())
}
