use axum::{
    extract::{Path, Query, State},
    http::StatusCode,
    response::IntoResponse,
    Json,
};
use serde::Deserialize;
use std::str::FromStr;
use std::sync::Arc;

use crate::appointments::{
    models::{
        AddGroupToAppointmentRequest, AddParticipantRequest, AppointmentResponse,
        CreateAppointmentRequest, CreateMessageRequest, Message, MessageDto,
        ParticipationStatusResponse, UpdateAppointmentRequest, UpdateParticipationStatusRequest,
        UserRole,
    },
    repository::AppointmentRepository,
    services::{AppointmentService, ServiceError},
};
use crate::event_bus::EventPublisher;
use crate::security::PrincipalContext;
use crate::users::identity::{IdentityRepository, UserIdentity};
use chrono::SecondsFormat;

/// Shared application state
#[derive(Clone)]
pub struct AppState {
    pub db_pool: sqlx::PgPool,
    pub event_publisher: Arc<dyn EventPublisher>,
}

/// Query parameters for listing appointments
#[derive(Debug, Deserialize)]
pub struct ListQuery {
    pub limit: Option<u32>,
    pub offset: Option<u32>,
    /// Sort by field: "date" (start_time, default) or "title"
    pub sort_by: Option<String>,
    /// Sort direction: "asc" or "desc" (default is "desc" for date, "asc" for title)
    pub sort_dir: Option<String>,
}

/// GET /api/v2/appointments/:id - Fetch a single appointment by ID
pub async fn get_appointment(
    State(state): State<Arc<AppState>>,
    Path(id): Path<i64>,
    principal: PrincipalContext,
) -> Result<impl IntoResponse, AppointmentError> {
    let repo = AppointmentRepository::new(state.db_pool.clone());
    let service = AppointmentService::with_events(repo, state.event_publisher.clone());

    // The authenticated principal holds the user's OIDC subject
    let user_id = principal.user_id();

    // Fetch the appointment
    let appointment = service.get_appointment(id).await.map_err(|e| match e {
        crate::appointments::services::ServiceError::NotFound => AppointmentError::NotFound,
        _ => AppointmentError::DatabaseError,
    })?;

    // Authorization check - the creator (RESPONSIBLE participant) may view the appointment
    // For now, only allow creators to view their appointments
    // TODO: Also allow the other participants of the appointment
    ensure_creator(&state, appointment.id, user_id).await?;

    let response: AppointmentResponse = appointment.into();
    Ok(Json(response))
}

/// GET /api/v2/appointments/:id/participation/status - Current user's participation status
/// Always 200 OK; PENDING when the user has no participation row (matches Java backend)
pub async fn get_participation_status(
    State(state): State<Arc<AppState>>,
    Path(id): Path<i64>,
    principal: PrincipalContext,
) -> Result<impl IntoResponse, AppointmentError> {
    let repo = AppointmentRepository::new(state.db_pool.clone());
    let service = AppointmentService::with_events(repo, state.event_publisher.clone());

    let status = service
        .get_participation_status(id, principal.user_id())
        .await
        .map_err(AppointmentError::from)?;

    Ok(Json(ParticipationStatusResponse { status }))
}

/// GET /api/v2/appointments/:id/messages - Messages of an appointment, newest first
///
/// Any participant (role other than NONE) may read; everybody else, including callers of an
/// unknown appointment, gets 403 (Java: `requireReadAppointment`). Sender names come from the
/// `user_profiles` cache; senders without a profile are named "Gelöschter Benutzer".
pub async fn list_messages(
    State(state): State<Arc<AppState>>,
    Path(id): Path<i64>,
    principal: PrincipalContext,
) -> Result<impl IntoResponse, AppointmentError> {
    let repo = AppointmentRepository::new(state.db_pool.clone());

    let role = repo
        .get_participant_role(id, principal.user_id())
        .await
        .map_err(|_| AppointmentError::DatabaseError)?;
    if matches!(role, None | Some(UserRole::None)) {
        return Err(AppointmentError::Unauthorized);
    }

    let messages = repo
        .find_messages(id)
        .await
        .map_err(|_| AppointmentError::DatabaseError)?;

    let mut sender_ids: Vec<String> = messages.iter().map(|m| m.sender_oidcid.clone()).collect();
    sender_ids.sort();
    sender_ids.dedup();
    let senders = IdentityRepository::find_by_ids(&state.db_pool, &sender_ids)
        .await
        .map_err(|_| AppointmentError::DatabaseError)?;

    let responses: Vec<MessageDto> = messages
        .into_iter()
        .map(|m| {
            let sender_name = senders
                .get(&m.sender_oidcid)
                .map(|s| s.name())
                .unwrap_or_default();
            message_dto(m, sender_name)
        })
        .collect();

    Ok(Json(responses))
}

