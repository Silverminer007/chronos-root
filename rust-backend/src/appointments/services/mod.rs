use crate::appointments::models::{Appointment, CreateAppointmentRequest, UpdateAppointmentRequest, AppointmentResponse};
use crate::appointments::repository::{AppointmentRepository, RepositoryError};
use chrono::{DateTime, Utc};
use uuid::Uuid;

/// Query parameters for listing appointments
#[derive(Debug, Clone)]
pub struct ListAppointmentsQuery {
    pub limit: Option<u32>,
    pub offset: Option<u32>,
    pub sort_by: Option<String>,  // "date" or "title"
    pub sort_dir: Option<String>, // "asc" or "desc"
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
                    appointments.sort_by_key(|a| a.start_time);
                } else {
                    appointments.sort_by_key(|a| std::cmp::Reverse(a.start_time));
                }
            }
            SortField::Title => {
                if sort_direction == SortDirection::Ascending {
                    appointments.sort_by_key(|a| a.title.clone());
                } else {
                    appointments.sort_by_key(|a| std::cmp::Reverse(a.title.clone()));
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

        let _end = (offset + limit).min(sorted_appointments.len());
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

        let _end = (offset + limit).min(sorted_appointments.len());
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
        request: CreateAppointmentRequest,
    ) -> Result<AppointmentResponse, String> {
        // Validation: name cannot be blank
        if request.name.trim().is_empty() {
            return Err("name cannot be blank".to_string());
        }

        // Validation: start and end times must be valid ISO-8601 strings
        let start_time = DateTime::parse_from_rfc3339(&request.start)
            .map_err(|_| "start must be a valid ISO-8601 timestamp".to_string())?
            .with_timezone(&Utc);

        let end_time = DateTime::parse_from_rfc3339(&request.end)
            .map_err(|_| "end must be a valid ISO-8601 timestamp".to_string())?
            .with_timezone(&Utc);

        // Validation: end time must be >= start time
        if end_time < start_time {
            return Err("end time cannot be before start time".to_string());
        }

        // Validation: minimal_attendees must be non-negative if provided
        if let Some(ma) = request.minimal_attendees {
            if ma < 0 {
                return Err("minimal_attendees must be non-negative".to_string());
            }
        }

        // Create appointment in database
        let appointment = self.repo.create(
            request.name,
            request.description,
            request.venue,
            start_time,
            end_time,
            Uuid::new_v4(), // TODO: use creator_id from principal context
            request.minimal_attendees,
        ).await
        .map_err(|e| format!("Failed to create appointment: {}", e))?;

        Ok(appointment.into())
    }

    /// Update an appointment
    pub async fn update_appointment(
        &self,
        id: Uuid,
        request: UpdateAppointmentRequest,
    ) -> Result<AppointmentResponse, String> {
        // Validation: if name is provided, it cannot be blank
        if let Some(ref name) = request.name {
            if name.trim().is_empty() {
                return Err("name cannot be blank".to_string());
            }
        }

        // Parse timestamps if provided
        let start_time = if let Some(ref start) = request.start {
            Some(DateTime::parse_from_rfc3339(start)
                .map_err(|_| "start must be a valid ISO-8601 timestamp".to_string())?
                .with_timezone(&Utc))
        } else {
            None
        };

        let end_time = if let Some(ref end) = request.end {
            Some(DateTime::parse_from_rfc3339(end)
                .map_err(|_| "end must be a valid ISO-8601 timestamp".to_string())?
                .with_timezone(&Utc))
        } else {
            None
        };

        // Validation: if both start and end are provided, end must be >= start
        if let (Some(st), Some(et)) = (start_time, end_time) {
            if et < st {
                return Err("end time cannot be before start time".to_string());
            }
        }

        // Validation: minimal_attendees must be non-negative if provided
        if let Some(ma) = request.minimal_attendees {
            if ma < 0 {
                return Err("minimal_attendees must be non-negative".to_string());
            }
        }

        // Update appointment in database
        let updated = self.repo.update(
            id,
            request.name,
            request.description,
            request.venue,
            start_time,
            end_time,
            request.minimal_attendees,
        ).await
        .map_err(|e| format!("Failed to update appointment: {}", e))?
        .ok_or_else(|| "Appointment not found".to_string())?;

        Ok(updated.into())
    }

    /// Soft delete an appointment
    pub async fn delete_appointment(&self, id: Uuid) -> Result<(), String> {
        self.repo.delete_soft(id).await
            .map_err(|e| format!("Failed to delete appointment: {}", e))?
            .ok_or_else(|| "Appointment not found".to_string())?;
        Ok(())
    }

    /// Soft cancel an appointment
    pub async fn cancel_appointment(&self, id: Uuid) -> Result<(), String> {
        self.repo.cancel_soft(id).await
            .map_err(|e| format!("Failed to cancel appointment: {}", e))?
            .ok_or_else(|| "Appointment not found".to_string())?;
        Ok(())
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
