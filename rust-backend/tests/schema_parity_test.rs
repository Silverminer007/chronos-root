//! The Rust schema must be identical to the final schema of the Java Flyway migrations
//! (ADR-2026-10-12-014). Both sides are described by `schema/snapshot.sql`; the Java side is a
//! checked-in snapshot (`schema/java_schema_expected.txt`) that is itself re-derived from the
//! Flyway SQL files in `../backend` by one of the tests below, so it cannot drift.
//!
//! Needs Docker (Testcontainers). Not ignored: a migration that does not apply to an empty
//! database fails the build.

use sqlx::postgres::PgPoolOptions;
use sqlx::{Executor, PgPool};
use std::collections::BTreeSet;
use std::path::Path;
use testcontainers::runners::AsyncRunner;
use testcontainers_modules::postgres::Postgres;

type AnyError = Box<dyn std::error::Error + Send + Sync>;

const SNAPSHOT_SQL: &str = include_str!("../schema/snapshot.sql");
const JAVA_EXPECTED: &str = include_str!("../schema/java_schema_expected.txt");

async fn connect(port: u16, database: &str) -> Result<PgPool, AnyError> {
    Ok(PgPoolOptions::new()
        .max_connections(2)
        .connect(&format!(
            "postgres://postgres:postgres@127.0.0.1:{}/{}",
            port, database
        ))
        .await?)
}

async fn snapshot(pool: &PgPool) -> Result<BTreeSet<String>, AnyError> {
    let lines: Vec<String> = sqlx::query_scalar(SNAPSHOT_SQL).fetch_all(pool).await?;
    Ok(lines.into_iter().collect())
}

fn expected() -> BTreeSet<String> {
    JAVA_EXPECTED.lines().map(str::to_string).collect()
}

fn assert_same(what: &str, actual: &BTreeSet<String>, expected: &BTreeSet<String>) {
    let missing: Vec<_> = expected.difference(actual).collect();
    let extra: Vec<_> = actual.difference(expected).collect();
    assert!(
        missing.is_empty() && extra.is_empty(),
        "{} differs from the expected schema.\nmissing:\n  {}\nunexpected:\n  {}",
        what,
        missing
            .iter()
            .map(|s| s.as_str())
            .collect::<Vec<_>>()
            .join("\n  "),
        extra
            .iter()
            .map(|s| s.as_str())
            .collect::<Vec<_>>()
            .join("\n  "),
    );
}

#[tokio::test]
async fn rust_migrations_produce_the_java_schema() -> Result<(), AnyError> {
    let container = Postgres::default().start().await;
    let port = container.get_host_port_ipv4(5432).await;
    let pool = connect(port, "postgres").await?;

    // Applies ALL migrations to an empty database; fails if any of them does not apply.
    sqlx::migrate!("./migrations").run(&pool).await?;

    assert_same("Rust migrations", &snapshot(&pool).await?, &expected());
    Ok(())
}

#[tokio::test]
async fn checked_in_snapshot_matches_java_flyway_migrations() -> Result<(), AnyError> {
    let dir =
        Path::new(env!("CARGO_MANIFEST_DIR")).join("../backend/src/main/resources/db/migration");
    if !dir.exists() {
        eprintln!("skipping: {} not available", dir.display());
        return Ok(());
    }

    let mut files: Vec<_> = std::fs::read_dir(&dir)?
        .filter_map(|e| e.ok().map(|e| e.path()))
        .filter(|p| p.extension().is_some_and(|e| e == "sql"))
        .collect();
    // V1.0.0 < V1.1.0 < ... < V2.1.0: compare the numeric version parts
    files.sort_by_key(|p| {
        let name = p.file_name().unwrap().to_string_lossy().to_string();
        let version = name
            .trim_start_matches('V')
            .split("__")
            .next()
            .unwrap()
            .to_string();
        version
            .split('.')
            .map(|n| n.parse::<u32>().unwrap())
            .collect::<Vec<_>>()
    });
    assert!(
        !files.is_empty(),
        "no Flyway migrations found in {}",
        dir.display()
    );

    let container = Postgres::default().start().await;
    let port = container.get_host_port_ipv4(5432).await;
    let pool = connect(port, "postgres").await?;
    for file in files {
        let sql = std::fs::read_to_string(&file)?;
        pool.execute(sql.as_str())
            .await
            .map_err(|e| format!("{}: {}", file.display(), e))?;
    }

    // "missing"/"unexpected" are relative to the schema the Flyway files really produce
    assert_same(
        "schema/java_schema_expected.txt",
        &expected(),
        &snapshot(&pool).await?,
    );
    Ok(())
}

#[test]
fn baseline_is_the_only_schema_migration_and_rust_only_tables_are_separate() {
    let dir = Path::new(env!("CARGO_MANIFEST_DIR")).join("migrations");
    let mut names: Vec<String> = std::fs::read_dir(dir)
        .unwrap()
        .filter_map(|e| e.ok())
        .map(|e| e.file_name().to_string_lossy().to_string())
        .collect();
    names.sort();

    assert_eq!(
        names,
        vec!["001_baseline.sql", "002_create_events_table.sql"],
        "migrations: one baseline with the Java schema, Rust-only tables as further migrations"
    );
}

#[tokio::test]
async fn ids_are_bigint_and_users_are_oidc_strings() -> Result<(), AnyError> {
    let container = Postgres::default().start().await;
    let port = container.get_host_port_ipv4(5432).await;
    let pool = connect(port, "postgres").await?;
    sqlx::migrate!("./migrations").run(&pool).await?;

    let uuid_columns: Vec<String> = sqlx::query_scalar(
        "SELECT table_name || '.' || column_name FROM information_schema.columns \
         WHERE table_schema = 'public' AND data_type = 'uuid'",
    )
    .fetch_all(&pool)
    .await?;
    assert!(
        uuid_columns.is_empty(),
        "uuid columns left: {:?}",
        uuid_columns
    );

    let users_table: Option<String> =
        sqlx::query_scalar("SELECT to_regclass('public.users')::text")
            .fetch_one(&pool)
            .await?;
    assert!(users_table.is_none(), "there must be no users table");
    Ok(())
}
