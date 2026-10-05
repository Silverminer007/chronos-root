use crate::appointments::events::{
    AppointmentCancelledEvent, AppointmentCreatedEvent, AppointmentDeletedEvent,
    AppointmentEditedEvent, AppointmentMovedEvent,
};
use crate::appointments::models::{
    Appointment, AppointmentResponse, CreateAppointmentRequest, UpdateAppointmentRequest,
};
use crate::appointments::repository::{AppointmentRepository, RepositoryError};
use crate::event_bus::EventPublisher;
use chrono::{DateTime, Utc};
use uuid::Uuid;

/// Custom error type for appointment service operations
#[derive(Debug, Clone)]
pub enum ServiceError {
    ValidationError(String),
    NotFound,
    DatabaseError(String),
    InvalidFormat(String),
}

impl std::fmt::Display for ServiceError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            ServiceError::ValidationError(msg) => write!(f, "Validation error: {}", msg),
            ServiceError::NotFound => write!(f, "Appointment not found"),
            ServiceError::DatabaseError(msg) => write!(f, "Database error: {}", msg),
            ServiceError::InvalidFormat(msg) => write!(f, "Invalid format: {}", msg),
        }
    }
}

impl std::error::Error for ServiceError {}

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
    event_publisher: Option<std::sync::Arc<dyn EventPublisher>>,
}

impl AppointmentService {
    /// Create a new AppointmentService
    pub fn new(repo: AppointmentRepository) -> Self {
        Self {
            repo,
            event_publisher: None,
        }
    }

