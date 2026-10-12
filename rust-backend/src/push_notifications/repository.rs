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
    /// (user, endpoint), so duplicates are rejected here; the entity's `id` is ignored
    /// (the id comes from `pushsubscription_seq`).
    async fn create(&self, entity: PushSubscription) -> Result<PushSubscription, RepositoryError> {
        sqlx::query_as::<_, PushSubscription>(&format!(
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
        .fetch_optional(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))?
        .ok_or_else(|| {
            RepositoryError::ConflictError(
                "Subscription already exists for this user and endpoint".to_string(),
            )
        })
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
