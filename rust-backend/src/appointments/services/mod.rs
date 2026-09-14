use crate::appointments::models::Appointment;
use crate::appointments::repository::{AppointmentRepository, RepositoryError};
use uuid::Uuid;
use chrono::{DateTime, Utc};

/// Query parameters for listing appointments
#[derive(Debug, Clone)]
pub struct ListAppointmentsQuery {
    pub limit: Option<u32>,
    pub offset: Option<u32>,
}

impl Default for ListAppointmentsQuery {
    fn default() -> Self {
        Self {
            limit: Some(20),
            offset: Some(0),
        }
    }
}

/// Paginated response wrapper
#[derive(Debug)]
pub struct PagedResponse<T> {
    pub data: Vec<T>,
    pub total: i64,
    pub limit: u32,
    pub offset: u32,
}

/// AppointmentService handles business logic for appointments
pub struct AppointmentService {
    repo: AppointmentRepository,
}

impl AppointmentService {
    /// Create a new AppointmentService
    pub fn new(repo: AppointmentRepository) -> Self {
        Self { repo }
    }

    /// Get a single appointment by ID
    pub async fn get_appointment(&self, id: Uuid) -> Result<Option<Appointment>, RepositoryError> {
        self.repo.find_by_id(id).await
    }

    /// List appointments visible to a user with pagination
    pub async fn list_user_appointments(
        &self,
        user_id: Uuid,
        query: ListAppointmentsQuery,
    ) -> Result<Vec<Appointment>, RepositoryError> {
        let mut appointments = self.repo.find_visible_to_user(user_id).await?;

        // Apply pagination
        let offset = query.offset.unwrap_or(0) as usize;
        let limit = query.limit.unwrap_or(20) as usize;

        // Sort by start_time descending (most recent first)
        appointments.sort_by(|a, b| b.start_time.cmp(&a.start_time));

        // Apply pagination
        let end = (offset + limit).min(appointments.len());
        Ok(appointments
            .into_iter()
            .skip(offset)
            .take(limit)
            .collect())
    }

    /// List all appointments (admin only)
    pub async fn list_all_appointments(
        &self,
        query: ListAppointmentsQuery,
    ) -> Result<Vec<Appointment>, RepositoryError> {
        let mut appointments = self.repo.find_all().await?;

        // Apply pagination
        let offset = query.offset.unwrap_or(0) as usize;
        let limit = query.limit.unwrap_or(20) as usize;

        // Sort by start_time descending (most recent first)
        appointments.sort_by(|a, b| b.start_time.cmp(&a.start_time));

        // Apply pagination
        Ok(appointments
            .into_iter()
            .skip(offset)
            .take(limit)
            .collect())
    }

    /// Get appointments for a specific creator
    pub async fn get_creator_appointments(
        &self,
        creator_id: Uuid,
    ) -> Result<Vec<Appointment>, RepositoryError> {
        self.repo.find_by_creator(creator_id).await
    }

    /// Get appointments within a date range
    pub async fn get_appointments_by_date_range(
        &self,
        start: DateTime<Utc>,
        end: DateTime<Utc>,
    ) -> Result<Vec<Appointment>, RepositoryError> {
        self.repo.find_by_date_range(start, end).await
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_list_query_default() {
        let query = ListAppointmentsQuery::default();
        assert_eq!(query.limit, Some(20));
        assert_eq!(query.offset, Some(0));
    }
}
