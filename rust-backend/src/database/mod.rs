use sqlx::postgres::PgPoolOptions;
use sqlx::PgPool;
use std::time::Duration;
use tracing::info;

pub mod repository;

/// Database configuration
#[derive(Clone, Debug)]
pub struct DatabaseConfig {
    pub url: String,
    pub max_connections: u32,
    pub min_idle: Option<u32>,
    pub connection_timeout: Duration,
    pub max_lifetime: Duration,
}

impl Default for DatabaseConfig {
    fn default() -> Self {
        Self {
            url: "postgres://chronos:chronos@localhost:5432/chronos".to_string(),
            max_connections: 16, // Conservative for <50 MiB memory target
            min_idle: Some(2),
            connection_timeout: Duration::from_secs(5),
            max_lifetime: Duration::from_secs(1800), // 30 minutes
        }
    }
}

/// Initialize the database connection pool
pub async fn init_pool(config: DatabaseConfig) -> Result<PgPool, sqlx::Error> {
    info!("Initializing database pool: {}", config.url);

    let pool = PgPoolOptions::new()
        .max_connections(config.max_connections)
        .min_idle(config.min_idle)
        .connect_timeout(config.connection_timeout)
        .max_lifetime(config.max_lifetime)
        .connect(&config.url)
        .await?;

    // Run migrations before returning the pool
    run_migrations(&pool).await?;

    info!("Database pool initialized successfully");
    Ok(pool)
}

/// Run database migrations using sqlx::migrate
pub async fn run_migrations(pool: &PgPool) -> Result<(), sqlx::Error> {
    info!("Running database migrations");
    sqlx::migrate!("./migrations").run(pool).await?;
    info!("Migrations completed successfully");
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_default_config() {
        let config = DatabaseConfig::default();
        assert_eq!(config.max_connections, 16);
        assert_eq!(config.min_idle, Some(2));
    }

    #[test]
    fn test_config_memory_efficient() {
        let config = DatabaseConfig::default();
        assert!(
            config.max_connections <= 32,
            "Connection pool too large for memory target"
        );
    }
}