/// POST /api/v2/appointments/:id/messages - Post a message to an appointment
///
/// Any participant (role other than NONE) may post; everybody else, including callers of an
/// unknown appointment, gets 403 (Java: `requireSendMessage`). Answers 200 (Java `Response.ok`,
/// the 201 of the first ticket answer was superseded) with the stored message,
/// `sender_name` is the caller's name from the access token. After the insert a
/// `MessageSentEvent {message_id}` is fired (Java: `MessageService#sendMessage`).
pub async fn send_message(
    State(state): State<Arc<AppState>>,
    Path(id): Path<i64>,
    principal: PrincipalContext,
    Json(request): Json<CreateMessageRequest>,
) -> Result<impl IntoResponse, AppointmentError> {
    let repo = AppointmentRepository::new(state.db_pool.clone());

    let role = repo
        .get_participant_role(id, principal.user_id())
        .await
        .map_err(|_| AppointmentError::DatabaseError)?;
    if matches!(role, None | Some(UserRole::None)) {
        return Err(AppointmentError::Unauthorized);
    }

    let message = repo
        .insert_message(id, principal.user_id(), request.body.as_deref())
        .await
        .map_err(|_| AppointmentError::DatabaseError)?;

    let event = crate::event_bus::Event::new(
        crate::push_notifications::events::MESSAGE_SENT,
        serde_json::json!({ "message_id": message.id }),
    );
    // The message is already stored, so a failing publisher must not fail the request
    if let Err(e) = state.event_publisher.fire(event).await {
        tracing::warn!(
            "Failed to publish MessageSentEvent for message {}: {}",
            message.id,
            e
        );
    }

    Ok(Json(message_dto(
        message,
        UserIdentity::from(&principal).name(),
    )))
}

/// Maps a stored message to the Java `MessageDto`
fn message_dto(message: Message, sender_name: String) -> MessageDto {
    MessageDto {
        id: message.id,
        sender_id: message.sender_oidcid,
        sender_name,
        appointment_id: message.appointment_id,
        body: message.body,
        timestamp: message
            .timestamp
            .map(|t| t.to_rfc3339_opts(SecondsFormat::AutoSi, true)),
    }
}

/// POST /api/v2/appointments/:id/participation - RSVP to an appointment
pub async fn rsvp_to_appointment(
    State(state): State<Arc<AppState>>,
    Path(id): Path<i64>,
    principal: PrincipalContext,
    Json(request): Json<UpdateParticipationStatusRequest>,
) -> Result<impl IntoResponse, AppointmentError> {
    let user_id = principal.user_id();

    let repo = AppointmentRepository::new(state.db_pool.clone());

    // Authorization check - user must be a participant in the appointment
    let participant_role = repo
        .get_participant_role(id, user_id)
        .await
        .map_err(|_| AppointmentError::DatabaseError)?;

    if participant_role == Some(UserRole::None) || participant_role.is_none() {
        return Err(AppointmentError::Unauthorized);
    }

    let service = AppointmentService::with_events(repo, state.event_publisher.clone());

    service
        .change_participation_status(id, user_id, request.status)
        .await
        .map_err(AppointmentError::from)?;

    Ok(StatusCode::OK)
}

/// GET /api/v2/appointments - List appointments with pagination
pub async fn list_appointments(
    State(state): State<Arc<AppState>>,
    Query(query): Query<ListQuery>,
    principal: PrincipalContext,
) -> Result<impl IntoResponse, AppointmentError> {
    let repo = AppointmentRepository::new(state.db_pool.clone());
    let service = AppointmentService::with_events(repo, state.event_publisher.clone());

    let user_id = principal.user_id();

    // List only appointments visible to this user
    let appointments = service
        .list_user_appointments(
            user_id,
            crate::appointments::services::ListAppointmentsQuery {
                limit: query.limit.or(Some(20)),
                offset: query.offset.or(Some(0)),
                sort_by: query.sort_by.clone(),
                sort_dir: query.sort_dir.clone(),
            },
        )
        .await
        .map_err(|_| AppointmentError::DatabaseError)?;

    let responses: Vec<AppointmentResponse> = appointments.into_iter().map(|a| a.into()).collect();

    Ok(Json(responses))
}

