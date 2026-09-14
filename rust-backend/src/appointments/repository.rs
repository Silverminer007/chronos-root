use crate::appointments::models::Appointment;
use sqlx::PgPool;
use uuid::Uuid;

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
}
