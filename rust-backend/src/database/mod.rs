use sqlx::postgres::PgPoolOptions;
use sqlx::PgPool;
use std::time::Duration;
use tracing::{error, info};
use url::Url;

pub mod repository;

/// Database configuration
#[derive(Clone, Debug)]
pub struct DatabaseConfig {
    pub url: String,
    pub max_connections: u32,
    pub min_connections: u32,
    pub acquire_timeout: Duration,
    pub max_lifetime: Duration,
    pub run_migrations: bool,
    /// Take over an existing schema created by the Java backend's Flyway migrations instead of
    /// failing on the baseline migration (`DATABASE_ADOPT_JAVA_SCHEMA`, default off)
    pub adopt_java_schema: bool,
}

impl DatabaseConfig {
    /// Load configuration from environment variables
    pub fn from_env() -> Result<Self, String> {
        let app_env = std::env::var("APP_ENV").unwrap_or_else(|_| "development".to_string());

        let url = if app_env == "development" {
            std::env::var("DATABASE_URL")
                .unwrap_or_else(|_| "postgres://chronos:chronos@localhost:5432/chronos".to_string())
        } else {
            std::env::var("DATABASE_URL")
                .map_err(|_| "DATABASE_URL environment variable is required".to_string())?
        };

        let max_connections = std::env::var("DATABASE_MAX_CONNECTIONS")
            .ok()
            .and_then(|v| v.parse().ok())
            .unwrap_or(16);

        let min_connections = std::env::var("DATABASE_MIN_CONNECTIONS")
            .ok()
            .and_then(|v| v.parse().ok())
            .unwrap_or(2);

        let acquire_timeout_secs = std::env::var("DATABASE_ACQUIRE_TIMEOUT_SECS")
            .ok()
            .and_then(|v| v.parse().ok())
            .unwrap_or(5);

        let run_migrations = std::env::var("DATABASE_RUN_MIGRATIONS")
            .ok()
            .and_then(|v| v.to_lowercase().parse::<bool>().ok())
            .unwrap_or(true);

        let adopt_java_schema = std::env::var("DATABASE_ADOPT_JAVA_SCHEMA")
            .ok()
            .and_then(|v| v.to_lowercase().parse::<bool>().ok())
            .unwrap_or(false);

        Ok(Self {
            url,
            max_connections,
            min_connections,
            acquire_timeout: Duration::from_secs(acquire_timeout_secs),
            max_lifetime: Duration::from_secs(1800),
            run_migrations,
            adopt_java_schema,
        })
    }

    /// Log database connection info without exposing the password
    pub fn log_connection_info(&self) {
        if let Ok(parsed_url) = Url::parse(&self.url) {
            let host = parsed_url
                .host()
                .map(|h| h.to_string())
                .unwrap_or_else(|| "unknown".to_string());
            let port = parsed_url.port().unwrap_or(5432);
            let database = parsed_url.path().trim_start_matches('/');
            info!(
                "Database configuration: host={}, port={}, database={}",
                host, port, database
            );
        }
    }
}

impl Default for DatabaseConfig {
    fn default() -> Self {
        Self {
            url: "postgres://chronos:chronos@localhost:5432/chronos".to_string(),
            max_connections: 16, // Conservative for <50 MiB memory target
            min_connections: 2,
            acquire_timeout: Duration::from_secs(5),
            max_lifetime: Duration::from_secs(1800), // 30 minutes
            run_migrations: true,
            adopt_java_schema: false,
        }
    }
}

/// Initialize the database connection pool
pub async fn init_pool(config: DatabaseConfig) -> Result<PgPool, Box<dyn std::error::Error>> {
    config.log_connection_info();

    let pool = PgPoolOptions::new()
        .max_connections(config.max_connections)
        .min_connections(config.min_connections)
        .acquire_timeout(config.acquire_timeout)
        .max_lifetime(config.max_lifetime)
        .connect(&config.url)
        .await
        .map_err(|e| {
            error!("Failed to connect to database: {}", e);
            Box::new(e) as Box<dyn std::error::Error>
        })?;

    if config.run_migrations {
        if config.adopt_java_schema {
            adopt_java_schema(&pool).await?;
        }
        run_migrations(&pool).await?;
    }

    info!("Database pool initialized successfully");
    Ok(pool)
}