    /// Create a new AppointmentService with event publishing
    pub fn with_events(
        repo: AppointmentRepository,
        event_publisher: std::sync::Arc<dyn EventPublisher>,
    ) -> Self {
        Self {
            repo,
            event_publisher: Some(event_publisher),
        }
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
    pub async fn get_appointment(&self, id: Uuid) -> Result<Appointment, ServiceError> {
        self.repo
            .find_by_id(id)
            .await
            .map_err(|e| ServiceError::DatabaseError(e.to_string()))?
            .ok_or(ServiceError::NotFound)
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
        creator_id: String,
    ) -> Result<AppointmentResponse, ServiceError> {
        // Parse creator_id as UUID
        let creator_uuid = Uuid::parse_str(&creator_id).map_err(|_| {
            ServiceError::InvalidFormat("creator_id must be a valid UUID".to_string())
        })?;

        // Validation: name cannot be blank
        if request.name.trim().is_empty() {
            return Err(ServiceError::ValidationError(
                "name cannot be blank".to_string(),
            ));
        }

        // Validation: start and end times must be valid ISO-8601 strings
        let start_time = DateTime::parse_from_rfc3339(&request.start)
            .map_err(|_| {
                ServiceError::InvalidFormat("start must be a valid ISO-8601 timestamp".to_string())
            })?
            .with_timezone(&Utc);

        let end_time = DateTime::parse_from_rfc3339(&request.end)
            .map_err(|_| {
                ServiceError::InvalidFormat("end must be a valid ISO-8601 timestamp".to_string())
            })?
            .with_timezone(&Utc);

        // Validation: end time must be >= start time
        if end_time < start_time {
            return Err(ServiceError::ValidationError(
                "end time cannot be before start time".to_string(),
            ));
        }

        // Validation: minimal_attendees must be non-negative if provided
        if let Some(ma) = request.minimal_attendees {
            if ma < 0 {
                return Err(ServiceError::ValidationError(
                    "minimal_attendees must be non-negative".to_string(),
                ));
            }
        }

        // Create appointment in database
        let appointment = self
            .repo
            .create(
                request.name,
                request.description,
                request.venue,
                start_time,
                end_time,
                creator_uuid,
                request.minimal_attendees,
            )
            .await
            .map_err(|e| ServiceError::DatabaseError(e.to_string()))?;

        // Add creator as RESPONSIBLE participant synchronously before firing event
        // This ensures the creator is added before the HTTP response is sent
        self.repo
            .add_participant(appointment.id, creator_uuid, "RESPONSIBLE", "APPROVED")
            .await
            .map_err(|e| ServiceError::DatabaseError(e.to_string()))?;

        // Fire event after successful database commit and participant addition
        if let Some(ref publisher) = self.event_publisher {
            let event = AppointmentCreatedEvent::new(appointment.id, creator_id);
            let event_json = serde_json::json!({
                "appointment_id": event.appointment_id.to_string(),
                "creator_id": event.creator_id,
                "timestamp": event.timestamp,
            });
            let event_bus_event =
                crate::event_bus::Event::new("AppointmentCreatedEvent", event_json);
            if let Err(e) = publisher.fire(event_bus_event).await {
                eprintln!("Failed to fire AppointmentCreatedEvent: {:?}", e);
                // Don't fail the request if event firing fails - this is a side effect
            }
        }

        Ok(appointment.into())
    }

    /// Update an appointment
    pub async fn update_appointment(
        &self,
        id: Uuid,
        request: UpdateAppointmentRequest,
    ) -> Result<AppointmentResponse, ServiceError> {
        // Get the existing appointment first to check if time changed
        let existing = self
            .repo
            .find_by_id(id)
            .await
            .map_err(|e| ServiceError::DatabaseError(e.to_string()))?
            .ok_or(ServiceError::NotFound)?;

        // Validation: if name is provided, it cannot be blank
        if let Some(ref name) = request.name {
            if name.trim().is_empty() {
                return Err(ServiceError::ValidationError(
                    "name cannot be blank".to_string(),
                ));
            }
        }

        // Parse timestamps if provided
        let start_time = if let Some(ref start) = request.start {
            Some(
                DateTime::parse_from_rfc3339(start)
                    .map_err(|_| {
                        ServiceError::InvalidFormat(
                            "start must be a valid ISO-8601 timestamp".to_string(),
                        )
                    })?
                    .with_timezone(&Utc),
            )
        } else {
            None
        };

        let end_time = if let Some(ref end) = request.end {
            Some(
                DateTime::parse_from_rfc3339(end)
                    .map_err(|_| {
                        ServiceError::InvalidFormat(
                            "end must be a valid ISO-8601 timestamp".to_string(),
                        )
                    })?
                    .with_timezone(&Utc),
            )
        } else {
            None
        };

        // Validation: if both start and end are provided, end must be >= start
        if let (Some(st), Some(et)) = (start_time, end_time) {
            if et < st {
                return Err(ServiceError::ValidationError(
                    "end time cannot be before start time".to_string(),
                ));
            }
        }

        // Validation: minimal_attendees must be non-negative if provided
        if let Some(ma) = request.minimal_attendees {
            if ma < 0 {
                return Err(ServiceError::ValidationError(
                    "minimal_attendees must be non-negative".to_string(),
                ));
            }
        }

        // Check if times changed for AppointmentMovedEvent
        let times_changed = (start_time.is_some() && start_time != Some(existing.start_time))
            || (end_time.is_some() && end_time != Some(existing.end_time));

        // Update appointment in database
        let updated = self
            .repo
            .update(
                id,
                request.name,
                request.description,
                request.venue,
                start_time,
                end_time,
                request.minimal_attendees,
            )
            .await
            .map_err(|e| ServiceError::DatabaseError(e.to_string()))?
            .ok_or(ServiceError::NotFound)?;

        // Fire events after successful database commit
        if let Some(ref publisher) = self.event_publisher {
            // Always fire AppointmentEditedEvent
            let edited_event = AppointmentEditedEvent::new(id);
            let edited_json = serde_json::json!({
                "appointment_id": edited_event.appointment_id.to_string(),
                "timestamp": edited_event.timestamp,
            });
            let event_bus_event =
                crate::event_bus::Event::new("AppointmentEditedEvent", edited_json);
            if let Err(e) = publisher.fire(event_bus_event).await {
                eprintln!("Failed to fire AppointmentEditedEvent: {:?}", e);
            }

            // Fire AppointmentMovedEvent if time changed
            if times_changed {
                let moved_event =
                    AppointmentMovedEvent::new(id, existing.start_time, existing.end_time);
                let moved_json = serde_json::json!({
                    "appointment_id": moved_event.appointment_id.to_string(),
                    "old_start": moved_event.old_start,
                    "old_end": moved_event.old_end,
                    "timestamp": moved_event.timestamp,
                });
                let event_bus_event =
                    crate::event_bus::Event::new("AppointmentMovedEvent", moved_json);
                if let Err(e) = publisher.fire(event_bus_event).await {
                    eprintln!("Failed to fire AppointmentMovedEvent: {:?}", e);
                }
            }
        }

        Ok(updated.into())
    }

    /// Soft delete an appointment
    pub async fn delete_appointment(&self, id: Uuid) -> Result<(), ServiceError> {
        self.repo
            .delete_soft(id)
            .await
            .map_err(|e| ServiceError::DatabaseError(e.to_string()))?
            .ok_or(ServiceError::NotFound)?;

        // Fire event after successful database commit
        if let Some(ref publisher) = self.event_publisher {
            let event = AppointmentDeletedEvent::new(id);
            let event_json = serde_json::json!({
                "appointment_id": event.appointment_id.to_string(),
                "timestamp": event.timestamp,
            });
            let event_bus_event =
                crate::event_bus::Event::new("AppointmentDeletedEvent", event_json);
            if let Err(e) = publisher.fire(event_bus_event).await {
                eprintln!("Failed to fire AppointmentDeletedEvent: {:?}", e);
            }
        }

        Ok(())
    }

    /// Soft cancel an appointment
    pub async fn cancel_appointment(&self, id: Uuid) -> Result<(), ServiceError> {
        self.repo
            .cancel_soft(id)
            .await
            .map_err(|e| ServiceError::DatabaseError(e.to_string()))?
            .ok_or(ServiceError::NotFound)?;

        // Fire event after successful database commit
        if let Some(ref publisher) = self.event_publisher {
            let event = AppointmentCancelledEvent::new(id);
            let event_json = serde_json::json!({
                "appointment_id": event.appointment_id.to_string(),
                "timestamp": event.timestamp,
            });
            let event_bus_event =
                crate::event_bus::Event::new("AppointmentCancelledEvent", event_json);
            if let Err(e) = publisher.fire(event_bus_event).await {
                eprintln!("Failed to fire AppointmentCancelledEvent: {:?}", e);
            }
        }

        Ok(())
    }

    /// Add a group to an appointment
    /// Only the appointment creator can add groups
    pub async fn add_group_to_appointment(
        &self,
        actor_id: Uuid,
        appointment_id: Uuid,
        group_id: Uuid,
        role: &str,
    ) -> Result<(), ServiceError> {
        // Check if appointment exists
        let appointment = self
            .repo
            .find_by_id(appointment_id)
            .await
            .map_err(|e| ServiceError::DatabaseError(e.to_string()))?
            .ok_or(ServiceError::NotFound)?;

        // Authorization: only creator can add groups
        if appointment.creator_id != actor_id {
            return Err(ServiceError::ValidationError(
                "Only the appointment creator can add groups".to_string(),
            ));
        }

        // Check if group exists and is not deleted
        let group_exists = self
            .repo
            .group_exists(group_id)
            .await
            .map_err(|e| ServiceError::DatabaseError(e.to_string()))?;

        if !group_exists {
            return Err(ServiceError::NotFound);
        }

        // Check if group is already a participant
        let already_participant = self
            .repo
            .group_participation_exists(appointment_id, group_id)
            .await
            .map_err(|e| ServiceError::DatabaseError(e.to_string()))?;

        if already_participant {
            return Err(ServiceError::ValidationError(
                "This group is already a participant of this appointment".to_string(),
            ));
        }

        // Add group to appointment
        self.repo
            .add_group(appointment_id, group_id, role)
            .await
            .map_err(|e| ServiceError::DatabaseError(e.to_string()))?;

        // Get group members and add them as participants
        let members = self
            .repo
            .get_group_members(group_id)
            .await
            .map_err(|e| ServiceError::DatabaseError(e.to_string()))?;

        for member_id in members {
            // Only add if not already a participant
            let is_participant = self
                .repo
                .is_participant(appointment_id, member_id)
                .await
                .map_err(|e| ServiceError::DatabaseError(e.to_string()))?;

            if !is_participant {
                self.repo
                    .add_participant(appointment_id, member_id, role, "PENDING")
                    .await
                    .map_err(|e| ServiceError::DatabaseError(e.to_string()))?;
            }
        }

        // Fire event after successful database commit
        if let Some(ref publisher) = self.event_publisher {
            let event_json = serde_json::json!({
                "appointment_id": appointment_id.to_string(),
                "group_id": group_id.to_string(),
                "actor_id": actor_id.to_string(),
                "timestamp": chrono::Utc::now().timestamp(),
            });
            let event_bus_event =
                crate::event_bus::Event::new("AppointmentGroupAddedEvent", event_json);
            if let Err(e) = publisher.fire(event_bus_event).await {
                eprintln!("Failed to fire AppointmentGroupAddedEvent: {:?}", e);
            }
        }

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
