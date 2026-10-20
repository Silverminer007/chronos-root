use axum::{
    extract::{Query, State},
    http::StatusCode,
    response::IntoResponse,
    Json,
};
use std::collections::HashMap;
use std::str::FromStr;
use std::sync::Arc;

use crate::appointments::{
    models::{
        AddGroupToAppointmentRequest, AddParticipantRequest, ChangeParticipantRoleRequest,
        CreateAppointmentRequest, CreateMessageRequest, Message, MessageDto, PageMeta,
        PagedResponse, ParticipationStatusResponse, UpdateAppointmentRequest,
        UpdateParticipationStatusRequest, UserRole,
    },
    repository::AppointmentRepository,
    services::{
        AppointmentSearch, AppointmentService, Embed, ServiceError, NOT_ATTENDANT_MESSAGE,
        NOT_RESPONSIBLE_MESSAGE, NO_ROLE_MESSAGE,
    },
};
use crate::error::{AppError, AppJson, AppPath};
use crate::event_bus::EventPublisher;
use crate::security::PrincipalContext;
use crate::users::identity::{IdentityRepository, UserIdentity};
use chrono::{DateTime, SecondsFormat, Utc};

/// Shared application state
#[derive(Clone)]
pub struct AppState {
    pub db_pool: sqlx::PgPool,
    pub event_publisher: Arc<dyn EventPublisher>,
}

/// Default of the `page` query parameter of `GET /appointments` (Java `getAgenda`)
const DEFAULT_PAGE: i64 = 0;
/// Default of the `size` query parameter of `GET /appointments` (Java `getAgenda`)
const DEFAULT_PAGE_SIZE: i64 = 10;

/// A `Boolean` query parameter: only `true` switches it on (Java `Boolean.parseBoolean`)
fn flag(query: &HashMap<String, String>, name: &str) -> bool {
    query
        .get(name)
        .is_some_and(|value| value.eq_ignore_ascii_case("true"))
}

/// An `Integer` query parameter: absent means `default`, unparsable is a 404 (Jakarta REST)
fn int_param(query: &HashMap<String, String>, name: &str, default: i64) -> Result<i64, AppError> {
    match query.get(name) {
        None => Ok(default),
        Some(raw) => raw
            .parse::<i32>()
            .map(i64::from)
            .map_err(|_| AppError::RouteNotFound),
    }
}

/// An optional ISO-8601 instant (Java `Instant.parse`); unparsable is a 400 here, Java answers 500
fn instant_param(
    query: &HashMap<String, String>,
    name: &str,
) -> Result<Option<DateTime<Utc>>, AppError> {
    query
        .get(name)
        .map(|raw| {
            DateTime::parse_from_rfc3339(raw)
                .map(|time| time.with_timezone(&Utc))
                .map_err(|_| {
                    AppError::bad_request(format!("{name} must be a valid ISO-8601 instant"))
                })
        })
        .transpose()
}

/// GET /api/v2/appointments/:id - Fetch a single appointment by ID
pub async fn get_appointment(
    State(state): State<Arc<AppState>>,
    AppPath(id): AppPath<i64>,
    Query(query): Query<HashMap<String, String>>,
    principal: PrincipalContext,
) -> Result<impl IntoResponse, AppointmentError> {
    let repo = AppointmentRepository::new(state.db_pool.clone());
    let service = AppointmentService::with_events(repo, state.event_publisher.clone());

    // The authenticated principal holds the user's OIDC subject
    let user_id = principal.user_id();

    // Authorization first, like Java: an unknown appointment has no participants, hence a 403.
    // For now, only allow creators (RESPONSIBLE participants) to view their appointments
    // TODO: Also allow the other participants of the appointment
    ensure_creator(&state, id, user_id, NO_ROLE_MESSAGE).await?;

    let appointment = service
        .get_appointment(id)
        .await
        .map_err(AppointmentError::from)?;

    // Java embeds what the flags ask for; unlike the list, the group flag is `group_participants`
    let embed = Embed {
        participants: flag(&query, "participants"),
        messages: flag(&query, "messages"),
        group_participants: flag(&query, "group_participants"),
    };
    let mut responses = service
        .with_details(vec![appointment.into()], embed)
        .await
        .map_err(AppointmentError::from)?;
    Ok(Json(responses.remove(0)))
}

