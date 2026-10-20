use crate::appointments::events::{
    AppointmentCancelledEvent, AppointmentCreatedEvent, AppointmentDeletedEvent,
    AppointmentEditedEvent, AppointmentMovedEvent, AppointmentParticipationRemovedEvent,
    AppointmentParticipationRoleChangedEvent, AppointmentParticipationStatusChangedEvent,
};
use crate::appointments::models::{
    Appointment, AppointmentResponse, CreateAppointmentRequest, GroupDto, MessageDto,
    ParticipationStatus, UpdateAppointmentRequest, UserDto, UserParticipantDto, UserRole,
};
use crate::appointments::repository::{AppointmentRepository, RepositoryError};
use crate::event_bus::EventPublisher;
use crate::users::identity::UserIdentity;
use chrono::{DateTime, Duration, SecondsFormat, Utc};

/// Authorization messages of the Java `AuthorizationService`
pub const NO_ROLE_MESSAGE: &str = "You have no role at this appointment";
pub const NOT_ATTENDANT_MESSAGE: &str = "You have to be at least attendant of this appointment";
pub const NOT_RESPONSIBLE_MESSAGE: &str = "You have to be responsible for this appointment";

/// Custom error type for appointment service operations
#[derive(Debug, Clone)]
pub enum ServiceError {
    ValidationError(String),
    NotFound,
    DatabaseError(String),
    InvalidFormat(String),
    Forbidden(String),
}

impl std::fmt::Display for ServiceError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            ServiceError::ValidationError(msg) => write!(f, "Validation error: {}", msg),
            ServiceError::NotFound => write!(f, "Appointment not found"),
            ServiceError::DatabaseError(msg) => write!(f, "Database error: {}", msg),
            ServiceError::InvalidFormat(msg) => write!(f, "Invalid format: {}", msg),
            ServiceError::Forbidden(msg) => write!(f, "Forbidden: {}", msg),
        }
    }
}

impl std::error::Error for ServiceError {}

/// Java lists upcoming appointments only: `end` defaults to now plus 1000 years
const DEFAULT_WINDOW_DAYS: i64 = 365 * 1000;

/// Filters of the appointment list (Java: the `start`, `end` and `search` query parameters);
/// `start` defaults to now, `end` to now plus 1000 years
#[derive(Debug, Clone, Default)]
pub struct AppointmentSearch {
    pub start: Option<DateTime<Utc>>,
    pub end: Option<DateTime<Utc>>,
    pub search: Option<String>,
}

/// Which collections an appointment response embeds
#[derive(Debug, Clone, Copy, Default)]
pub struct Embed {
    pub participants: bool,
    pub messages: bool,
    pub group_participants: bool,
}

impl Embed {
    /// Everything: what Java's lazily loaded collections amount to in list, PATCH and admin list
    pub const ALL: Embed = Embed {
        participants: true,
        messages: true,
        group_participants: true,
    };
}

/// AppointmentService handles business logic for appointments
pub struct AppointmentService {
    repo: AppointmentRepository,
    event_publisher: Option<std::sync::Arc<dyn EventPublisher>>,
    /// Admin requests skip the participant checks (Java: `PrincipalContext#isAdminRequest`)
    admin: bool,
}

impl AppointmentService {
    /// Create a new AppointmentService
    pub fn new(repo: AppointmentRepository) -> Self {
        Self {
            repo,
            event_publisher: None,
            admin: false,
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
            admin: false,
        }
    }

    /// The service for a request under `/api/v2/admin/*`: authorization checks are skipped
    pub fn as_admin(mut self) -> Self {
        self.admin = true;
        self
    }

    /// Get a single appointment by ID
    pub async fn get_appointment(&self, id: i64) -> Result<Appointment, ServiceError> {
        self.repo
            .find_by_id(id)
            .await
            .map_err(|e| ServiceError::DatabaseError(e.to_string()))?
            .ok_or(ServiceError::NotFound)
    }

    /// Get a user's participation status for an appointment.
    /// Defaults to PENDING without a participation row (matches Java AppointmentParticipationQueryService).
    /// Does not check appointment existence or soft-delete state, matching the Java backend.
    pub async fn get_participation_status(
        &self,
        appointment_id: i64,
        user_id: &str,
    ) -> Result<ParticipationStatus, ServiceError> {
        let status = self
            .repo
            .find_participation_status(appointment_id, user_id)
            .await
            .map_err(|e| ServiceError::DatabaseError(e.to_string()))?;

        Ok(status.unwrap_or(ParticipationStatus::Pending))
    }

