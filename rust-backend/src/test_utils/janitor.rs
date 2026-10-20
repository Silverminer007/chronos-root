//! Janitor for test databases that earlier runs left behind (killed test binary, SIGKILL, CI abort).
//!
//! PostgreSQL does not record when a database was created, so the creation time is part of the
//! name: `<prefix><unix seconds>_<uuid>`. Names without that shape are never touched.

use sqlx::PgPool;
use std::time::Duration;

/// Databases older than this are considered abandoned
pub const STALE_AFTER: Duration = Duration::from_secs(60 * 60);

/// Upper bound per run: every `DROP DATABASE` waits for a checkpoint, so a large backlog must not
/// stall the first test of a run
pub const MAX_DROPS_PER_RUN: usize = 20;

/// Creation time (unix seconds) encoded in `name`, or `None` if `name` is not `<prefix><secs>_<rest>`
pub fn created_at(name: &str, prefix: &str) -> Option<u64> {
    let (secs, _) = name.strip_prefix(prefix)?.split_once('_')?;
    secs.parse().ok()
}

/// Drop databases named `<prefix><secs>_*` that are older than `max_age` at `now_secs` and have no
/// connection. Returns the dropped names. All other databases are left alone; a database that gets
/// a connection while being dropped is skipped.
pub async fn drop_stale_databases(
    admin: &PgPool,
    prefix: &str,
    now_secs: u64,
    max_age: Duration,
    limit: usize,
) -> Result<Vec<String>, sqlx::Error> {
    // Anything starting with the prefix is a candidate; the age check happens on the parsed name
    let candidates: Vec<(String,)> = sqlx::query_as(
        "SELECT d.datname::text FROM pg_database d
         WHERE NOT d.datistemplate AND left(d.datname, length($1)) = $1
           AND NOT EXISTS (SELECT 1 FROM pg_stat_activity a WHERE a.datname = d.datname)",
    )
    .bind(prefix)
    .fetch_all(admin)
    .await?;

    let mut dropped = Vec::new();
    for (name,) in candidates {
        let stale = created_at(&name, prefix)
            .is_some_and(|secs| now_secs.saturating_sub(secs) > max_age.as_secs());
        if !stale || dropped.len() >= limit {
            continue;
        }
        // No FORCE: if a session connected in the meantime the database is alive, keep it
        if sqlx::query(&format!("DROP DATABASE IF EXISTS \"{name}\""))
            .execute(admin)
            .await
            .is_ok()
        {
            dropped.push(name);
        }
    }
    Ok(dropped)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_creation_time_from_name() {
        assert_eq!(
            created_at("test_db_1700000000_abc", "test_db_"),
            Some(1_700_000_000)
        );
    }

    #[test]
    fn ignores_names_without_timestamp() {
        // Legacy name (plain uuid), foreign database, non-numeric timestamp
        assert_eq!(created_at("test_db_0123456789abcdef", "test_db_"), None);
        assert_eq!(created_at("chronos", "test_db_"), None);
        assert_eq!(created_at("test_db_x_abc", "test_db_"), None);
    }
}