/// Run database migrations using sqlx::migrate
pub async fn run_migrations(pool: &PgPool) -> Result<(), Box<dyn std::error::Error>> {
    info!("Running database migrations");
    sqlx::migrate!("./migrations")
        .run(pool)
        .await
        .map_err(|e| {
            error!("Migration failed: {}", e);
            Box::new(e) as Box<dyn std::error::Error>
        })?;
    info!("Migrations completed successfully");
    Ok(())
}

/// Final Java schema (ADR-2026-10-12-014): the query that describes a schema and the expected result
const SCHEMA_SNAPSHOT_SQL: &str = include_str!("../../schema/snapshot.sql");
const JAVA_SCHEMA_EXPECTED: &str = include_str!("../../schema/java_schema_expected.txt");
/// Arbitrary constant key of the advisory lock that serialises concurrent adoptions
const ADOPT_LOCK_KEY: i64 = 0x0063_6872_6f6e_6f73;

/// Lets the Rust migrations take over a database created by the Java backend (Flyway).
///
/// Baseline migration `001` would fail on such a database (`CREATE TABLE` of existing tables). If the
/// database has the Java tables but no `_sqlx_migrations` table, and its schema equals the expected
/// Java schema exactly, `001` is recorded as applied (with the checksum of the embedded migration),
/// so that `run_migrations` only applies the Rust-only migrations afterwards. Existing data is not
/// touched. Returns `true` if the schema was adopted.
///
/// Does nothing on an empty database or one that Rust already manages (`_sqlx_migrations` has rows). A schema that deviates from
/// the Java one is an error: nothing is changed, so a wrong database is never marked as migrated.
pub async fn adopt_java_schema(pool: &PgPool) -> Result<bool, Box<dyn std::error::Error>> {
    let mut tx = pool.begin().await?;
    sqlx::query("SELECT pg_advisory_xact_lock($1)")
        .bind(ADOPT_LOCK_KEY)
        .execute(&mut *tx)
        .await?;

    // sqlx creates `_sqlx_migrations` before applying anything, so a failed first start leaves it
    // empty: only recorded migrations mean that Rust manages the database
    let table_exists: bool =
        sqlx::query_scalar("SELECT to_regclass('_sqlx_migrations') IS NOT NULL")
            .fetch_one(&mut *tx)
            .await?;
    let managed: bool = table_exists
        && sqlx::query_scalar("SELECT EXISTS (SELECT 1 FROM _sqlx_migrations)")
            .fetch_one(&mut *tx)
            .await?;
    let has_java_tables: bool = sqlx::query_scalar("SELECT to_regclass('appointment') IS NOT NULL")
        .fetch_one(&mut *tx)
        .await?;
    if managed || !has_java_tables {
        return Ok(false);
    }

    let actual: std::collections::BTreeSet<String> = sqlx::query_scalar(SCHEMA_SNAPSHOT_SQL)
        .fetch_all(&mut *tx)
        .await?
        .into_iter()
        .collect();
    let expected: std::collections::BTreeSet<String> =
        JAVA_SCHEMA_EXPECTED.lines().map(str::to_string).collect();
    if actual != expected {
        let join = |a: &std::collections::BTreeSet<String>,
                    b: &std::collections::BTreeSet<String>| {
            a.difference(b).cloned().collect::<Vec<_>>().join("; ")
        };
        let message = format!(
            "Cannot adopt the existing schema: it differs from the Java schema. missing: [{}], unexpected: [{}]",
            join(&expected, &actual),
            join(&actual, &expected)
        );
        error!("{}", message);
        return Err(message.into());
    }

    let migrator = sqlx::migrate!("./migrations");
    let baseline = migrator
        .iter()
        .find(|m| m.version == 1)
        .ok_or("baseline migration 001 not found")?;
    // Same table definition as sqlx creates itself
    sqlx::query(
        "CREATE TABLE IF NOT EXISTS _sqlx_migrations (
            version BIGINT PRIMARY KEY,
            description TEXT NOT NULL,
            installed_on TIMESTAMPTZ NOT NULL DEFAULT now(),
            success BOOLEAN NOT NULL,
            checksum BYTEA NOT NULL,
            execution_time BIGINT NOT NULL
        )",
    )
    .execute(&mut *tx)
    .await?;
    sqlx::query(
        "INSERT INTO _sqlx_migrations (version, description, success, checksum, execution_time)
         VALUES ($1, $2, TRUE, $3, 0)",
    )
    .bind(baseline.version)
    .bind(baseline.description.as_ref())
    .bind(baseline.checksum.as_ref())
    .execute(&mut *tx)
    .await?;
    tx.commit().await?;
    info!("Adopted the existing Java schema: baseline migration 001 recorded as applied");
    Ok(true)
}

