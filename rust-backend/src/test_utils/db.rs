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

        #[cfg(test)]
        let base_url = test_support::get_postgres_url().await?;
        #[cfg(not(test))]
        let base_url = std::env::var("DATABASE_URL")?;

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
                .connect(&postgres_url)
                .await
            {
                Ok(pool) => break pool,
                Err(_) if retries < 10 => {
                    retries += 1;
                    tokio::time::sleep(Duration::from_millis(100)).await;
                }
                Err(e) => return Err(Box::new(e)),
            }
        };

        // Create the test database
        sqlx::query(&format!("CREATE DATABASE {}", test_db_name))
            .execute(&postgres_pool)
            .await?;

        drop(postgres_pool);

        // Connect to the test database
        let pool = sqlx::postgres::PgPoolOptions::new()
            .max_connections(config.max_connections)
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
mod test_support {
    use super::*;

    /// Get the PostgreSQL connection URL
    pub async fn get_postgres_url() -> Result<String, Box<dyn std::error::Error>> {
        // Check if DATABASE_URL is set (e.g., in CI with a running database)
        if let Ok(url) = std::env::var("DATABASE_URL") {
            return Ok(url);
        }

        // Try localhost with a short timeout (for local development)
        let localhost_url = "postgres://postgres:postgres@localhost:5432";
        match tokio::time::timeout(
            Duration::from_secs(3),
            sqlx::postgres::PgPoolOptions::new()
                .max_connections(1)
                .connect(localhost_url),
        )
        .await
        {
            Ok(Ok(_)) => {
                info!("Connected to localhost PostgreSQL");
                Ok(localhost_url.to_string())
            }
            _ => Err("No PostgreSQL database available. Set DATABASE_URL or ensure localhost:5432 is running with postgres:postgres credentials".into()),
        }
    }
}
