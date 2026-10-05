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
    /// Create a new test database with a PostgreSQL container
    pub async fn new() -> Result<Self, Box<dyn std::error::Error>> {
        Self::with_config(TestDbConfig::default()).await
    }

    /// Create a new test database with custom configuration
    pub async fn with_config(config: TestDbConfig) -> Result<Self, Box<dyn std::error::Error>> {
        info!("Connecting to PostgreSQL test database");

        let base_url = std::env::var("DATABASE_URL")
            .unwrap_or_else(|_| "postgres://postgres:postgres@localhost:5432".to_string());

        // Create a unique database name for this test
        let test_db_name = format!(
            "test_db_{}",
            uuid::Uuid::new_v4().to_string().replace('-', "")
        );
        let database_url = format!("{}/{}", base_url, test_db_name);

        info!("Creating test database: {}", test_db_name);

        // Connect to postgres to create the test database
        let postgres_url = base_url.clone();
        let mut retries = 0;
        let postgres_pool = loop {
            match sqlx::postgres::PgPoolOptions::new()
                .max_connections(1)
                .acquire_timeout(Duration::from_secs(5))
                .connect(&postgres_url)
                .await
            {
                Ok(pool) => break pool,
                Err(_) if retries < 30 => {
                    retries += 1;
                    tokio::time::sleep(Duration::from_millis(100)).await;
                }
                Err(e) => return Err(format!("Failed to connect to postgres: {}", e).into()),
            }
        };

        // Create the test database
        sqlx::query(&format!("CREATE DATABASE {}", test_db_name))
            .execute(&postgres_pool)
            .await?;

        drop(postgres_pool);

        // Connect to the test database with explicit timeout
        let pool = sqlx::postgres::PgPoolOptions::new()
            .max_connections(config.max_connections)
            .acquire_timeout(config.connection_timeout)
            .connect(&database_url)
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
        // Clear all tables in reverse dependency order
        sqlx::query("TRUNCATE TABLE events CASCADE")
            .execute(&self.pool)
            .await?;
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
