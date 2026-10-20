//! Leader election for the reminder scheduler with a PostgreSQL session-level advisory lock
//! (ADR-2026-10-20-022). Java uses a Kubernetes lease (`LeaderElectionService`); the Rust backend
//! needs no cluster API, only the database every replica shares.
//!
//! The lock is bound to one dedicated database connection. It is released when that connection
//! ends (resign, crash, network loss), so a dead leader can never block the others.

use sqlx::{Connection, PgConnection, PgPool};
use tokio::sync::Mutex;
use tracing::{info, warn};

/// Environment variable (Java: `scheduler.leader-election.enabled`, default `true`)
pub const ENABLED_ENV: &str = "LEADER_ELECTION_ENABLED";

/// Key of the advisory lock (the ASCII bytes of "chronos\0" read as a big-endian i64)
pub const SCHEDULER_LOCK_KEY: i64 = 0x6368_726f_6e6f_7300;

pub struct LeaderElection {
    /// `None`: election disabled, the instance is always the leader
    pool: Option<PgPool>,
    /// Connection that holds the advisory lock while this instance is the leader
    lock_connection: Mutex<Option<PgConnection>>,
}

impl LeaderElection {
    pub fn new(pool: PgPool) -> Self {
        Self {
            pool: Some(pool),
            lock_connection: Mutex::new(None),
        }
    }

    /// Always the leader, no database involved
    pub fn disabled() -> Self {
        Self {
            pool: None,
            lock_connection: Mutex::new(None),
        }
    }

    /// Reads `LEADER_ELECTION_ENABLED` (default enabled)
    pub fn from_env(pool: PgPool) -> Self {
        let value = std::env::var(ENABLED_ENV).ok();
        if parse_enabled(value.as_deref()) {
            Self::new(pool)
        } else {
            info!("Leader election is disabled; all instances will run scheduled tasks");
            Self::disabled()
        }
    }

    /// True while this instance holds leadership; tries to acquire it when it does not.
    /// A lost database connection means lost leadership.
    pub async fn is_leader(&self) -> bool {
        let Some(pool) = &self.pool else {
            return true;
        };
        let mut held = self.lock_connection.lock().await;
        if let Some(connection) = held.as_mut() {
            if connection.ping().await.is_ok() {
                return true;
            }
            warn!("Lost scheduler leadership: lock connection is gone");
            *held = None;
        }
        match try_acquire(pool).await {
            Ok(Some(connection)) => {
                info!("Acquired scheduler leadership");
                *held = Some(connection);
                true
            }
            Ok(None) => false,
            Err(e) => {
                warn!("Leader election failed: {}", e);
                false
            }
        }
    }

    /// Gives up leadership so another instance can take over immediately
    pub async fn resign(&self) {
        if let Some(connection) = self.lock_connection.lock().await.take() {
            // closing the session releases the advisory lock
            if let Err(e) = connection.close().await {
                warn!("Closing the leader lock connection failed: {}", e);
            }
            info!("Resigned scheduler leadership");
        }
    }
}

async fn try_acquire(pool: &PgPool) -> Result<Option<PgConnection>, sqlx::Error> {
    // detached: the connection must never go back to the pool while it holds the lock
    let mut connection = pool.acquire().await?.detach();
    let acquired: bool = sqlx::query_scalar("SELECT pg_try_advisory_lock($1)")
        .bind(SCHEDULER_LOCK_KEY)
        .fetch_one(&mut connection)
        .await?;
    if acquired {
        Ok(Some(connection))
    } else {
        let _ = connection.close().await;
        Ok(None)
    }
}

/// MicroProfile-style boolean: `true`, `1`, `yes`, `y`, `on` enable; anything else disables.
/// Unset means enabled.
fn parse_enabled(value: Option<&str>) -> bool {
    match value {
        None => true,
        Some(v) => matches!(
            v.trim().to_ascii_lowercase().as_str(),
            "true" | "1" | "yes" | "y" | "on"
        ),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn election_is_enabled_by_default() {
        assert!(parse_enabled(None));
        assert!(parse_enabled(Some("true")));
        assert!(parse_enabled(Some("TRUE")));
    }

    #[test]
    fn election_can_be_disabled() {
        assert!(!parse_enabled(Some("false")));
        assert!(!parse_enabled(Some("0")));
        assert!(!parse_enabled(Some("")));
    }

    #[tokio::test]
    async fn disabled_election_is_always_leader() {
        let election = LeaderElection::disabled();
        assert!(election.is_leader().await);
        election.resign().await;
        assert!(election.is_leader().await);
    }
}