/// GET /api/v2/appointments/:id/participation/status - Current user's participation status
/// Always 200 OK; PENDING when the user has no participation row (matches Java backend)
pub async fn get_participation_status(
    State(state): State<Arc<AppState>>,
    AppPath(id): AppPath<i64>,
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

/// GET /api/v2/appointments/:id/participants - List the participants with roles and status
/// 403 unless the caller participates with a role other than NONE (matches Java backend)
pub async fn list_participants(
    State(state): State<Arc<AppState>>,
    AppPath(id): AppPath<i64>,
    principal: PrincipalContext,
) -> Result<impl IntoResponse, AppointmentError> {
    let repo = AppointmentRepository::new(state.db_pool.clone());
    let service = AppointmentService::with_events(repo, state.event_publisher.clone());

    let participants = service
        .get_participants(id, principal.user_id())
        .await
        .map_err(AppointmentError::from)?;

    Ok(Json(participants))
}

/// GET /api/v2/appointments/:id/messages - Messages of an appointment, newest first
///
/// Any participant (role other than NONE) may read; everybody else, including callers of an
/// unknown appointment, gets 403 (Java: `requireReadAppointment`). Sender names come from the
/// `user_profiles` cache; senders without a profile are named "Gelöschter Benutzer".
pub async fn list_messages(
    State(state): State<Arc<AppState>>,
    AppPath(id): AppPath<i64>,
    principal: PrincipalContext,
) -> Result<impl IntoResponse, AppointmentError> {
    let repo = AppointmentRepository::new(state.db_pool.clone());

    let role = repo
        .get_participant_role(id, principal.user_id())
        .await
        .map_err(|_| AppointmentError::DatabaseError)?;
    if matches!(role, None | Some(UserRole::None)) {
        return Err(AppointmentError::Forbidden(NO_ROLE_MESSAGE.to_string()));
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
    AppPath(id): AppPath<i64>,
    principal: PrincipalContext,
    AppJson(request): AppJson<CreateMessageRequest>,
) -> Result<impl IntoResponse, AppointmentError> {
    let repo = AppointmentRepository::new(state.db_pool.clone());

    let role = repo
        .get_participant_role(id, principal.user_id())
        .await
        .map_err(|_| AppointmentError::DatabaseError)?;
    if matches!(role, None | Some(UserRole::None)) {
        return Err(AppointmentError::Forbidden(NO_ROLE_MESSAGE.to_string()));
    }

    let message = repo
        .persist_message(id, principal.user_id(), request.body.as_deref())
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
    AppPath(id): AppPath<i64>,
    principal: PrincipalContext,
    AppJson(request): AppJson<UpdateParticipationStatusRequest>,
) -> Result<impl IntoResponse, AppointmentError> {
    let user_id = principal.user_id();

    let repo = AppointmentRepository::new(state.db_pool.clone());

    // Authorization check - user must be a participant in the appointment
    let participant_role = repo
        .get_participant_role(id, user_id)
        .await
        .map_err(|_| AppointmentError::DatabaseError)?;

    if participant_role == Some(UserRole::None) || participant_role.is_none() {
        return Err(AppointmentError::Forbidden(NO_ROLE_MESSAGE.to_string()));
    }

    let service = AppointmentService::with_events(repo, state.event_publisher.clone());

    service
        .change_participation_status(id, user_id, request.status)
        .await
        .map_err(AppointmentError::from)?;

    Ok(StatusCode::OK)
}

/// GET /api/v2/appointments - The caller's upcoming appointments, paged (Java: `getAgenda`)
///
/// `page` (default 0), `size` (default 10), `start` (default now), `end` (default now + 1000
/// years), `search`. The objects carry `participants`, `messages` and `group_participants`:
/// Java's flags of the same names only decide how the collections are fetched, the lazily
/// loaded ones are serialized anyway. A `page`/`size` that is no int is a 404 (Jakarta REST);
/// a negative `page`, a `size` below 1 or an unparsable `start`/`end` is a 500 in Java and a
/// 400 here (ticket #156 answer 5, ADR-2026-10-20-026).
pub async fn list_appointments(
    State(state): State<Arc<AppState>>,
    Query(query): Query<HashMap<String, String>>,
    principal: PrincipalContext,
) -> Result<impl IntoResponse, AppError> {
    let page = int_param(&query, "page", DEFAULT_PAGE)?;
    let size = int_param(&query, "size", DEFAULT_PAGE_SIZE)?;
    if page < 0 || size < 1 {
        return Err(AppError::bad_request(
            "page must not be negative and size must be positive",
        ));
    }
    let filter = AppointmentSearch {
        start: instant_param(&query, "start")?,
        end: instant_param(&query, "end")?,
        search: query.get("search").cloned(),
    };

    let repo = AppointmentRepository::new(state.db_pool.clone());
    let service = AppointmentService::with_events(repo, state.event_publisher.clone());

    let (appointments, total) = service
        .search_user_appointments(principal.user_id(), &filter, page, size)
        .await
        .map_err(|_| AppointmentError::DatabaseError)?;
    let data = service
        .with_details(
            appointments.into_iter().map(Into::into).collect(),
            Embed::ALL,
        )
        .await
        .map_err(AppointmentError::from)?;

    Ok(Json(PagedResponse {
        data,
        meta: PageMeta { page, size, total },
    }))
}

/// POST /api/v2/appointments - Create a new appointment
pub async fn create_appointment(
    State(state): State<Arc<AppState>>,
    principal: PrincipalContext,
    AppJson(request): AppJson<CreateAppointmentRequest>,
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
    AppPath(id): AppPath<i64>,
    principal: PrincipalContext,
    AppJson(request): AppJson<UpdateAppointmentRequest>,
) -> Result<impl IntoResponse, AppointmentError> {
    let user_id = principal.user_id();

    let repo = AppointmentRepository::new(state.db_pool.clone());

    // User must be a participant with ATTENDANT role or above (the creator is RESPONSIBLE).
    // Authorization comes first, like Java: an unknown appointment has no participants, hence a 403.
    let participant_role = repo
        .get_participant_role(id, user_id)
        .await
        .map_err(|_| AppointmentError::DatabaseError)?;

    let is_attendant_or_above = matches!(
        participant_role,
        Some(UserRole::Attendant | UserRole::Helper | UserRole::Responsible)
    );
    if !is_attendant_or_above {
        return Err(AppointmentError::Forbidden(
            NOT_ATTENDANT_MESSAGE.to_string(),
        ));
    }

    let service = AppointmentService::with_events(repo, state.event_publisher.clone());

    // Validate and update appointment
    let response = service
        .update_appointment(id, request, user_id)
        .await
        .map_err(AppointmentError::from)?;
    let mut detailed = service
        .with_details(vec![response], Embed::ALL)
        .await
        .map_err(AppointmentError::from)?;

    Ok(Json(detailed.remove(0)))
}

/// DELETE /api/v2/appointments/:id - Delete an appointment (soft delete)
/// Requires RESPONSIBLE role (creator only)
pub async fn delete_appointment(
    State(state): State<Arc<AppState>>,
    AppPath(id): AppPath<i64>,
    principal: PrincipalContext,
) -> Result<impl IntoResponse, AppointmentError> {
    let user_id = principal.user_id();

    let repo = AppointmentRepository::new(state.db_pool.clone());

    // Authorization first, like Java: an unknown appointment has no participants, hence a 403
    ensure_creator(&state, id, user_id, NOT_RESPONSIBLE_MESSAGE).await?;

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
    AppPath(id): AppPath<i64>,
    principal: PrincipalContext,
) -> Result<impl IntoResponse, AppointmentError> {
    let user_id = principal.user_id();

    let repo = AppointmentRepository::new(state.db_pool.clone());

    // Authorization first, like Java: an unknown appointment has no participants, hence a 403
    ensure_creator(&state, id, user_id, NOT_RESPONSIBLE_MESSAGE).await?;

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
    denied_message: &str,
) -> Result<(), AppointmentError> {
    let role = AppointmentRepository::new(state.db_pool.clone())
        .get_participant_role(appointment_id, user_id)
        .await
        .map_err(|_| AppointmentError::DatabaseError)?;

    match role {
        Some(UserRole::Responsible) => Ok(()),
        _ => Err(AppointmentError::Forbidden(denied_message.to_string())),
    }
}

/// POST /api/v2/appointments/:id/groups/:groupId - Add a group to an appointment
/// Requires the RESPONSIBLE role (only the creator can add groups)
pub async fn add_group_to_appointment(
    State(state): State<Arc<AppState>>,
    AppPath((appointment_id, group_id)): AppPath<(i64, i64)>,
    principal: PrincipalContext,
    AppJson(request): AppJson<AddGroupToAppointmentRequest>,
) -> Result<impl IntoResponse, AppointmentError> {
    let user_id = principal.user_id();

    let repo = AppointmentRepository::new(state.db_pool.clone());
    let service = AppointmentService::with_events(repo, state.event_publisher.clone());

    let role = UserRole::from_str(&request.role)
        .map_err(|_| AppointmentError::ValidationError("Invalid role".to_string()))?;

    service
        .add_group_to_appointment(user_id, appointment_id, group_id, role)
        .await
        .map_err(missing_is_forbidden)?;

    Ok(StatusCode::OK)
}

/// DELETE /api/v2/appointments/:id/groups/:groupId - Remove a group from an appointment
///
/// Requires the RESPONSIBLE role (403 otherwise, also for an unknown appointment). Returns 400 if
/// the group is not linked to the appointment and 200 with an empty body on success. Participants
/// that came in through the group are removed, directly added ones stay.
pub async fn remove_group_from_appointment(
    State(state): State<Arc<AppState>>,
    AppPath((appointment_id, group_id)): AppPath<(i64, i64)>,
    principal: PrincipalContext,
) -> Result<impl IntoResponse, AppointmentError> {
    let repo = AppointmentRepository::new(state.db_pool.clone());
    let service = AppointmentService::with_events(repo, state.event_publisher.clone());

    service
        .remove_group_from_appointment(principal.user_id(), appointment_id, group_id)
        .await
        .map_err(AppointmentError::from)?;

    Ok(StatusCode::OK)
}

/// DELETE /api/v2/appointments/:id/participants/:userId - Remove a participant (hard delete)
///
/// Only a RESPONSIBLE participant may remove participants. Fires an
/// `AppointmentParticipationRemovedEvent`. An unknown appointment is a 403 like in Java, which
/// authorizes before it looks the appointment up (`missing_is_forbidden`, ADR); an unknown
/// participant is a 400.
pub async fn remove_participant(
    State(state): State<Arc<AppState>>,
    AppPath((appointment_id, target_user_id)): AppPath<(i64, String)>,
    principal: PrincipalContext,
) -> Result<impl IntoResponse, AppointmentError> {
    let acting_user_id = principal.user_id();

    let repo = AppointmentRepository::new(state.db_pool.clone());
    let service = AppointmentService::with_events(repo, state.event_publisher.clone());

    service
        .remove_participant(appointment_id, &target_user_id, acting_user_id)
        .await
        .map_err(missing_is_forbidden)?;

    Ok(StatusCode::OK)
}

/// PUT /api/v2/appointments/:id/participation/:userId/role - Change a participant's role
///
/// # Authorization
/// The acting user must be RESPONSIBLE for the appointment (403 otherwise).
///
/// # Request
/// Body `{"role": "NONE|GUEST|ATTENDANT|HELPER|RESPONSIBLE"}`; an unknown role is a 400.
///
/// # Response
/// 200 OK with an empty body. Fires `AppointmentParticipationRoleChangedEvent` (with the old role).
/// An unknown appointment is a 403 like in Java (`missing_is_forbidden`, ADR); 400 if the target is
/// not a participant or already has the role.
pub async fn change_participant_role(
    State(state): State<Arc<AppState>>,
    AppPath((appointment_id, target_user_id)): AppPath<(i64, String)>,
    principal: PrincipalContext,
    AppJson(request): AppJson<ChangeParticipantRoleRequest>,
) -> Result<impl IntoResponse, AppointmentError> {
    let new_role = UserRole::from_str(&request.role)
        .map_err(|_| AppointmentError::ValidationError("invalid role".to_string()))?;

    let repo = AppointmentRepository::new(state.db_pool.clone());
    let service = AppointmentService::with_events(repo, state.event_publisher.clone());

    service
        .change_participant_role(
            appointment_id,
            &target_user_id,
            principal.user_id(),
            new_role,
        )
        .await
        .map_err(missing_is_forbidden)?;

    Ok(StatusCode::OK)
}

/// Errors that can occur in appointment handlers
#[derive(Debug)]
pub enum AppointmentError {
    NotFound,
    Forbidden(String),
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
            ServiceError::Forbidden(msg) => AppointmentError::Forbidden(msg),
        }
    }
}