/// Serialises tests that read or write process-wide environment variables (such as `DATABASE_URL`),
/// because every test thread shares the same environment. `test_utils` is always compiled, so this is too.
pub(crate) fn test_env_lock() -> std::sync::MutexGuard<'static, ()> {
    static LOCK: std::sync::OnceLock<std::sync::Mutex<()>> = std::sync::OnceLock::new();
    LOCK.get_or_init(|| std::sync::Mutex::new(()))
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner())
}

#[cfg(test)]
mod tests {
    use super::*;

    const ENV_VARS: &[&str] = &[
        "APP_ENV",
        "DATABASE_URL",
        "DATABASE_MAX_CONNECTIONS",
        "DATABASE_MIN_CONNECTIONS",
        "DATABASE_ACQUIRE_TIMEOUT_SECS",
        "DATABASE_RUN_MIGRATIONS",
    ];

    /// Restores the captured environment variables on drop, so one test cannot leak values into another
    struct EnvRestore(Vec<(&'static str, Option<std::ffi::OsString>)>);

    impl EnvRestore {
        fn capture() -> Self {
            Self(
                ENV_VARS
                    .iter()
                    .map(|name| (*name, std::env::var_os(name)))
                    .collect(),
            )
        }
    }

    impl Drop for EnvRestore {
        fn drop(&mut self) {
            for (name, value) in &self.0 {
                match value {
                    Some(value) => std::env::set_var(name, value),
                    None => std::env::remove_var(name),
                }
            }
        }
    }

    #[test]
    fn test_default_config() {
        let config = DatabaseConfig::default();
        assert_eq!(config.max_connections, 16);
        assert_eq!(config.min_connections, 2);
    }

    #[test]
    fn test_config_memory_efficient() {
        let config = DatabaseConfig::default();
        assert!(
            config.max_connections <= 32,
            "Connection pool too large for memory target"
        );
    }

    #[test]
    fn test_config_from_env_development() {
        let _guard = test_env_lock();
        let _env = EnvRestore::capture();
        std::env::remove_var("DATABASE_URL");
        std::env::set_var("APP_ENV", "development");
        // DATABASE_URL not set, should use default
        let config = DatabaseConfig::from_env().expect("Should succeed in dev");
        assert!(config.url.contains("localhost"));
    }

    #[test]
    fn test_config_from_env_production_missing_url() {
        let _guard = test_env_lock();
        let _env = EnvRestore::capture();
        std::env::remove_var("DATABASE_URL");
        std::env::set_var("APP_ENV", "production");
        let result = DatabaseConfig::from_env();
        assert!(result.is_err());
    }

    #[test]
    fn test_config_from_env_respects_overrides() {
        let _guard = test_env_lock();
        let _env = EnvRestore::capture();
        std::env::set_var("DATABASE_URL", "postgres://user:pass@localhost:5432/test");
        std::env::set_var("DATABASE_MAX_CONNECTIONS", "32");
        std::env::set_var("DATABASE_MIN_CONNECTIONS", "4");
        std::env::set_var("DATABASE_ACQUIRE_TIMEOUT_SECS", "10");
        std::env::set_var("DATABASE_RUN_MIGRATIONS", "false");
        std::env::set_var("APP_ENV", "production");

        let config = DatabaseConfig::from_env().expect("Should succeed");
        assert!(config.url.contains("test"));
        assert_eq!(config.max_connections, 32);
        assert_eq!(config.min_connections, 4);
        assert_eq!(config.acquire_timeout, Duration::from_secs(10));
        assert!(!config.run_migrations);

        // Unset again so the fake credentials do not leak into other tests in this process
        std::env::remove_var("DATABASE_URL");
        std::env::remove_var("DATABASE_MAX_CONNECTIONS");
        std::env::remove_var("DATABASE_MIN_CONNECTIONS");
        std::env::remove_var("DATABASE_ACQUIRE_TIMEOUT_SECS");
        std::env::remove_var("DATABASE_RUN_MIGRATIONS");
        std::env::remove_var("APP_ENV");
    }
}