    /// List the participants of an appointment (Java: `AppointmentParticipationService#getParticipants`).
    /// Every user with a role other than NONE may read; this includes unknown appointments, which
    /// yield `Forbidden` like in Java. No soft-delete filtering: removed participants are hard-deleted.
    pub async fn get_participants(
        &self,
        appointment_id: i64,
        requesting_user_id: &str,
    ) -> Result<Vec<UserParticipantDto>, ServiceError> {
        let db_error = |e: RepositoryError| ServiceError::DatabaseError(e.to_string());

        let role = self
            .repo
            .get_participant_role(appointment_id, requesting_user_id)
            .await
            .map_err(db_error)?;
        if matches!(role, None | Some(UserRole::None)) {
            return Err(ServiceError::Forbidden(NO_ROLE_MESSAGE.to_string()));
        }

        let rows = self
            .repo
            .list_participants(appointment_id)
            .await
            .map_err(db_error)?;
        let user_ids: Vec<String> = rows.iter().map(|r| r.user_oidcid.clone()).collect();
        let users = self
            .repo
            .find_identities(&user_ids)
            .await
            .map_err(db_error)?;

        Ok(rows
            .into_iter()
            .map(|row| {
                let user = users.get(&row.user_oidcid);
                UserParticipantDto {
                    name: user.map(UserIdentity::name),
                    profile_picture_url: user.and_then(|u| u.profile_picture_url.clone()),
                    user_id: row.user_oidcid,
                    role: row.role,
                    status: row.status,
                    via_group_id: row.group_participation_id,
                    via_group_name: row.group_name,
                }
            })
            .collect())
    }

    /// One page of the appointments a user participates in (Java: `AppointmentRepository#search`)
    /// and the total number of matches
    pub async fn search_user_appointments(
        &self,
        user_id: &str,
        filter: &AppointmentSearch,
        page: i64,
        size: i64,
    ) -> Result<(Vec<Appointment>, i64), RepositoryError> {
        let now = Utc::now();
        self.repo
            .search_visible(
                user_id,
                filter.search.as_deref(),
                filter.start.unwrap_or(now),
                filter
                    .end
                    .unwrap_or(now + Duration::days(DEFAULT_WINDOW_DAYS)),
                page,
                size,
            )
            .await
    }

