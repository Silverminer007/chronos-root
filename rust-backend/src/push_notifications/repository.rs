use crate::database::repository::{Repository, RepositoryError};
use crate::push_notifications::models::PushSubscription;
use async_trait::async_trait;
use sqlx::PgPool;

/// Columns of the Java-compatible `pushsubscription` table (ids from `pushsubscription_seq`)
const COLUMNS: &str = "id, user_oidcid AS user_id, endpoint, p256dh, auth";

/// PushSubscriptionRepository provides data access for PushSubscription entities
pub struct PushSubscriptionRepository {
    pool: PgPool,
}

impl PushSubscriptionRepository {
    /// Create a new PushSubscriptionRepository
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }

    /// Find subscriptions by user (OIDC subject)
    pub async fn find_by_user_id(
        &self,
        user_id: &str,
    ) -> Result<Vec<PushSubscription>, RepositoryError> {
        sqlx::query_as::<_, PushSubscription>(&format!(
            "SELECT {} FROM pushsubscription WHERE user_oidcid = $1 ORDER BY id",
            COLUMNS
        ))
        .bind(user_id)
        .fetch_all(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Whether any user has a subscription for this endpoint
    pub async fn exists_by_endpoint(&self, endpoint: &str) -> Result<bool, RepositoryError> {
        sqlx::query_scalar::<_, bool>(
            "SELECT EXISTS (SELECT 1 FROM pushsubscription WHERE endpoint = $1)",
        )
        .bind(endpoint)
        .fetch_one(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Find subscription by endpoint
    pub async fn find_by_endpoint(
        &self,
        endpoint: &str,
    ) -> Result<Option<PushSubscription>, RepositoryError> {
        sqlx::query_as::<_, PushSubscription>(&format!(
            "SELECT {} FROM pushsubscription WHERE endpoint = $1 ORDER BY id LIMIT 1",
            COLUMNS
        ))
        .bind(endpoint)
        .fetch_optional(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Delete every subscription with this endpoint (Java: `PushSubscriptionService#deleteByEndpoint`)
    pub async fn delete_by_endpoint(&self, endpoint: &str) -> Result<u64, RepositoryError> {
        sqlx::query("DELETE FROM pushsubscription WHERE endpoint = $1")
            .bind(endpoint)
            .execute(&self.pool)
            .await
            .map(|result| result.rows_affected())
            .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Java: `PushSubscriptionService#saveSubscription`. An endpoint belongs to one browser, so a
    /// known endpoint (of any user) is replaced. The advisory lock serializes concurrent saves of
    /// the same endpoint.
    pub async fn replace_for_user(
        &self,
        user_id: &str,
        endpoint: &str,
        p256dh: &str,
        auth: &str,
    ) -> Result<(), RepositoryError> {
        let db_error = |e: sqlx::Error| RepositoryError::DatabaseError(e.to_string());
        let mut tx = self.pool.begin().await.map_err(db_error)?;
        sqlx::query("SELECT pg_advisory_xact_lock(hashtextextended($1, 0))")
            .bind(format!("pushsubscription-endpoint:{endpoint}"))
            .execute(&mut *tx)
            .await
            .map_err(db_error)?;
        sqlx::query("DELETE FROM pushsubscription WHERE endpoint = $1")
            .bind(endpoint)
            .execute(&mut *tx)
            .await
            .map_err(db_error)?;
        sqlx::query(
            "INSERT INTO pushsubscription (id, user_oidcid, endpoint, p256dh, auth)
             VALUES (nextval('pushsubscription_seq'), $1, $2, $3, $4)",
        )
        .bind(user_id)
        .bind(endpoint)
        .bind(p256dh)
        .bind(auth)
        .execute(&mut *tx)
        .await
        .map_err(db_error)?;
        tx.commit().await.map_err(db_error)
    }

    /// Delete subscription by user and endpoint
    pub async fn delete_by_user_and_endpoint(
        &self,
        user_id: &str,
        endpoint: &str,
    ) -> Result<bool, RepositoryError> {
        let result =
            sqlx::query("DELETE FROM pushsubscription WHERE user_oidcid = $1 AND endpoint = $2")
                .bind(user_id)
                .bind(endpoint)
                .execute(&self.pool)
                .await
                .map_err(|e| RepositoryError::DatabaseError(e.to_string()))?;

        Ok(result.rows_affected() > 0)
    }
}

/// One row of `push_notification_log`: the outcome of a single send attempt
#[derive(Debug, Clone)]
pub struct PushNotificationLogEntry<'a> {
    pub user_id: &'a str,
    pub notification_type: Option<&'a str>,
    pub payload: &'a str,
    pub endpoint: &'a str,
    pub http_status_code: Option<i32>,
    pub success: bool,
    pub error_message: Option<&'a str>,
}

/// Writes `push_notification_log` (Java: `PushNotificationLogRepository#log`)
pub struct PushNotificationLogRepository {
    pool: PgPool,
}

impl PushNotificationLogRepository {
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }

    pub async fn log(&self, entry: &PushNotificationLogEntry<'_>) -> Result<(), RepositoryError> {
        sqlx::query(
            "INSERT INTO push_notification_log
                 (id, user_oidcid, notification_type, payload, endpoint, http_status_code,
                  success, error_message, created_at)
             VALUES (nextval('push_notification_log_seq'), $1, $2, $3, $4, $5, $6, $7, NOW())",
        )
        .bind(entry.user_id)
        .bind(entry.notification_type)
        .bind(entry.payload)
        .bind(entry.endpoint)
        .bind(entry.http_status_code)
        .bind(entry.success)
        .bind(entry.error_message)
        .execute(&self.pool)
        .await
        .map(|_| ())
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }
}

#[async_trait]
impl Repository<PushSubscription> for PushSubscriptionRepository {
    /// Find a subscription by its ID
    async fn find_by_id(&self, id: i64) -> Result<Option<PushSubscription>, RepositoryError> {
        sqlx::query_as::<_, PushSubscription>(&format!(
            "SELECT {} FROM pushsubscription WHERE id = $1",
            COLUMNS
        ))
        .bind(id)
        .fetch_optional(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Find all subscriptions
    async fn find_all(&self) -> Result<Vec<PushSubscription>, RepositoryError> {
        sqlx::query_as::<_, PushSubscription>(&format!(
            "SELECT {} FROM pushsubscription ORDER BY id",
            COLUMNS
        ))
        .fetch_all(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Create a new subscription. The Java schema has no unique constraint on
    /// (user, endpoint), so duplicates are rejected here. The check is race-safe: a
    /// transaction-scoped advisory lock keyed on (user, endpoint) serializes concurrent
    /// creates for the same pair, and the insert runs in a statement that starts after the
    /// lock is granted, so it sees the row a concurrent winner has committed. The entity's
    /// `id` is ignored (the id comes from `pushsubscription_seq`).
    async fn create(&self, entity: PushSubscription) -> Result<PushSubscription, RepositoryError> {
        let db_error = |e: sqlx::Error| RepositoryError::DatabaseError(e.to_string());
        let mut tx = self.pool.begin().await.map_err(db_error)?;

        sqlx::query("SELECT pg_advisory_xact_lock(hashtextextended($1, 0))")
            .bind(format!(
                "pushsubscription:{}:{}",
                entity.user_id, entity.endpoint
            ))
            .execute(&mut *tx)
            .await
            .map_err(db_error)?;

        let created = sqlx::query_as::<_, PushSubscription>(&format!(
            "INSERT INTO pushsubscription (id, user_oidcid, endpoint, p256dh, auth)
             SELECT nextval('pushsubscription_seq'), $1, $2, $3, $4
             WHERE NOT EXISTS (
                 SELECT 1 FROM pushsubscription WHERE user_oidcid = $1 AND endpoint = $2
             )
             RETURNING {}",
            COLUMNS
        ))
        .bind(&entity.user_id)
        .bind(&entity.endpoint)
        .bind(&entity.p256dh)
        .bind(&entity.auth)
        .fetch_optional(&mut *tx)
        .await
        .map_err(db_error)?
        .ok_or_else(|| {
            RepositoryError::ConflictError(
                "Subscription already exists for this user and endpoint".to_string(),
            )
        })?;

        tx.commit().await.map_err(db_error)?;
        Ok(created)
    }

    /// Update an existing subscription
    async fn update(
        &self,
        id: i64,
        entity: PushSubscription,
    ) -> Result<Option<PushSubscription>, RepositoryError> {
        sqlx::query_as::<_, PushSubscription>(&format!(
            "UPDATE pushsubscription SET p256dh = $2, auth = $3 WHERE id = $1 RETURNING {}",
            COLUMNS
        ))
        .bind(id)
        .bind(&entity.p256dh)
        .bind(&entity.auth)
        .fetch_optional(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Delete a subscription
    async fn delete(&self, id: i64) -> Result<bool, RepositoryError> {
        let result = sqlx::query("DELETE FROM pushsubscription WHERE id = $1")
            .bind(id)
            .execute(&self.pool)
            .await
            .map_err(|e| RepositoryError::DatabaseError(e.to_string()))?;

        Ok(result.rows_affected() > 0)
    }
}

#[cfg(test)]
mod tests {
    #[test]
    #[ignore]
    fn test_push_subscription_repository_creation() {
        // This test is a placeholder - full integration tests would require a test database
        // Marked as ignored because it requires external infrastructure
    }
}
