use crate::database::repository::{Repository, RepositoryError};
use crate::push_notifications::models::PushSubscription;
use async_trait::async_trait;
use chrono::Utc;
use sqlx::PgPool;
use uuid::Uuid;

/// PushSubscriptionRepository provides data access for PushSubscription entities
pub struct PushSubscriptionRepository {
    pool: PgPool,
}

impl PushSubscriptionRepository {
    /// Create a new PushSubscriptionRepository
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }

    /// Find subscriptions by user ID
    pub async fn find_by_user_id(
        &self,
        user_id: Uuid,
    ) -> Result<Vec<PushSubscription>, RepositoryError> {
        sqlx::query_as::<_, PushSubscription>(
            "SELECT id, user_id, endpoint, p256dh, auth, created_at, updated_at
             FROM push_subscriptions WHERE user_id = $1"
        )
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
        sqlx::query_as::<_, PushSubscription>(
            "SELECT id, user_id, endpoint, p256dh, auth, created_at, updated_at
             FROM push_subscriptions WHERE endpoint = $1"
        )
        .bind(endpoint)
        .fetch_optional(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Delete subscription by user ID and endpoint
    pub async fn delete_by_user_and_endpoint(
        &self,
        user_id: Uuid,
        endpoint: &str,
    ) -> Result<bool, RepositoryError> {
        let result = sqlx::query(
            "DELETE FROM push_subscriptions WHERE user_id = $1 AND endpoint = $2"
        )
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
    async fn find_by_id(&self, id: Uuid) -> Result<Option<PushSubscription>, RepositoryError> {
        sqlx::query_as::<_, PushSubscription>(
            "SELECT id, user_id, endpoint, p256dh, auth, created_at, updated_at
             FROM push_subscriptions WHERE id = $1"
        )
        .bind(id)
        .fetch_optional(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Find all subscriptions
    async fn find_all(&self) -> Result<Vec<PushSubscription>, RepositoryError> {
        sqlx::query_as::<_, PushSubscription>(
            "SELECT id, user_id, endpoint, p256dh, auth, created_at, updated_at
             FROM push_subscriptions"
        )
        .fetch_all(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Create a new subscription
    async fn create(&self, entity: PushSubscription) -> Result<PushSubscription, RepositoryError> {
        let now = Utc::now();
        let result = sqlx::query_as::<_, PushSubscription>(
            "INSERT INTO push_subscriptions (id, user_id, endpoint, p256dh, auth, created_at, updated_at)
             VALUES ($1, $2, $3, $4, $5, $6, $7)
             RETURNING id, user_id, endpoint, p256dh, auth, created_at, updated_at"
        )
        .bind(entity.id)
        .bind(entity.user_id)
        .bind(&entity.endpoint)
        .bind(&entity.p256dh)
        .bind(&entity.auth)
        .bind(now)
        .bind(now)
        .fetch_one(&self.pool)
        .await
        .map_err(|e| {
            if e.to_string().contains("unique_subscription") {
                RepositoryError::ConflictError("Subscription already exists for this user and endpoint".to_string())
            } else {
                RepositoryError::DatabaseError(e.to_string())
            }
        })?;

        Ok(result)
    }

    /// Update an existing subscription
    async fn update(
        &self,
        id: Uuid,
        entity: PushSubscription,
    ) -> Result<Option<PushSubscription>, RepositoryError> {
        let now = Utc::now();
        sqlx::query_as::<_, PushSubscription>(
            "UPDATE push_subscriptions
             SET p256dh = $2, auth = $3, updated_at = $4
             WHERE id = $1
             RETURNING id, user_id, endpoint, p256dh, auth, created_at, updated_at"
        )
        .bind(id)
        .bind(&entity.p256dh)
        .bind(&entity.auth)
        .bind(now)
        .fetch_optional(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Delete a subscription
    async fn delete(&self, id: Uuid) -> Result<bool, RepositoryError> {
        let result = sqlx::query("DELETE FROM push_subscriptions WHERE id = $1")
            .bind(id)
            .execute(&self.pool)
            .await
            .map_err(|e| RepositoryError::DatabaseError(e.to_string()))?;

        Ok(result.rows_affected() > 0)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_push_subscription_repository_creation() {
        // This test just verifies the repository can be instantiated
        // Full integration tests would require a test database
        let _repo = PushSubscriptionRepository::new({
            // In real tests, we'd use a connection pool
            panic!("This is just a placeholder test")
        });
    }
}
