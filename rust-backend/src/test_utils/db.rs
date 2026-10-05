use sqlx::{PgPool, Postgres, Transaction};
use std::time::Duration;
use tracing::info;

/// Configuration for test database
#[derive(Clone, Debug)]
pub struct TestDbConfig {
    pub max_connections: u32,
    pub connection_timeout: Duration,
}

impl Default for TestDbConfig {
    fn default() -> Self {
        Self {
            max_connections: 20,
            connection_timeout: Duration::from_secs(30),
        }
    }
}

/// Test database container and connection pool
pub struct TestDb {
    pool: PgPool,
}

impl TestDb {
    /// Create a new test database connection
    pub async fn new() -> Result<Self, Box<dyn std::error::Error>> {
        Self::with_config(TestDbConfig::default()).await
    }

    /// Create a new test database connection with custom configuration
    pub async fn with_config(config: TestDbConfig) -> Result<Self, Box<dyn std::error::Error>> {
        // Get database URL from environment or use sensible default
        let database_url = std::env::var("DATABASE_URL")
            .unwrap_or_else(|_| "postgres://postgres:postgres@localhost:5432/postgres".to_string());

        info!("Connecting to test database");

        // Connect with timeout to prevent hanging indefinitely.
        // SQLx 0.7 doesn't expose per-connection timeout on PgPoolOptions, so we wrap with
        // tokio::time::timeout. The outer timeout respects the configured connection_timeout.
        let mut retries = 0;
        const MAX_RETRIES: u32 = 30;
        const RETRY_DELAY_MS: u64 = 100;

        let pool = loop {
            match tokio::time::timeout(
                config.connection_timeout,
                sqlx::postgres::PgPoolOptions::new()
                    .max_connections(config.max_connections)
                    .connect(&database_url),
            )
            .await
            {
                Ok(Ok(pool)) => {
                    info!("Successfully connected to test database");
                    break pool;
                }
                _ if retries < MAX_RETRIES => {
                    retries += 1;
                    tokio::time::sleep(Duration::from_millis(RETRY_DELAY_MS)).await;
                }
                Ok(Err(e)) => {
                    return Err(format!(
                        "Failed to connect to database at {}: {}. Set DATABASE_URL environment variable if needed.",
                        database_url, e
                    )
                    .into());
                }
                Err(_) => {
                    return Err(format!(
                        "Database connection timeout after {} attempts ({}ms). \
                         Database may not be running at {}. Set DATABASE_URL environment variable if needed.",
                        MAX_RETRIES,
                        MAX_RETRIES as u64 * RETRY_DELAY_MS,
                        database_url
                    )
                    .into());
                }
            }
        };

        info!("Running migrations on test database");
        sqlx::migrate!("./migrations").run(&pool).await?;

        Ok(TestDb { pool })
    }

    /// Get the connection pool
    pub fn pool(&self) -> &PgPool {
        &self.pool
    }

    /// Begin a transaction
    pub async fn begin(&self) -> Result<Transaction<'_, Postgres>, sqlx::Error> {
        self.pool.begin().await
    }

    /// Rollback all changes after test (useful for transaction-scoped tests)
    pub async fn rollback_all(&self) -> Result<(), sqlx::Error> {
        // Clear all tables in reverse dependency order
        sqlx::query("TRUNCATE TABLE appointment_participants CASCADE")
            .execute(&self.pool)
            .await?;
        sqlx::query("TRUNCATE TABLE appointments CASCADE")
            .execute(&self.pool)
            .await?;
        sqlx::query("TRUNCATE TABLE groups CASCADE")
            .execute(&self.pool)
            .await?;
        sqlx::query("TRUNCATE TABLE users CASCADE")
            .execute(&self.pool)
            .await?;

        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    #[ignore]
    async fn test_database_connection() {
        let db = TestDb::new().await.expect("Failed to create test database");

        // Verify we can query the database
        let result: (i32,) = sqlx::query_as("SELECT 1")
            .fetch_one(db.pool())
            .await
            .expect("Failed to query test database");

        assert_eq!(result.0, 1);
    }

    #[tokio::test]
    #[ignore]
    async fn test_rollback_all() {
        let db = TestDb::new().await.expect("Failed to create test database");

        // Insert a test user
        sqlx::query(
            "INSERT INTO users (id, keycloak_id, email, first_name, last_name) VALUES ($1, $2, $3, $4, $5)"
        )
        .bind(uuid::Uuid::new_v4())
        .bind("keycloak_id_1")
        .bind("test@example.com")
        .bind("Test")
        .bind("User")
        .execute(db.pool())
        .await
        .expect("Failed to insert test user");

        // Verify user exists
        let count: (i64,) = sqlx::query_as("SELECT COUNT(*) FROM users")
            .fetch_one(db.pool())
            .await
            .expect("Failed to count users");
        assert_eq!(count.0, 1);

        // Rollback
        db.rollback_all().await.expect("Failed to rollback");

        // Verify user is gone
        let count: (i64,) = sqlx::query_as("SELECT COUNT(*) FROM users")
            .fetch_one(db.pool())
            .await
            .expect("Failed to count users");
        assert_eq!(count.0, 0);
    }
}
