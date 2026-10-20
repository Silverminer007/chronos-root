use super::janitor;
use sqlx::{Connection, PgPool, Postgres, Transaction};
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

/// Prefix of every per-test database; the janitor only ever touches names with this prefix
pub const TEST_DB_PREFIX: &str = "test_db_";

/// Prefix of the migrated template the per-test databases are cloned from
const TEMPLATE_PREFIX: &str = "test_template_";

/// Serializes template creation across test binaries and processes
const TEMPLATE_LOCK_KEY: i64 = 0x0063_6872_6f6e_6f73;

/// `CREATE DATABASE ... TEMPLATE` fails with this SQLSTATE while another session uses the template
const OBJECT_IN_USE: &str = "55006";
const CLONE_MAX_ATTEMPTS: u32 = 100;
const CLONE_RETRY_DELAY: Duration = Duration::from_millis(50);

/// Test database container and connection pool. The database is dropped together with the value,
/// also when the test panics.
pub struct TestDb {
    pool: PgPool,
    name: String,
    admin_url: url::Url,
}

fn unix_now() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map_or(0, |d| d.as_secs())
}

fn is_object_in_use(e: &sqlx::Error) -> bool {
    e.as_database_error()
        .and_then(|d| d.code())
        .is_some_and(|c| c == OBJECT_IN_USE)
}

/// Name of the template for the current migrations: changes whenever a migration changes
fn template_name() -> String {
    use std::hash::{Hash, Hasher};
    let mut hasher = std::collections::hash_map::DefaultHasher::new();
    for m in sqlx::migrate!("./migrations").iter() {
        m.version.hash(&mut hasher);
        m.checksum.hash(&mut hasher);
    }
    format!("{TEMPLATE_PREFIX}{:016x}", hasher.finish())
}

async fn database_exists(admin: &mut sqlx::PgConnection, name: &str) -> Result<bool, sqlx::Error> {
    sqlx::query_scalar("SELECT EXISTS (SELECT 1 FROM pg_database WHERE datname = $1)")
        .bind(name)
        .fetch_one(admin)
        .await
}

/// Make sure the migrated template exists. Built once per migration set and reused by every
/// test binary; concurrent builders are serialized with an advisory lock.
async fn ensure_template(
    admin_pool: &PgPool,
    admin_url: &url::Url,
    config: &TestDbConfig,
    template: &str,
) -> Result<(), Box<dyn std::error::Error>> {
    let mut conn = admin_pool.acquire().await?;
    if database_exists(&mut conn, template).await? {
        return Ok(());
    }
    sqlx::query("SELECT pg_advisory_lock($1)")
        .bind(TEMPLATE_LOCK_KEY)
        .execute(&mut *conn)
        .await?;
    let built = build_template(&mut conn, admin_url, config, template).await;
    sqlx::query("SELECT pg_advisory_unlock($1)")
        .bind(TEMPLATE_LOCK_KEY)
        .execute(&mut *conn)
        .await?;
    built
}

async fn build_template(
    conn: &mut sqlx::PgConnection,
    admin_url: &url::Url,
    config: &TestDbConfig,
    template: &str,
) -> Result<(), Box<dyn std::error::Error>> {
    if database_exists(conn, template).await? {
        return Ok(());
    }
    // Built under a janitor-visible name and renamed at the end, so a crashed build is never
    // mistaken for a complete template and gets cleaned up
    let building = new_db_name();
    sqlx::query(&format!("CREATE DATABASE \"{building}\""))
        .execute(&mut *conn)
        .await?;
    let mut url = admin_url.clone();
    url.set_path(&format!("/{building}"));
    let pool = sqlx::postgres::PgPoolOptions::new()
        .max_connections(1)
        .acquire_timeout(config.connection_timeout)
        .connect(url.as_str())
        .await?;
    info!("Running migrations on test template {}", template);
    let migrated = sqlx::migrate!("./migrations").run(&pool).await;
    pool.close().await;
    migrated?;
    sqlx::query(&format!(
        "ALTER DATABASE \"{building}\" RENAME TO \"{template}\""
    ))
    .execute(conn)
    .await?;
    Ok(())
}

