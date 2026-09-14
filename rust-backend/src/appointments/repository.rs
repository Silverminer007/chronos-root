use crate::appointments::models::Appointment;
use crate::database::repository::{Repository, RepositoryError};
use async_trait::async_trait;
use chrono::Utc;
use sqlx::PgPool;
use uuid::Uuid;

/// AppointmentRepository provides data access for Appointment entities
pub struct AppointmentRepository {
    pool: PgPool,
}

impl AppointmentRepository {
    /// Create a new AppointmentRepository
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }
}

#[async_trait]
impl Repository<Appointment> for AppointmentRepository {
    /// Find an appointment by its ID
    async fn find_by_id(&self, id: Uuid) -> Result<Option<Appointment>, RepositoryError> {
        sqlx::query_as::<_, Appointment>(
            "SELECT id, title, description, start_time, end_time, location, creator_id, created_at, updated_at
             FROM appointments WHERE id = $1"
        )
        .bind(id)
        .fetch_optional(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Find all appointments
    async fn find_all(&self) -> Result<Vec<Appointment>, RepositoryError> {
        sqlx::query_as::<_, Appointment>(
            "SELECT id, title, description, start_time, end_time, location, creator_id, created_at, updated_at
             FROM appointments ORDER BY start_time DESC"
        )
        .fetch_all(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Create a new appointment
    async fn create(&self, entity: Appointment) -> Result<Appointment, RepositoryError> {
        let now = Utc::now();
        let result = sqlx::query_as::<_, Appointment>(
            "INSERT INTO appointments (id, title, description, start_time, end_time, location, creator_id, created_at, updated_at)
             VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
             RETURNING id, title, description, start_time, end_time, location, creator_id, created_at, updated_at"
        )
        .bind(entity.id)
        .bind(&entity.title)
        .bind(&entity.description)
        .bind(entity.start_time)
        .bind(entity.end_time)
        .bind(&entity.location)
        .bind(entity.creator_id)
        .bind(now)
        .bind(now)
        .fetch_one(&self.pool)
        .await
        .map_err(|e| {
            if e.to_string().contains("valid_times") {
                RepositoryError::InvalidInput("end_time must be after start_time".to_string())
            } else {
                RepositoryError::DatabaseError(e.to_string())
            }
        })?;

        Ok(result)
    }

    /// Update an existing appointment
    async fn update(
        &self,
        id: Uuid,
        entity: Appointment,
    ) -> Result<Option<Appointment>, RepositoryError> {
        let now = Utc::now();
        sqlx::query_as::<_, Appointment>(
            "UPDATE appointments
             SET title = $2, description = $3, start_time = $4, end_time = $5, location = $6, updated_at = $7
             WHERE id = $1
             RETURNING id, title, description, start_time, end_time, location, creator_id, created_at, updated_at"
        )
        .bind(id)
        .bind(&entity.title)
        .bind(&entity.description)
        .bind(entity.start_time)
        .bind(entity.end_time)
        .bind(&entity.location)
        .bind(now)
        .fetch_optional(&self.pool)
        .await
        .map_err(|e| {
            if e.to_string().contains("valid_times") {
                RepositoryError::InvalidInput("end_time must be after start_time".to_string())
            } else {
                RepositoryError::DatabaseError(e.to_string())
            }
        })
    }

    /// Delete an appointment
    async fn delete(&self, id: Uuid) -> Result<bool, RepositoryError> {
        let result = sqlx::query("DELETE FROM appointments WHERE id = $1")
            .bind(id)
            .execute(&self.pool)
            .await
            .map_err(|e| RepositoryError::DatabaseError(e.to_string()))?;

        Ok(result.rows_affected() > 0)
    }
}
