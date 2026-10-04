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

        Ok(Self {
            url,
            max_connections,
            min_connections,
            acquire_timeout: Duration::from_secs(acquire_timeout_secs),
            max_lifetime: Duration::from_secs(1800),
            run_migrations,
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

#[cfg(test)]
mod tests {
    use super::*;

    struct EnvGuard {
        vars: Vec<(&'static str, Option<String>)>,
    }

    impl EnvGuard {
        fn new(vars: &[&'static str]) -> Self {
            let saved = vars
                .iter()
                .map(|&var| (var, std::env::var(var).ok()))
                .collect();
            Self { vars: saved }
        }
    }

    impl Drop for EnvGuard {
        fn drop(&mut self) {
            for (var, original) in &self.vars {
                match original {
                    Some(val) => std::env::set_var(var, val),
                    None => std::env::remove_var(var),
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
        let _guard = EnvGuard::new(&["APP_ENV", "DATABASE_URL"]);
        std::env::set_var("APP_ENV", "development");
        std::env::remove_var("DATABASE_URL");
        // DATABASE_URL not set, should use default
        let config = DatabaseConfig::from_env().expect("Should succeed in dev");
        assert!(config.url.contains("localhost"));
    }

    #[test]
    fn test_config_from_env_production_missing_url() {
        let _guard = EnvGuard::new(&["APP_ENV", "DATABASE_URL"]);
        std::env::remove_var("DATABASE_URL");
        std::env::set_var("APP_ENV", "production");
        let result = DatabaseConfig::from_env();
        assert!(result.is_err());
    }

    #[test]
    fn test_config_from_env_respects_overrides() {
        let _guard = EnvGuard::new(&[
            "DATABASE_URL",
            "DATABASE_MAX_CONNECTIONS",
            "DATABASE_MIN_CONNECTIONS",
            "DATABASE_ACQUIRE_TIMEOUT_SECS",
            "DATABASE_RUN_MIGRATIONS",
            "APP_ENV",
        ]);
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
    }
}
