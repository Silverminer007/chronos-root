use crate::appointments::models::Appointment;
use chrono::{DateTime, Utc};
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
            "SELECT id, title, description, start_time, end_time, location, creator_id, created_at, updated_at, status, minimal_attendees
             FROM appointments WHERE id = $1 AND status NOT IN ('DELETED', 'CANCELLED')"
        )
        .bind(id)
        .fetch_optional(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Find all appointments
    pub async fn find_all(&self) -> Result<Vec<Appointment>, RepositoryError> {
        sqlx::query_as::<_, Appointment>(
            "SELECT id, title, description, start_time, end_time, location, creator_id, created_at, updated_at, status, minimal_attendees
             FROM appointments WHERE status NOT IN ('DELETED', 'CANCELLED') ORDER BY start_time DESC"
        )
        .fetch_all(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Find appointments by creator_id
    pub async fn find_by_creator(
        &self,
        creator_id: Uuid,
    ) -> Result<Vec<Appointment>, RepositoryError> {
        sqlx::query_as::<_, Appointment>(
            "SELECT id, title, description, start_time, end_time, location, creator_id, created_at, updated_at, status, minimal_attendees
             FROM appointments WHERE creator_id = $1 AND status NOT IN ('DELETED', 'CANCELLED') ORDER BY start_time DESC"
        )
        .bind(creator_id)
        .fetch_all(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Find appointments visible to a user (created by user or user is a participant)
    pub async fn find_visible_to_user(
        &self,
        user_id: Uuid,
    ) -> Result<Vec<Appointment>, RepositoryError> {
        sqlx::query_as::<_, Appointment>(
            "SELECT DISTINCT a.id, a.title, a.description, a.start_time, a.end_time, a.location, a.creator_id, a.created_at, a.updated_at, a.status, a.minimal_attendees
             FROM appointments a
             LEFT JOIN appointment_participants ap ON a.id = ap.appointment_id
             WHERE (a.creator_id = $1 OR ap.user_id = $1) AND a.status NOT IN ('DELETED', 'CANCELLED')
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
            "SELECT id, title, description, start_time, end_time, location, creator_id, created_at, updated_at, status, minimal_attendees
             FROM appointments WHERE start_time >= $1 AND end_time <= $2 AND status NOT IN ('DELETED', 'CANCELLED') ORDER BY start_time ASC"
        )
        .bind(start)
        .bind(end)
        .fetch_all(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Create a new appointment
    pub async fn create(&self,
        title: String,
        description: Option<String>,
        location: Option<String>,
        start_time: DateTime<Utc>,
        end_time: DateTime<Utc>,
        creator_id: Uuid,
        minimal_attendees: Option<i32>,
    ) -> Result<Appointment, RepositoryError> {
        let id = Uuid::new_v4();
        let now = chrono::Utc::now();
        let status = "PLANNED";

        sqlx::query_as::<_, Appointment>(
            "INSERT INTO appointments (id, title, description, start_time, end_time, location, creator_id, created_at, updated_at, status, minimal_attendees)
             VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)
             RETURNING id, title, description, start_time, end_time, location, creator_id, created_at, updated_at, status, minimal_attendees"
        )
        .bind(id)
        .bind(title)
        .bind(description)
        .bind(start_time)
        .bind(end_time)
        .bind(location)
        .bind(creator_id)
        .bind(now)
        .bind(now)
        .bind(status)
        .bind(minimal_attendees)
        .fetch_one(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Update an appointment
    pub async fn update(&self,
        id: Uuid,
        title: Option<String>,
        description: Option<String>,
        location: Option<String>,
        start_time: Option<DateTime<Utc>>,
        end_time: Option<DateTime<Utc>>,
        minimal_attendees: Option<i32>,
    ) -> Result<Option<Appointment>, RepositoryError> {
        let now = chrono::Utc::now();

        // Build dynamic query based on which fields are provided
        let mut query_str = "UPDATE appointments SET updated_at = $1".to_string();
        let mut param_count = 1;

        if title.is_some() {
            param_count += 1;
            query_str.push_str(&format!(", title = ${}", param_count));
        }
        if description.is_some() {
            param_count += 1;
            query_str.push_str(&format!(", description = ${}", param_count));
        }
        if location.is_some() {
            param_count += 1;
            query_str.push_str(&format!(", location = ${}", param_count));
        }
        if start_time.is_some() {
            param_count += 1;
            query_str.push_str(&format!(", start_time = ${}", param_count));
        }
        if end_time.is_some() {
            param_count += 1;
            query_str.push_str(&format!(", end_time = ${}", param_count));
        }
        if minimal_attendees.is_some() {
            param_count += 1;
            query_str.push_str(&format!(", minimal_attendees = ${}", param_count));
        }

        param_count += 1;
        query_str.push_str(&format!(" WHERE id = ${}", param_count));
        query_str.push_str(" RETURNING id, title, description, start_time, end_time, location, creator_id, created_at, updated_at, status, minimal_attendees");

        let mut query = sqlx::query_as::<_, Appointment>(&query_str)
            .bind(now);

        if let Some(t) = title {
            query = query.bind(t);
        }
        if let Some(d) = description {
            query = query.bind(d);
        }
        if let Some(l) = location {
            query = query.bind(l);
        }
        if let Some(st) = start_time {
            query = query.bind(st);
        }
        if let Some(et) = end_time {
            query = query.bind(et);
        }
        if let Some(ma) = minimal_attendees {
            query = query.bind(ma);
        }

        query = query.bind(id);

        query
            .fetch_optional(&self.pool)
            .await
            .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Soft delete an appointment (set status to DELETED)
    pub async fn delete_soft(&self, id: Uuid) -> Result<Option<Appointment>, RepositoryError> {
        sqlx::query_as::<_, Appointment>(
            "UPDATE appointments SET status = 'DELETED', updated_at = NOW() WHERE id = $1
             RETURNING id, title, description, start_time, end_time, location, creator_id, created_at, updated_at, status, minimal_attendees"
        )
        .bind(id)
        .fetch_optional(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Soft cancel an appointment (set status to CANCELLED)
    pub async fn cancel_soft(&self, id: Uuid) -> Result<Option<Appointment>, RepositoryError> {
        sqlx::query_as::<_, Appointment>(
            "UPDATE appointments SET status = 'CANCELLED', updated_at = NOW() WHERE id = $1
             RETURNING id, title, description, start_time, end_time, location, creator_id, created_at, updated_at, status, minimal_attendees"
        )
        .bind(id)
        .fetch_optional(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Check if a user is a participant in an appointment
    pub async fn is_participant(&self, appointment_id: Uuid, user_id: Uuid) -> Result<bool, RepositoryError> {
        let result = sqlx::query_scalar::<_, bool>(
            "SELECT EXISTS(SELECT 1 FROM appointment_participants WHERE appointment_id = $1 AND user_id = $2)"
        )
        .bind(appointment_id)
        .bind(user_id)
        .fetch_one(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))?;

        Ok(result)
    }

    /// Add a participant to an appointment
    pub async fn add_participant(
        &self,
        appointment_id: Uuid,
        user_id: Uuid,
        role: &str,
        status: &str,
    ) -> Result<(), RepositoryError> {
        let id = Uuid::new_v4();
        let now = chrono::Utc::now();

        sqlx::query(
            "INSERT INTO appointment_participants (id, appointment_id, user_id, role, status, created_at, updated_at)
             VALUES ($1, $2, $3, $4, $5, $6, $7)
             ON CONFLICT (appointment_id, user_id) DO NOTHING"
        )
        .bind(id)
        .bind(appointment_id)
        .bind(user_id)
        .bind(role)
        .bind(status)
        .bind(now)
        .bind(now)
        .execute(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))?;

        Ok(())
    }
}
