use crate::appointments::models::Appointment;
use sqlx::PgPool;
use uuid::Uuid;
use chrono::{DateTime, Utc};

/// Errors that can occur in the repository layer
#[derive(Debug)]
pub enum RepositoryError {
    DatabaseError(String),
    NotFound,
    InvalidInput(String),
}

impl std::fmt::Display for RepositoryError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            RepositoryError::DatabaseError(msg) => write!(f, "Database error: {}", msg),
            RepositoryError::NotFound => write!(f, "Not found"),
            RepositoryError::InvalidInput(msg) => write!(f, "Invalid input: {}", msg),
        }
    }
}

impl std::error::Error for RepositoryError {}

/// AppointmentRepository provides data access for Appointment entities
pub struct AppointmentRepository {
    pool: PgPool,
}

impl AppointmentRepository {
    /// Create a new AppointmentRepository
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }

    /// Find an appointment by its ID
    pub async fn find_by_id(&self, id: Uuid) -> Result<Option<Appointment>, RepositoryError> {
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
    pub async fn find_all(&self) -> Result<Vec<Appointment>, RepositoryError> {
        sqlx::query_as::<_, Appointment>(
            "SELECT id, title, description, start_time, end_time, location, creator_id, created_at, updated_at
             FROM appointments ORDER BY start_time DESC"
        )
        .fetch_all(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Find appointments by creator_id
    pub async fn find_by_creator(&self, creator_id: Uuid) -> Result<Vec<Appointment>, RepositoryError> {
        sqlx::query_as::<_, Appointment>(
            "SELECT id, title, description, start_time, end_time, location, creator_id, created_at, updated_at
             FROM appointments WHERE creator_id = $1 ORDER BY start_time DESC"
        )
        .bind(creator_id)
        .fetch_all(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Find appointments visible to a user (created by user or user is a participant)
    pub async fn find_visible_to_user(&self, user_id: Uuid) -> Result<Vec<Appointment>, RepositoryError> {
        sqlx::query_as::<_, Appointment>(
            "SELECT DISTINCT a.id, a.title, a.description, a.start_time, a.end_time, a.location, a.creator_id, a.created_at, a.updated_at
             FROM appointments a
             LEFT JOIN appointment_participants ap ON a.id = ap.appointment_id
             WHERE a.creator_id = $1 OR ap.user_id = $1
             ORDER BY a.start_time DESC"
        )
        .bind(user_id)
        .fetch_all(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Find appointments by date range
    pub async fn find_by_date_range(
        &self,
        start: DateTime<Utc>,
        end: DateTime<Utc>,
    ) -> Result<Vec<Appointment>, RepositoryError> {
        sqlx::query_as::<_, Appointment>(
            "SELECT id, title, description, start_time, end_time, location, creator_id, created_at, updated_at
             FROM appointments WHERE start_time >= $1 AND end_time <= $2 ORDER BY start_time ASC"
        )
        .bind(start)
        .bind(end)
        .fetch_all(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }
}
