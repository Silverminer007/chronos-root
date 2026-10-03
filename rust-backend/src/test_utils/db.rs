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
            max_connections: 5,
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
        info!("Setting up test database");

        // Use DATABASE_URL env var if set, otherwise create a unique test database
        let database_url = match std::env::var("DATABASE_URL") {
            Ok(url) => url,
            Err(_) => {
                // Generate a unique test database name to isolate tests
                let test_id = uuid::Uuid::new_v4().to_string().replace('-', "_");
                let db_name = format!("test_db_{}", &test_id[..12]);

                // Connect to postgres server to create the test database
                let admin_url = "postgres://postgres:postgres@localhost:5432/postgres";
                if let Ok(admin_pool) = try_connect(admin_url, &config).await {
                    let create_db_query =
                        format!("CREATE DATABASE {} WITH TEMPLATE chronos_test;", db_name);
                    if let Ok(_) = sqlx::query(&create_db_query).execute(&admin_pool).await {
                        info!("Created test database: {}", db_name);
                    }
                }

                format!("postgres://postgres:postgres@localhost:5432/{}", db_name)
            }
        };

        info!("Attempting to connect to: {}", &database_url);

        // Wait for database to be ready with multiple retries
        let pool = try_connect_with_retries(&database_url, &config, 120).await?;

        info!("Connected to test database");

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

async fn try_connect(
    database_url: &str,
    config: &TestDbConfig,
) -> Result<PgPool, Box<dyn std::error::Error>> {
    sqlx::postgres::PgPoolOptions::new()
        .max_connections(config.max_connections)
        .acquire_timeout(config.connection_timeout)
        .connect(database_url)
        .await
        .map_err(|e| Box::new(e) as Box<dyn std::error::Error>)
}

async fn try_connect_with_retries(
    database_url: &str,
    config: &TestDbConfig,
    max_retries: u32,
) -> Result<PgPool, Box<dyn std::error::Error>> {
    let mut retries = 0;
    loop {
        match try_connect(database_url, config).await {
            Ok(pool) => return Ok(pool),
            Err(_) if retries < max_retries => {
                retries += 1;
                tokio::time::sleep(Duration::from_millis(100)).await;
            }
            Err(e) => return Err(e),
        }
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