/// POST /api/v2/appointments - Create a new appointment
pub async fn create_appointment(
    State(state): State<Arc<AppState>>,
    principal: PrincipalContext,
    Json(request): Json<CreateAppointmentRequest>,
) -> Result<impl IntoResponse, AppointmentError> {
    let repo = AppointmentRepository::new(state.db_pool.clone());
    let service = AppointmentService::with_events(repo, state.event_publisher.clone());

    // Get creator ID from principal context
    let creator_id = principal.user_id().to_string();

    // Validate and create appointment
    let response = service
        .create_appointment(request, creator_id)
        .await
        .map_err(|e| {
            eprintln!("Appointment creation error: {}", e);
            AppointmentError::from(e)
        })?;

    Ok((StatusCode::CREATED, Json(response)))
}

/// PATCH /api/v2/appointments/:id - Update an appointment
/// Requires ATTENDANT role or above (any participant can edit)
pub async fn update_appointment(
    State(state): State<Arc<AppState>>,
    Path(id): Path<i64>,
    principal: PrincipalContext,
    Json(request): Json<UpdateAppointmentRequest>,
) -> Result<impl IntoResponse, AppointmentError> {
    let user_id = principal.user_id();

    let repo = AppointmentRepository::new(state.db_pool.clone());

    // Check if appointment exists and user is authorized
    let appointment = repo
        .find_by_id(id)
        .await
        .map_err(|_| AppointmentError::DatabaseError)?
        .ok_or(AppointmentError::NotFound)?;

    // User must be a participant with ATTENDANT role or above (the creator is RESPONSIBLE)
    let participant_role = repo
        .get_participant_role(appointment.id, user_id)
        .await
        .map_err(|_| AppointmentError::DatabaseError)?;

    let is_attendant_or_above = matches!(
        participant_role,
        Some(UserRole::Attendant | UserRole::Helper | UserRole::Responsible)
    );
    if !is_attendant_or_above {
        return Err(AppointmentError::Unauthorized);
    }

    let service = AppointmentService::with_events(repo, state.event_publisher.clone());

    // Validate and update appointment
    let response = service
        .update_appointment(id, request, user_id)
        .await
        .map_err(AppointmentError::from)?;

    Ok(Json(response))
}

/// DELETE /api/v2/appointments/:id - Delete an appointment (soft delete)
/// Requires RESPONSIBLE role (creator only)
pub async fn delete_appointment(
    State(state): State<Arc<AppState>>,
    Path(id): Path<i64>,
    principal: PrincipalContext,
) -> Result<impl IntoResponse, AppointmentError> {
    let user_id = principal.user_id();

    let repo = AppointmentRepository::new(state.db_pool.clone());

    // Check if appointment exists and user is the creator
    let appointment = repo
        .find_by_id(id)
        .await
        .map_err(|_| AppointmentError::DatabaseError)?
        .ok_or(AppointmentError::NotFound)?;

    ensure_creator(&state, appointment.id, user_id).await?;

    let service = AppointmentService::with_events(repo, state.event_publisher.clone());

    service
        .delete_appointment(id)
        .await
        .map_err(AppointmentError::from)?;

    Ok(StatusCode::OK)
}

/// POST /api/v2/appointments/:id/cancel - Cancel an appointment (soft cancel)
/// Requires RESPONSIBLE role (creator only)
pub async fn cancel_appointment(
    State(state): State<Arc<AppState>>,
    Path(id): Path<i64>,
    principal: PrincipalContext,
) -> Result<impl IntoResponse, AppointmentError> {
    let user_id = principal.user_id();

    let repo = AppointmentRepository::new(state.db_pool.clone());

    // Check if appointment exists and user is the creator
    let appointment = repo
        .find_by_id(id)
        .await
        .map_err(|_| AppointmentError::DatabaseError)?
        .ok_or(AppointmentError::NotFound)?;

    ensure_creator(&state, appointment.id, user_id).await?;

    let service = AppointmentService::with_events(repo, state.event_publisher.clone());

    service
        .cancel_appointment(id, user_id)
        .await
        .map_err(AppointmentError::from)?;

    Ok(StatusCode::OK)
}

