use crate::appointments::models::Appointment;
use crate::appointments::repository::{AppointmentRepository, RepositoryError};
use uuid::Uuid;
use chrono::{DateTime, Utc};

/// Query parameters for listing appointments
#[derive(Debug, Clone)]
pub struct ListAppointmentsQuery {
    pub limit: Option<u32>,
    pub offset: Option<u32>,
    pub sort_by: Option<String>,   // "date" or "title"
    pub sort_dir: Option<String>,  // "asc" or "desc"
}

impl Default for ListAppointmentsQuery {
    fn default() -> Self {
        Self {
            limit: Some(20),
            offset: Some(0),
            sort_by: Some("date".to_string()),
            sort_dir: Some("desc".to_string()),
        }
    }
}

/// Sort direction for appointments
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SortDirection {
    Ascending,
    Descending,
}

/// Sort field for appointments
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum SortField {
    Date,
    Title,
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

    /// Parse sort field from string
    fn parse_sort_field(field_str: &str) -> SortField {
        match field_str.to_lowercase().as_str() {
            "title" => SortField::Title,
            _ => SortField::Date, // Default to date
        }
    }

    /// Parse sort direction from string
    fn parse_sort_direction(dir_str: &str) -> SortDirection {
        match dir_str.to_lowercase().as_str() {
            "asc" | "ascending" => SortDirection::Ascending,
            _ => SortDirection::Descending, // Default to descending
        }
    }

    /// Apply sorting to a list of appointments
    fn apply_sorting(
        mut appointments: Vec<Appointment>,
        sort_field: SortField,
        sort_direction: SortDirection,
    ) -> Vec<Appointment> {
        match sort_field {
            SortField::Date => {
                if sort_direction == SortDirection::Ascending {
                    appointments.sort_by(|a, b| a.start_time.cmp(&b.start_time));
                } else {
                    appointments.sort_by(|a, b| b.start_time.cmp(&a.start_time));
                }
            }
            SortField::Title => {
                if sort_direction == SortDirection::Ascending {
                    appointments.sort_by(|a, b| a.title.cmp(&b.title));
                } else {
                    appointments.sort_by(|a, b| b.title.cmp(&a.title));
                }
            }
        }
        appointments
    }

    /// Get a single appointment by ID
    pub async fn get_appointment(&self, id: Uuid) -> Result<Option<Appointment>, RepositoryError> {
        self.repo.find_by_id(id).await
    }

    /// List appointments visible to a user with pagination and sorting
    pub async fn list_user_appointments(
        &self,
        user_id: Uuid,
        query: ListAppointmentsQuery,
    ) -> Result<Vec<Appointment>, RepositoryError> {
        let appointments = self.repo.find_visible_to_user(user_id).await?;

        // Parse sort parameters
        let sort_field = query
            .sort_by
            .as_ref()
            .map(|s| Self::parse_sort_field(s))
            .unwrap_or(SortField::Date);

        let sort_direction = query
            .sort_dir
            .as_ref()
            .map(|d| Self::parse_sort_direction(d))
            .unwrap_or(SortDirection::Descending);

        // Apply sorting
        let sorted_appointments = Self::apply_sorting(appointments, sort_field, sort_direction);

        // Apply pagination
        let offset = query.offset.unwrap_or(0) as usize;
        let limit = query.limit.unwrap_or(20) as usize;

        let end = (offset + limit).min(sorted_appointments.len());
        Ok(sorted_appointments
            .into_iter()
            .skip(offset)
            .take(limit)
            .collect())
    }

    /// List all appointments (admin only) with sorting and pagination
    pub async fn list_all_appointments(
        &self,
        query: ListAppointmentsQuery,
    ) -> Result<Vec<Appointment>, RepositoryError> {
        let appointments = self.repo.find_all().await?;

        // Parse sort parameters
        let sort_field = query
            .sort_by
            .as_ref()
            .map(|s| Self::parse_sort_field(s))
            .unwrap_or(SortField::Date);

        let sort_direction = query
            .sort_dir
            .as_ref()
            .map(|d| Self::parse_sort_direction(d))
            .unwrap_or(SortDirection::Descending);

        // Apply sorting
        let sorted_appointments = Self::apply_sorting(appointments, sort_field, sort_direction);

        // Apply pagination
        let offset = query.offset.unwrap_or(0) as usize;
        let limit = query.limit.unwrap_or(20) as usize;

        let end = (offset + limit).min(sorted_appointments.len());
        Ok(sorted_appointments
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

    /// Create a new appointment
    pub async fn create_appointment(
        &self,
        title: String,
        description: Option<String>,
        location: Option<String>,
        start_time: DateTime<Utc>,
        end_time: DateTime<Utc>,
        creator_id: Uuid,
    ) -> Result<Appointment, RepositoryError> {
        // Validation
        if title.is_empty() {
            return Err(RepositoryError::InvalidInput("Title cannot be empty".to_string()));
        }

        if start_time >= end_time {
            return Err(RepositoryError::InvalidInput("Start time must be before end time".to_string()));
        }

        self.repo.create(title, description, location, start_time, end_time, creator_id).await
    }

    /// Update an appointment
    pub async fn update_appointment(
        &self,
        id: Uuid,
        title: Option<String>,
        description: Option<String>,
        location: Option<String>,
        start_time: Option<DateTime<Utc>>,
        end_time: Option<DateTime<Utc>>,
    ) -> Result<Appointment, RepositoryError> {
        // Validation
        if let Some(ref t) = title {
            if t.is_empty() {
                return Err(RepositoryError::InvalidInput("Title cannot be empty".to_string()));
            }
        }

        if let (Some(start), Some(end)) = (start_time, end_time) {
            if start >= end {
                return Err(RepositoryError::InvalidInput("Start time must be before end time".to_string()));
            }
        }

        self.repo.update(id, title, description, location, start_time, end_time).await
    }

    /// Delete an appointment
    pub async fn delete_appointment(&self, id: Uuid) -> Result<(), RepositoryError> {
        self.repo.delete(id).await
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