/// The `AppointmentError` of a user-facing mutation of the participants of an appointment: Java
/// authorizes before it looks the appointment up, so an unknown one is a 403 there (the admin
/// endpoints skip the authorization and keep the 404)
fn missing_is_forbidden(error: ServiceError) -> AppointmentError {
    match error {
        ServiceError::NotFound => AppointmentError::Forbidden(NOT_RESPONSIBLE_MESSAGE.to_string()),
        other => other.into(),
    }
}

impl From<AppointmentError> for AppError {
    fn from(error: AppointmentError) -> Self {
        match error {
            AppointmentError::NotFound => AppError::not_found_message("Appointment not found"),
            AppointmentError::Forbidden(msg) => AppError::forbidden(msg),
            AppointmentError::DatabaseError => AppError::unexpected(),
            AppointmentError::ValidationError(msg) => AppError::bad_request(msg),
        }
    }
}

impl IntoResponse for AppointmentError {
    fn into_response(self) -> axum::response::Response {
        AppError::from(self).into_response()
    }
}

/// POST /api/v2/appointments/:id/participants/:userId - Add a participant to an appointment
pub async fn add_participant(
    State(state): State<Arc<AppState>>,
    AppPath((appointment_id, target_user_id)): AppPath<(i64, String)>,
    principal: PrincipalContext,
    AppJson(request): AppJson<AddParticipantRequest>,
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
        .map_err(missing_is_forbidden)?;

    Ok(StatusCode::OK)
}