fn new_db_name() -> String {
    format!(
        "{TEST_DB_PREFIX}{}_{}",
        unix_now(),
        uuid::Uuid::new_v4().simple()
    )
}

async fn clone_template(
    admin_pool: &PgPool,
    template: &str,
    name: &str,
) -> Result<(), sqlx::Error> {
    let mut attempt = 1;
    loop {
        match sqlx::query(&format!(
            "CREATE DATABASE \"{name}\" TEMPLATE \"{template}\""
        ))
        .execute(admin_pool)
        .await
        {
            Err(e) if is_object_in_use(&e) && attempt < CLONE_MAX_ATTEMPTS => {
                attempt += 1;
                tokio::time::sleep(CLONE_RETRY_DELAY).await;
            }
            other => return other.map(|_| ()),
        }
    }
}

/// Drop stale databases of earlier aborted runs, once per test process
async fn run_janitor_once(admin_pool: &PgPool) {
    static DONE: tokio::sync::OnceCell<()> = tokio::sync::OnceCell::const_new();
    DONE.get_or_init(|| async {
        match janitor::drop_stale_databases(
            admin_pool,
            TEST_DB_PREFIX,
            unix_now(),
            janitor::STALE_AFTER,
            janitor::MAX_DROPS_PER_RUN,
        )
        .await
        {
            Ok(dropped) if !dropped.is_empty() => {
                info!("Dropped {} stale test databases", dropped.len())
            }
            Ok(_) => {}
            Err(e) => tracing::warn!("Test database janitor failed: {}", e),
        }
    })
    .await;
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
        let test_db_name = new_db_name();
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

        run_janitor_once(&admin_pool).await;
        let template = template_name();
        ensure_template(&admin_pool, &admin_url, &config, &template).await?;

        info!("Creating test database: {}", test_db_name);
        clone_template(&admin_pool, &template, &test_db_name).await?;
        admin_pool.close().await;

        let pool = match sqlx::postgres::PgPoolOptions::new()
            .max_connections(config.max_connections)
            .acquire_timeout(config.connection_timeout)
            .connect(database_url.as_str())
            .await
        {
            Ok(pool) => pool,
            Err(e) => {
                drop_database_blocking(&admin_url, &test_db_name);
                return Err(e.into());
            }
        };
        Ok(TestDb {
            pool,
            name: test_db_name,
            admin_url,
        })
    }

    /// Name of this test's database
    pub fn name(&self) -> &str {
        &self.name
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

/// Drops the database, terminating sessions that clones of the pool still hold. This is called
/// from `Drop`, which is synchronous and may run inside a runtime (or during a panic), so the work
/// happens on its own thread with its own runtime. Leftovers are collected by the next run's janitor.
fn drop_database_blocking(admin_url: &url::Url, name: &str) {
    let (admin_url, owned_name) = (admin_url.to_string(), name.to_string());
    let dropped = std::thread::spawn(move || {
        tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
            .map_err(|e| e.to_string())?
            .block_on(async {
                let mut conn = sqlx::PgConnection::connect(&admin_url)
                    .await
                    .map_err(|e| e.to_string())?;
                sqlx::query(&format!(
                    "DROP DATABASE IF EXISTS \"{owned_name}\" WITH (FORCE)"
                ))
                .execute(&mut conn)
                .await
                .map_err(|e| e.to_string())?;
                Ok::<(), String>(())
            })
    })
    .join();
    if !matches!(dropped, Ok(Ok(()))) {
        eprintln!("Could not drop test database {name}: {dropped:?}");
    }
}

impl Drop for TestDb {
    fn drop(&mut self) {
        drop_database_blocking(&self.admin_url, &self.name);
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