/// The creator of an appointment is its RESPONSIBLE participant (the Java schema has no creator column)
async fn ensure_creator(
    state: &AppState,
    appointment_id: i64,
    user_id: &str,
) -> Result<(), AppointmentError> {
    let role = AppointmentRepository::new(state.db_pool.clone())
        .get_participant_role(appointment_id, user_id)
        .await
        .map_err(|_| AppointmentError::DatabaseError)?;

    match role {
        Some(UserRole::Responsible) => Ok(()),
        _ => Err(AppointmentError::Unauthorized),
    }
}

/// POST /api/v2/appointments/:id/groups/:groupId - Add a group to an appointment
/// Requires the RESPONSIBLE role (only the creator can add groups)
pub async fn add_group_to_appointment(
    State(state): State<Arc<AppState>>,
    Path((appointment_id, group_id)): Path<(i64, i64)>,
    principal: PrincipalContext,
    Json(request): Json<AddGroupToAppointmentRequest>,
) -> Result<impl IntoResponse, AppointmentError> {
    let user_id = principal.user_id();

    let repo = AppointmentRepository::new(state.db_pool.clone());
    let service = AppointmentService::with_events(repo, state.event_publisher.clone());

    let role = UserRole::from_str(&request.role)
        .map_err(|_| AppointmentError::ValidationError("Invalid role".to_string()))?;

    service
        .add_group_to_appointment(user_id, appointment_id, group_id, role)
        .await
        .map_err(AppointmentError::from)?;

    Ok(StatusCode::OK)
}

/// DELETE /api/v2/appointments/:id/participants/:userId - Remove a participant (hard delete)
///
/// Only a RESPONSIBLE participant may remove participants. Fires an
/// `AppointmentParticipationRemovedEvent`. Returns 404 if the appointment or participant is
/// unknown and 403 if the caller is not RESPONSIBLE.
pub async fn remove_participant(
    State(state): State<Arc<AppState>>,
    Path((appointment_id, target_user_id)): Path<(i64, String)>,
    principal: PrincipalContext,
) -> Result<impl IntoResponse, AppointmentError> {
    let acting_user_id = principal.user_id();

    let repo = AppointmentRepository::new(state.db_pool.clone());
    let service = AppointmentService::with_events(repo, state.event_publisher.clone());

    service
        .remove_participant(appointment_id, &target_user_id, acting_user_id)
        .await
        .map_err(AppointmentError::from)?;

    Ok(StatusCode::OK)
}

/// Errors that can occur in appointment handlers
#[derive(Debug)]
pub enum AppointmentError {
    NotFound,
    Unauthorized,
    DatabaseError,
    ValidationError(String),
}

impl From<ServiceError> for AppointmentError {
    fn from(error: ServiceError) -> Self {
        match error {
            ServiceError::NotFound => AppointmentError::NotFound,
            ServiceError::ValidationError(msg) => AppointmentError::ValidationError(msg),
            ServiceError::DatabaseError(msg) => {
                eprintln!("Database error: {}", msg);
                AppointmentError::DatabaseError
            }
            ServiceError::InvalidFormat(msg) => AppointmentError::ValidationError(msg),
        }
    }
}

impl IntoResponse for AppointmentError {
    fn into_response(self) -> axum::response::Response {
        let (status, error_message) = match self {
            AppointmentError::NotFound => {
                (StatusCode::NOT_FOUND, "Appointment not found".to_string())
            }
            AppointmentError::Unauthorized => (StatusCode::FORBIDDEN, "Unauthorized".to_string()),
            AppointmentError::DatabaseError => (
                StatusCode::INTERNAL_SERVER_ERROR,
                "Database error".to_string(),
            ),
            AppointmentError::ValidationError(msg) => (StatusCode::BAD_REQUEST, msg),
        };

        (status, error_message).into_response()
    }
}

/// POST /api/v2/appointments/:id/participants/:userId - Add a participant to an appointment
pub async fn add_participant(
    State(state): State<Arc<AppState>>,
    Path((appointment_id, target_user_id)): Path<(i64, String)>,
    principal: PrincipalContext,
    Json(request): Json<AddParticipantRequest>,
) -> Result<impl IntoResponse, AppointmentError> {
    let acting_user_id = principal.user_id();

    let repo = AppointmentRepository::new(state.db_pool.clone());
    let service = AppointmentService::with_events(repo, state.event_publisher.clone());

    service
        .add_participant(
            appointment_id,
            &target_user_id,
            acting_user_id,
            &request.user_role.to_string(),
        )
        .await
        .map_err(AppointmentError::from)?;

    Ok(StatusCode::OK)
}