    /// Fills `participants`, `messages` and `group_participants` into the responses
    /// (Java: `AppointmentMapper#toDto` plus `AppointmentService#enrichAppointmentDtos`).
    /// Names come from the `user_profiles` cache; participants are ordered by id, messages
    /// oldest first (Java hands out unordered sets).
    pub async fn with_details(
        &self,
        responses: Vec<AppointmentResponse>,
        embed: Embed,
    ) -> Result<Vec<AppointmentResponse>, ServiceError> {
        let db_error = |e: RepositoryError| ServiceError::DatabaseError(e.to_string());
        let ids: Vec<i64> = responses.iter().map(|a| a.id).collect();

        let participants = if embed.participants {
            self.repo
                .list_participants_of(&ids)
                .await
                .map_err(db_error)?
        } else {
            Vec::new()
        };
        let messages = if embed.messages {
            self.repo.find_messages_of(&ids).await.map_err(db_error)?
        } else {
            Vec::new()
        };
        let group_participations = if embed.group_participants {
            self.repo
                .list_group_participations(&ids)
                .await
                .map_err(db_error)?
        } else {
            Vec::new()
        };
        let mut group_ids: Vec<i64> = group_participations.iter().map(|g| g.1).collect();
        group_ids.sort_unstable();
        group_ids.dedup();
        let members = self
            .repo
            .list_group_members(&group_ids)
            .await
            .map_err(db_error)?;

        let mut user_ids: Vec<String> = participants
            .iter()
            .map(|p| p.user_oidcid.clone())
            .chain(messages.iter().map(|m| m.sender_oidcid.clone()))
            .chain(members.iter().map(|m| m.1.clone()))
            .collect();
        user_ids.sort();
        user_ids.dedup();
        let users = self
            .repo
            .find_identities(&user_ids)
            .await
            .map_err(db_error)?;

        Ok(responses
            .into_iter()
            .map(|mut response| {
                let id = response.id;
                if embed.participants {
                    response.participants = Some(
                        participants
                            .iter()
                            .filter(|p| p.appointment_id == id)
                            .map(|row| {
                                let user = users.get(&row.user_oidcid);
                                UserParticipantDto {
                                    name: user.map(UserIdentity::name),
                                    profile_picture_url: user
                                        .and_then(|u| u.profile_picture_url.clone()),
                                    user_id: row.user_oidcid.clone(),
                                    role: row.role,
                                    status: row.status,
                                    via_group_id: row.group_participation_id,
                                    via_group_name: row.group_name.clone(),
                                }
                            })
                            .collect(),
                    );
                }
                if embed.messages {
                    response.messages = Some(
                        messages
                            .iter()
                            .filter(|m| m.appointment_id == Some(id))
                            .map(|m| MessageDto {
                                id: m.id,
                                sender_id: m.sender_oidcid.clone(),
                                sender_name: users
                                    .get(&m.sender_oidcid)
                                    .map(UserIdentity::name)
                                    .unwrap_or_default(),
                                appointment_id: m.appointment_id,
                                body: m.body.clone(),
                                timestamp: m
                                    .timestamp
                                    .map(|t| t.to_rfc3339_opts(SecondsFormat::AutoSi, true)),
                            })
                            .collect(),
                    );
                }
                if embed.group_participants {
                    response.group_participants = Some(
                        group_participations
                            .iter()
                            .filter(|g| g.0 == id)
                            .map(|(_, group_id, name)| GroupDto {
                                id: *group_id,
                                name: name.clone().unwrap_or_default(),
                                members: Some(
                                    members
                                        .iter()
                                        .filter(|m| m.0 == *group_id)
                                        .map(|m| {
                                            let user = users.get(&m.1);
                                            UserDto {
                                                id: m.1.clone(),
                                                first_name: user.and_then(|u| u.first_name.clone()),
                                                last_name: user.and_then(|u| u.last_name.clone()),
                                            }
                                        })
                                        .collect(),
                                ),
                            })
                            .collect(),
                    );
                }
                response
            })
            .collect())
    }

    /// Get appointments for a specific creator
    pub async fn get_creator_appointments(
        &self,
        creator_id: &str,
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
                &creator_id,
                request.minimal_attendees,
            )
            .await
            .map_err(|e| ServiceError::DatabaseError(e.to_string()))?;

        // The repository stored the creator as RESPONSIBLE participant in the same transaction.
        // Fire event after successful database commit
        if let Some(ref publisher) = self.event_publisher {
            let event = AppointmentCreatedEvent::new(appointment.id, creator_id);
            let event_json = serde_json::json!({
                "appointment_id": event.appointment_id,
                "creator_id": event.creator_id,
                "timestamp": event.timestamp,
            });
            let event_bus_event =
                crate::event_bus::Event::new("AppointmentCreatedEvent", event_json);
            if let Err(e) = publisher.fire(event_bus_event).await {
                tracing::error!("Failed to fire AppointmentCreatedEvent: {:?}", e);
                // Don't fail the request if event firing fails - this is a side effect
            }
        }

