use axum::{
    extract::{Path, Query, State},
    http::StatusCode,
    response::IntoResponse,
    Json,
};
use serde::Deserialize;
use std::str::FromStr;
use std::sync::Arc;
use uuid::Uuid;

use crate::appointments::{
    models::{AddGroupToAppointmentRequest, AppointmentResponse, CreateAppointmentRequest, UpdateAppointmentRequest, UserRole},
    repository::AppointmentRepository,
    services::{AppointmentService, ServiceError},
};
use crate::event_bus::EventPublisher;
use crate::security::PrincipalContext;

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
    Path(id): Path<Uuid>,
    principal: PrincipalContext,
) -> Result<impl IntoResponse, AppointmentError> {
    let repo = AppointmentRepository::new(state.db_pool.clone());
    let service = AppointmentService::with_events(repo, state.event_publisher.clone());

    // Get user ID from the authenticated principal (already validated as UUID in auth middleware)
    let user_id = principal.user_id();

    // Fetch the appointment
    let appointment = service.get_appointment(id).await.map_err(|e| match e {
        crate::appointments::services::ServiceError::NotFound => AppointmentError::NotFound,
        _ => AppointmentError::DatabaseError,
    })?;

    // Authorization check - user must be creator or invited participant
    // For now, only allow creators to view their appointments
    // TODO: Also check if user is a participant in the appointment_participants table
    if appointment.creator_id != user_id {
        return Err(AppointmentError::Unauthorized);
    }

    let response: AppointmentResponse = appointment.into();
    Ok(Json(response))
}

/// GET /api/v2/appointments - List appointments with pagination
pub async fn list_appointments(
    State(state): State<Arc<AppState>>,
    Query(query): Query<ListQuery>,
    principal: PrincipalContext,
) -> Result<impl IntoResponse, AppointmentError> {
    let repo = AppointmentRepository::new(state.db_pool.clone());
    let service = AppointmentService::with_events(repo, state.event_publisher.clone());

    // Get user ID from the authenticated principal (already validated as UUID in auth middleware)
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
    Path(id): Path<Uuid>,
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

    // User must be creator or a participant with ATTENDANT role or above
    if appointment.creator_id != user_id {
        let participant_role = repo
            .get_participant_role(id, user_id)
            .await
            .map_err(|_| AppointmentError::DatabaseError)?;

        match participant_role {
            Some(role) => {
                let is_attendant_or_above = matches!(
                    role,
                    UserRole::Attendant | UserRole::Helper | UserRole::Responsible
                );
                if !is_attendant_or_above {
                    return Err(AppointmentError::Unauthorized);
                }
            }
            None => {
                return Err(AppointmentError::Unauthorized);
            }
        }
    }

    let service = AppointmentService::with_events(repo, state.event_publisher.clone());

    // Validate and update appointment
    let response = service
        .update_appointment(id, request)
        .await
        .map_err(AppointmentError::from)?;

    Ok(Json(response))
}

/// DELETE /api/v2/appointments/:id - Delete an appointment (soft delete)
/// Requires RESPONSIBLE role (creator only)
pub async fn delete_appointment(
    State(state): State<Arc<AppState>>,
    Path(id): Path<Uuid>,
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

    if appointment.creator_id != user_id {
        return Err(AppointmentError::Unauthorized);
    }

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
    Path(id): Path<Uuid>,
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

    if appointment.creator_id != user_id {
        return Err(AppointmentError::Unauthorized);
    }

    let service = AppointmentService::with_events(repo, state.event_publisher.clone());

    service
        .cancel_appointment(id)
        .await
        .map_err(AppointmentError::from)?;

    Ok(StatusCode::OK)
}

/// POST /api/v2/appointments/:id/groups/:groupId - Add a group to an appointment
/// Requires creator role (only the creator can add groups)
pub async fn add_group_to_appointment(
    State(state): State<Arc<AppState>>,
    Path((appointment_id, group_id)): Path<(Uuid, Uuid)>,
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
