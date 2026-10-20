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
        // Server URL from environment or use sensible default. Any database path in the URL is
        // ignored: the server is addressed via the `postgres` database to create test databases.
        let base_url = {
            let _env_guard = crate::database::test_env_lock();
            std::env::var("TEST_DATABASE_URL")
                .or_else(|_| std::env::var("DATABASE_URL"))
                .unwrap_or_else(|_| {
                    "postgres://postgres:postgres@localhost:5432/postgres".to_string()
                })
        };
        let mut admin_url = url::Url::parse(&base_url)?;
        admin_url.set_path("/postgres");

        // Each test gets its own database so parallel tests cannot collide on unique keys
        let test_db_name = format!(
            "test_db_{}",
            uuid::Uuid::new_v4().to_string().replace('-', "")
        );
        let mut database_url = admin_url.clone();
        database_url.set_path(&format!("/{}", test_db_name));

        info!("Connecting to test database server");

        // Connect with timeout to prevent hanging indefinitely.
        // SQLx 0.7 doesn't expose per-connection timeout on PgPoolOptions, so we wrap with
        // tokio::time::timeout. The outer timeout respects the configured connection_timeout.
        let mut retries = 0;
        const MAX_RETRIES: u32 = 30;
        const RETRY_DELAY_MS: u64 = 100;

        let admin_pool = loop {
            match tokio::time::timeout(
                config.connection_timeout,
                sqlx::postgres::PgPoolOptions::new()
                    .max_connections(1)
                    .connect(admin_url.as_str()),
            )
            .await
            {
                Ok(Ok(pool)) => {
                    info!("Successfully connected to test database server");
                    break pool;
                }
                _ if retries < MAX_RETRIES => {
                    retries += 1;
                    tokio::time::sleep(Duration::from_millis(RETRY_DELAY_MS)).await;
                }
                Ok(Err(e)) => {
                    return Err(format!(
                        "Failed to connect to database at {}: {}. Set DATABASE_URL environment variable if needed.",
                        admin_url, e
                    )
                    .into());
                }
                Err(_) => {
                    return Err(format!(
                        "Database connection timeout after {} attempts ({}ms). \
                         Database may not be running at {}. Set DATABASE_URL environment variable if needed.",
                        MAX_RETRIES,
                        MAX_RETRIES as u64 * RETRY_DELAY_MS,
                        admin_url
                    )
                    .into());
                }
            }
        };

        info!("Creating test database: {}", test_db_name);
        sqlx::query(&format!("CREATE DATABASE {}", test_db_name))
            .execute(&admin_pool)
            .await?;
        admin_pool.close().await;

        let pool = sqlx::postgres::PgPoolOptions::new()
            .max_connections(config.max_connections)
            .acquire_timeout(config.connection_timeout)
            .connect(database_url.as_str())
            .await?;

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
        // TRUNCATE ... CASCADE also clears every table that references these (participations,
        // group members, messages, ...); sequences are not reset.
        sqlx::query("TRUNCATE TABLE appointment, groups, friendship_requests, pushsubscription, push_notification_log, settings, user_profiles CASCADE")
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

        sqlx::query("INSERT INTO user_profiles (oidc_id, first_name, last_name, email) VALUES ($1, $2, $3, $4)")
            .bind("keycloak-sub-1")
            .bind("Test")
            .bind("User")
            .bind("test@example.com")
            .execute(db.pool())
            .await
            .expect("Failed to insert test user profile");

        let count: (i64,) = sqlx::query_as("SELECT COUNT(*) FROM user_profiles")
            .fetch_one(db.pool())
            .await
            .expect("Failed to count user profiles");
        assert_eq!(count.0, 1);

        db.rollback_all().await.expect("Failed to rollback");

        let count: (i64,) = sqlx::query_as("SELECT COUNT(*) FROM user_profiles")
            .fetch_one(db.pool())
            .await
            .expect("Failed to count user profiles");
        assert_eq!(count.0, 0);
    }
}