        Ok(appointment.into())
    }

    /// Update an appointment
    pub async fn update_appointment(
        &self,
        id: i64,
        request: UpdateAppointmentRequest,
        acting_user_id: &str,
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
                "appointment_id": edited_event.appointment_id,
                "timestamp": edited_event.timestamp,
            });
            let event_bus_event =
                crate::event_bus::Event::new("AppointmentEditedEvent", edited_json);
            if let Err(e) = publisher.fire(event_bus_event).await {
                tracing::error!("Failed to fire AppointmentEditedEvent: {:?}", e);
            }

            // Fire AppointmentMovedEvent if time changed
            if times_changed {
                let moved_event = AppointmentMovedEvent::new(
                    id,
                    existing.start_time,
                    existing.end_time,
                    acting_user_id.to_string(),
                );
                let moved_json = serde_json::json!({
                    "appointment_id": moved_event.appointment_id,
                    "old_start": moved_event.old_start,
                    "old_end": moved_event.old_end,
                    "acting_user_id": moved_event.acting_user_id,
                    "timestamp": moved_event.timestamp,
                });
                let event_bus_event =
                    crate::event_bus::Event::new("AppointmentMovedEvent", moved_json);
                if let Err(e) = publisher.fire(event_bus_event).await {
                    tracing::error!("Failed to fire AppointmentMovedEvent: {:?}", e);
                }
            }
        }

        Ok(updated.into())
    }

    /// Soft delete an appointment
    pub async fn delete_appointment(&self, id: i64) -> Result<(), ServiceError> {
        self.repo
            .delete_soft(id)
            .await
            .map_err(|e| ServiceError::DatabaseError(e.to_string()))?
            .ok_or(ServiceError::NotFound)?;

        // Fire event after successful database commit
        if let Some(ref publisher) = self.event_publisher {
            let event = AppointmentDeletedEvent::new(id);
            let event_json = serde_json::json!({
                "appointment_id": event.appointment_id,
                "timestamp": event.timestamp,
            });
            let event_bus_event =
                crate::event_bus::Event::new("AppointmentDeletedEvent", event_json);
            if let Err(e) = publisher.fire(event_bus_event).await {
                tracing::error!("Failed to fire AppointmentDeletedEvent: {:?}", e);
            }
        }

        Ok(())
    }

    /// Stores a system message (Java: `MessageService#persistMessage` called from the event
    /// observers) written by `user_id`; `text` receives the display name of that user.
    async fn persist_system_message(
        &self,
        appointment_id: i64,
        user_id: &str,
        text: impl FnOnce(&str) -> String,
    ) -> Result<(), ServiceError> {
        let db_err = |e: RepositoryError| ServiceError::DatabaseError(e.to_string());
        let name = self
            .repo
            .find_identities(&[user_id.to_string()])
            .await
            .map_err(db_err)?
            .remove(user_id)
            .unwrap_or_else(|| UserIdentity::deleted(user_id))
            .name();
        self.repo
            .persist_message(appointment_id, user_id, Some(&text(&name)))
            .await
            .map_err(db_err)?;
        Ok(())
    }

    /// Soft cancel an appointment
    pub async fn cancel_appointment(
        &self,
        id: i64,
        acting_user_id: &str,
    ) -> Result<(), ServiceError> {
        self.repo
            .cancel_soft(id)
            .await
            .map_err(|e| ServiceError::DatabaseError(e.to_string()))?
            .ok_or(ServiceError::NotFound)?;

        self.persist_system_message(id, acting_user_id, |name| {
            format!("{name} hat diesen Termin abgesagt. Er wird nicht stattfinden!")
        })
        .await?;

        // Fire event after successful database commit
        if let Some(ref publisher) = self.event_publisher {
            let event = AppointmentCancelledEvent::new(id, acting_user_id.to_string());
            let event_json = serde_json::json!({
                "appointment_id": event.appointment_id,
                "acting_user_id": event.acting_user_id,
                "timestamp": event.timestamp,
            });
            let event_bus_event =
                crate::event_bus::Event::new("AppointmentCancelledEvent", event_json);
            if let Err(e) = publisher.fire(event_bus_event).await {
                tracing::error!("Failed to fire AppointmentCancelledEvent: {:?}", e);
            }
        }

        Ok(())
    }

    /// Validate authorization and preconditions for adding a group to an appointment
    async fn validate_group_addition(
        &self,
        actor_id: &str,
        appointment_id: i64,
        group_id: i64,
    ) -> Result<(), ServiceError> {
        // Check if appointment exists
        let _appointment = self
            .repo
            .find_by_id(appointment_id)
            .await
            .map_err(|e| ServiceError::DatabaseError(e.to_string()))?
            .ok_or(ServiceError::NotFound)?;

        // Authorization: the creator is the RESPONSIBLE participant (the Java schema has no creator column)
        let actor_role = self
            .repo
            .get_participant_role(appointment_id, actor_id)
            .await
            .map_err(|e| ServiceError::DatabaseError(e.to_string()))?;

        if !self.admin && actor_role != Some(UserRole::Responsible) {
            return Err(ServiceError::ValidationError(
                "Only the appointment creator can add groups".to_string(),
            ));
        }

        // Check if group exists
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

        Ok(())
    }

    /// Add a group to an appointment
    /// Only the appointment creator can add groups
    pub async fn add_group_to_appointment(
        &self,
        actor_id: &str,
        appointment_id: i64,
        group_id: i64,
        role: UserRole,
    ) -> Result<(), ServiceError> {
        // Validate authorization and preconditions
        self.validate_group_addition(actor_id, appointment_id, group_id)
            .await?;

        // Mutate: add group to appointment
        self.repo
            .add_group_with_members(appointment_id, group_id, role)
            .await
            .map_err(|e| ServiceError::DatabaseError(e.to_string()))?;

        // Publish event after successful database commit. The group is already persisted, so a
        // publish failure is logged instead of reported as a failed request (as for the other events).
        if let Some(ref publisher) = self.event_publisher {
            let event_json = serde_json::json!({
                "appointment_id": appointment_id,
                "group_id": group_id,
                "actor_id": actor_id,
                "timestamp": chrono::Utc::now().timestamp(),
            });
            let event_bus_event =
                crate::event_bus::Event::new("AppointmentGroupAddedEvent", event_json);
            if let Err(e) = publisher.fire(event_bus_event).await {
                tracing::error!("Failed to fire AppointmentGroupAddedEvent: {:?}", e);
            }
        }

        Ok(())
    }

    /// Remove a group from an appointment (Java: `AppointmentParticipationService#removeGroupFromAppointment`).
    /// Hard delete of the group link and of all participations that came in through the group.
    /// Authorization comes first: the caller needs the RESPONSIBLE role, so an unknown
    /// appointment yields `Forbidden` like in Java. A group that is not linked is a validation error.
    pub async fn remove_group_from_appointment(
        &self,
        actor_id: &str,
        appointment_id: i64,
        group_id: i64,
    ) -> Result<(), ServiceError> {
        let db_error = |e: RepositoryError| ServiceError::DatabaseError(e.to_string());

        let role = self
            .repo
            .get_participant_role(appointment_id, actor_id)
            .await
            .map_err(db_error)?;
        if !self.admin && role != Some(UserRole::Responsible) {
            return Err(ServiceError::Forbidden(NOT_RESPONSIBLE_MESSAGE.to_string()));
        }

        let removed = self
            .repo
            .remove_group(appointment_id, group_id)
            .await
            .map_err(db_error)?;
        if removed < 1 {
            return Err(ServiceError::ValidationError(
                "This group is not a participant of this appointment".to_string(),
            ));
        }
        self.repo
            .remove_group_participants(appointment_id, group_id)
            .await
            .map_err(db_error)?;

        // The removal is persisted, so a publish failure is logged instead of failing the request.
        if let Some(ref publisher) = self.event_publisher {
            let event_json = serde_json::json!({
                "appointment_id": appointment_id,
                "group_id": group_id,
                "actor_id": actor_id,
                "timestamp": chrono::Utc::now().timestamp(),
            });
            let event = crate::event_bus::Event::new(
                "AppointmentGroupParticipationRemovedEvent",
                event_json,
            );
            if let Err(e) = publisher.fire(event).await {
                tracing::error!(
                    "Failed to fire AppointmentGroupParticipationRemovedEvent: {:?}",
                    e
                );
            }
        }

        Ok(())
    }

    /// One page of all appointments of any status, ordered by id, plus the total count (admin listing)
    pub async fn list_page(
        &self,
        page: i64,
        size: i64,
    ) -> Result<(Vec<Appointment>, i64), RepositoryError> {
        let appointments = self.repo.find_page(page * size, size).await?;
        Ok((appointments, self.repo.count().await?))
    }

    /// Store a chat message and fire `MessageSentEvent` (Java: `MessageService#sendMessage`).
    /// The sender needs no role at the appointment (only the admin endpoint stores messages so
    /// far); an unknown appointment stores the message without one, like Java's `findById`
    /// returning `null`.
    pub async fn send_message(
        &self,
        appointment_id: i64,
        body: &str,
        sender_id: &str,
        timestamp: DateTime<Utc>,
    ) -> Result<(), ServiceError> {
        let db_err = |e: RepositoryError| ServiceError::DatabaseError(e.to_string());
        let appointment_id = self
            .repo
            .find_by_id_any_status(appointment_id)
            .await
            .map_err(db_err)?
            .map(|appointment| appointment.id);
        let message_id = self
            .repo
            .insert_message(appointment_id, body, sender_id, timestamp)
            .await
            .map_err(db_err)?;
        if let Some(ref publisher) = self.event_publisher {
            let event = crate::event_bus::Event::new(
                crate::push_notifications::events::MESSAGE_SENT,
                serde_json::json!({ "message_id": message_id }),
            );
            if let Err(e) = publisher.fire(event).await {
                tracing::error!("Failed to fire MessageSentEvent: {:?}", e);
            }
        }
        Ok(())
    }

    /// Change a user's participation status in an appointment
    pub async fn change_participation_status(
        &self,
        appointment_id: i64,
        user_id: &str,
        new_status: ParticipationStatus,
    ) -> Result<(), ServiceError> {
        if new_status == ParticipationStatus::Pending {
            return Err(ServiceError::ValidationError(
                "you cannot set your participation status back to pending".to_string(),
            ));
        }

        let current_status = self
            .repo
            .find_participation_status(appointment_id, user_id)
            .await
            .map_err(|e| ServiceError::DatabaseError(e.to_string()))?
            .ok_or_else(|| {
                ServiceError::ValidationError(
                    "This user is not a participant of this event".to_string(),
                )
            })?;

        if current_status == new_status {
            return Err(ServiceError::ValidationError(
                "this is already your participation status".to_string(),
            ));
        }

        self.repo
            .update_participation_status(appointment_id, user_id, new_status)
            .await
            .map_err(|e| ServiceError::DatabaseError(e.to_string()))?;

        self.persist_system_message(appointment_id, user_id, |name| {
            let verb = if new_status == ParticipationStatus::Approved {
                "zugesagt"
            } else {
                "abgesagt"
            };
            format!("{name} hat {verb}")
        })
        .await?;

        // Fire event after successful database commit
        if let Some(ref publisher) = self.event_publisher {
            let event = AppointmentParticipationStatusChangedEvent::new(
                appointment_id,
                user_id.to_string(),
                new_status,
                current_status,
            );
            let event_json = serde_json::json!({
                "appointment_id": event.appointment_id,
                "user_id": event.user_id,
                "new_status": event.new_status,
                "old_status": event.old_status,
                "timestamp": event.timestamp,
            });
            let event_bus_event = crate::event_bus::Event::new(
                "AppointmentParticipationStatusChangedEvent",
                event_json,
            );
            if let Err(e) = publisher.fire(event_bus_event).await {
                eprintln!(
                    "Failed to fire AppointmentParticipationStatusChangedEvent: {:?}",
                    e
                );
            }
        }

        Ok(())
    }

    /// Change the role of a participant (Java: `changeUserRole`)
    /// Requires the acting user to be RESPONSIBLE in the appointment
    pub async fn change_participant_role(
        &self,
        appointment_id: i64,
        target_user_id: &str,
        acting_user_id: &str,
        new_role: UserRole,
    ) -> Result<(), ServiceError> {
        self.repo
            .find_by_id_any_status(appointment_id)
            .await
            .map_err(|e| ServiceError::DatabaseError(e.to_string()))?
            .ok_or(ServiceError::NotFound)?;

        let acting_user_role = self
            .repo
            .get_participant_role(appointment_id, acting_user_id)
            .await
            .map_err(|e| ServiceError::DatabaseError(e.to_string()))?;

        if acting_user_role != Some(UserRole::Responsible) {
            return Err(ServiceError::Forbidden(NOT_RESPONSIBLE_MESSAGE.to_string()));
        }

        let old_role = self
            .repo
            .get_participant_role(appointment_id, target_user_id)
            .await
            .map_err(|e| ServiceError::DatabaseError(e.to_string()))?
            .ok_or_else(|| {
                ServiceError::ValidationError(
                    "This user is not a participant of this event".to_string(),
                )
            })?;

        if old_role == new_role {
            return Err(ServiceError::ValidationError(
                "this is already the users role".to_string(),
            ));
        }

        self.repo
            .update_participant_role(appointment_id, target_user_id, new_role)
            .await
            .map_err(|e| ServiceError::DatabaseError(e.to_string()))?;

        if let Some(ref publisher) = self.event_publisher {
            let event = AppointmentParticipationRoleChangedEvent::new(
                appointment_id,
                target_user_id.to_string(),
                acting_user_id.to_string(),
                old_role,
            );
            let event_json = serde_json::json!({
                "appointment_id": event.appointment_id,
                "target_user_id": event.target_user_id,
                "acting_user_id": event.acting_user_id,
                "old_role": event.old_role,
                "timestamp": event.timestamp,
            });
            let event_bus_event = crate::event_bus::Event::new(
                "AppointmentParticipationRoleChangedEvent",
                event_json,
            );
            if let Err(e) = publisher.fire(event_bus_event).await {
                eprintln!(
                    "Failed to fire AppointmentParticipationRoleChangedEvent: {:?}",
                    e
                );
            }
        }

        Ok(())
    }

    /// Add a participant to an appointment
    /// Requires the acting user to be RESPONSIBLE in the appointment
    pub async fn add_participant(
        &self,
        appointment_id: i64,
        target_user_id: &str,
        acting_user_id: &str,
        user_role: &str,
    ) -> Result<(), ServiceError> {
        // Check if appointment exists (allow any status - spec requires adding to DELETED/CANCELLED appointments)
        let _appointment = self
            .repo
            .find_by_id_any_status(appointment_id)
            .await
            .map_err(|e| ServiceError::DatabaseError(e.to_string()))?
            .ok_or(ServiceError::NotFound)?;

        // Check authorization - acting user must be RESPONSIBLE
        let acting_user_role = self
            .repo
            .get_participant_role(appointment_id, acting_user_id)
            .await
            .map_err(|e| ServiceError::DatabaseError(e.to_string()))?;

        match acting_user_role {
            Some(crate::appointments::models::UserRole::Responsible) => {
                // User has permission to add participants
            }
            _ => {
                return Err(ServiceError::ValidationError(
                    "Only RESPONSIBLE participants can add participants".to_string(),
                ));
            }
        }

        // Add the participant with PENDING status
        self.repo
            .add_participant_strict(appointment_id, target_user_id, user_role, "PENDING")
            .await
            .map_err(|e| match e {
                crate::appointments::repository::RepositoryError::InvalidInput(msg) => {
                    ServiceError::ValidationError(msg)
                }
                _ => ServiceError::DatabaseError(e.to_string()),
            })?;

        // Fire the event
        if let Some(ref publisher) = self.event_publisher {
            let event = crate::appointments::events::AppointmentParticipationAddedEvent::new(
                appointment_id,
                target_user_id.to_string(),
                acting_user_id.to_string(),
            );
            let event_json = serde_json::json!({
                "appointment_id": event.appointment_id,
                "target_user_id": event.target_user_id,
                "acting_user_id": event.acting_user_id,
                "timestamp": event.timestamp,
            });
            let event_bus_event =
                crate::event_bus::Event::new("AppointmentParticipationAddedEvent", event_json);
            if let Err(e) = publisher.fire(event_bus_event).await {
                eprintln!("Failed to fire AppointmentParticipationAddedEvent: {:?}", e);
            }
        }

        Ok(())
    }

    /// Remove a participant from an appointment (hard delete)
    /// Requires the acting user to be RESPONSIBLE in the appointment
    pub async fn remove_participant(
        &self,
        appointment_id: i64,
        target_user_id: &str,
        acting_user_id: &str,
    ) -> Result<(), ServiceError> {
        self.repo
            .find_by_id_any_status(appointment_id)
            .await
            .map_err(|e| ServiceError::DatabaseError(e.to_string()))?
            .ok_or(ServiceError::NotFound)?;

        let acting_user_role = self
            .repo
            .get_participant_role(appointment_id, acting_user_id)
            .await
            .map_err(|e| ServiceError::DatabaseError(e.to_string()))?;

        if !matches!(
            acting_user_role,
            Some(crate::appointments::models::UserRole::Responsible)
        ) {
            return Err(ServiceError::ValidationError(
                "Only RESPONSIBLE participants can remove participants".to_string(),
            ));
        }

        self.repo
            .remove_participant(appointment_id, target_user_id)
            .await
            .map_err(|e| match e {
                RepositoryError::NotFound => {
                    ServiceError::ValidationError("Participant not found".to_string())
                }
                _ => ServiceError::DatabaseError(e.to_string()),
            })?;

        // Fire event after successful database commit
        if let Some(ref publisher) = self.event_publisher {
            let event = AppointmentParticipationRemovedEvent::new(
                appointment_id,
                target_user_id.to_string(),
                acting_user_id.to_string(),
            );
            let event_json = serde_json::json!({
                "appointment_id": event.appointment_id,
                "target_user_id": event.target_user_id,
                "acting_user_id": event.acting_user_id,
                "timestamp": event.timestamp,
            });
            let event_bus_event =
                crate::event_bus::Event::new("AppointmentParticipationRemovedEvent", event_json);
            if let Err(e) = publisher.fire(event_bus_event).await {
                eprintln!(
                    "Failed to fire AppointmentParticipationRemovedEvent: {:?}",
                    e
                );
            }
        }

        Ok(())
    }
}
