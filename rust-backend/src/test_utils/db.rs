use sqlx::{PgPool, Postgres, Transaction};
use std::sync::Arc;
use std::time::Duration;
use testcontainers::clients::Cli;
use testcontainers::core::ContainerAsync;
use testcontainers_modules::postgres::Postgres as PostgresImage;
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
            connection_timeout: Duration::from_secs(10),
        }
    }
}

/// Test database container and connection pool
pub struct TestDb {
    pool: PgPool,
    // Keeps the container alive for the duration of the test
    #[allow(dead_code)]
    container: Arc<ContainerAsync<PostgresImage>>,
}

impl TestDb {
    /// Create a new test database with a PostgreSQL container
    pub async fn new() -> Result<Self, Box<dyn std::error::Error>> {
        Self::with_config(TestDbConfig::default()).await
    }

    /// Create a new test database with custom configuration
    pub async fn with_config(config: TestDbConfig) -> Result<Self, Box<dyn std::error::Error>> {
        info!("Starting PostgreSQL test container");

        let docker = Cli::default();
        let postgres_image = PostgresImage::default()
            .with_db_name("chronos_test")
            .with_username("chronos")
            .with_password("chronos");

        let container = docker.run(postgres_image);
        let container = Arc::new(container);

        let database_url = format!(
            "postgres://chronos:chronos@127.0.0.1:{}/chronos_test",
            container.get_host_port_ipv4(5432)
        );

        info!("Connecting to test database: {}", database_url);

        // Wait for database to be ready
        let mut retries = 0;
        let pool = loop {
            match sqlx::PgPoolOptions::new()
                .max_connections(config.max_connections)
                .connect_timeout(config.connection_timeout)
                .connect(&database_url)
                .await
            {
                Ok(pool) => break pool,
                Err(_) if retries < 30 => {
                    retries += 1;
                    tokio::time::sleep(Duration::from_millis(100)).await;
                }
                Err(e) => return Err(Box::new(e)),
            }
        };

        info!("Running migrations on test database");
        sqlx::migrate!("./migrations").run(&pool).await?;

        Ok(TestDb { pool, container })
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
        let db = TestDb::new()
            .await
            .expect("Failed to create test database");

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
        let db = TestDb::new()
            .await
            .expect("Failed to create test database");

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
        db.rollback_all()
            .await
            .expect("Failed to rollback");

        // Verify user is gone
        let count: (i64,) = sqlx::query_as("SELECT COUNT(*) FROM users")
            .fetch_one(db.pool())
            .await
            .expect("Failed to count users");
        assert_eq!(count.0, 0);
    }
}
