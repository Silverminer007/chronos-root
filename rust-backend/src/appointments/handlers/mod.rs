use axum::{
    extract::{Path, Query, State},
    http::StatusCode,
    response::IntoResponse,
    Json,
};
use serde_json::json;
use std::sync::Arc;
use uuid::Uuid;

use crate::appointments::{
    models::{AppointmentResponse, CreateAppointmentRequest, UpdateAppointmentRequest, MoveAppointmentRequest},
    repository::AppointmentRepository,
    services::AppointmentService,
};
use crate::security::PrincipalContext;

/// Shared application state
#[derive(Clone)]
pub struct AppState {
    pub db_pool: sqlx::PgPool,
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
    let service = AppointmentService::new(repo);

    // Get user ID from the authenticated principal
    let user_id_str = principal.user_id();
    let user_id = Uuid::parse_str(&user_id_str)
        .map_err(|_| AppointmentError::DatabaseError)?;

    // Fetch the appointment
    let appointment = service
        .get_appointment(id)
        .await
        .map_err(|_| AppointmentError::DatabaseError)?
        .ok_or(AppointmentError::NotFound)?;

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
    let service = AppointmentService::new(repo);

    // Get user ID from the authenticated principal
    // Note: principal.user_id() returns String, we need to convert to UUID
    let user_id_str = principal.user_id();
    let user_id = uuid::Uuid::parse_str(&user_id_str)
        .map_err(|_| AppointmentError::DatabaseError)?;

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

    let responses: Vec<AppointmentResponse> = appointments
        .into_iter()
        .map(|a| a.into())
        .collect();

    Ok(Json(responses))
}

/// POST /api/v2/appointments - Create a new appointment
pub async fn create_appointment(
    State(state): State<Arc<AppState>>,
    principal: PrincipalContext,
    Json(request): Json<CreateAppointmentRequest>,
) -> Result<impl IntoResponse, AppointmentError> {
    let repo = AppointmentRepository::new(state.db_pool.clone());
    let service = AppointmentService::new(repo);

    // Get user ID from the authenticated principal
    let user_id_str = principal.user_id();
    let user_id = Uuid::parse_str(&user_id_str)
        .map_err(|_| AppointmentError::DatabaseError)?;

    // Create the appointment
    let appointment = service
        .create_appointment(
            request.title,
            request.description,
            request.location,
            request.start_time,
            request.end_time,
            user_id,
        )
        .await
        .map_err(|e| match e {
            crate::appointments::repository::RepositoryError::InvalidInput(_) => {
                AppointmentError::ValidationError(e.to_string())
            }
            _ => AppointmentError::DatabaseError,
        })?;

    let response: AppointmentResponse = appointment.into();
    Ok((StatusCode::CREATED, Json(response)))
}

/// PUT /api/v2/appointments/:id - Update an appointment
pub async fn update_appointment(
    State(state): State<Arc<AppState>>,
    Path(id): Path<Uuid>,
    principal: PrincipalContext,
    Json(request): Json<UpdateAppointmentRequest>,
) -> Result<impl IntoResponse, AppointmentError> {
    let repo = AppointmentRepository::new(state.db_pool.clone());
    let service = AppointmentService::new(repo);

    // Get user ID from the authenticated principal
    let user_id_str = principal.user_id();
    let user_id = Uuid::parse_str(&user_id_str)
        .map_err(|_| AppointmentError::DatabaseError)?;

    // Fetch the appointment to check authorization
    let appointment = service
        .get_appointment(id)
        .await
        .map_err(|_| AppointmentError::DatabaseError)?
        .ok_or(AppointmentError::NotFound)?;

    // Authorization check - only creator can edit
    if appointment.creator_id != user_id {
        return Err(AppointmentError::Unauthorized);
    }

    // Update the appointment
    let updated_appointment = service
        .update_appointment(
            id,
            request.title,
            request.description,
            request.location,
            request.start_time,
            request.end_time,
        )
        .await
        .map_err(|e| match e {
            crate::appointments::repository::RepositoryError::InvalidInput(_) => {
                AppointmentError::ValidationError(e.to_string())
            }
            _ => AppointmentError::DatabaseError,
        })?;

    let response: AppointmentResponse = updated_appointment.into();
    Ok(Json(response))
}

/// PUT /api/v2/appointments/:id/move - Reschedule an appointment
pub async fn move_appointment(
    State(state): State<Arc<AppState>>,
    Path(id): Path<Uuid>,
    principal: PrincipalContext,
    Json(request): Json<MoveAppointmentRequest>,
) -> Result<impl IntoResponse, AppointmentError> {
    let repo = AppointmentRepository::new(state.db_pool.clone());
    let service = AppointmentService::new(repo);

    // Get user ID from the authenticated principal
    let user_id_str = principal.user_id();
    let user_id = Uuid::parse_str(&user_id_str)
        .map_err(|_| AppointmentError::DatabaseError)?;

    // Fetch the appointment to check authorization
    let appointment = service
        .get_appointment(id)
        .await
        .map_err(|_| AppointmentError::DatabaseError)?
        .ok_or(AppointmentError::NotFound)?;

    // Authorization check - only creator can move
    if appointment.creator_id != user_id {
        return Err(AppointmentError::Unauthorized);
    }

    // Update the appointment (move to new time)
    let updated_appointment = service
        .update_appointment(
            id,
            None,
            None,
            None,
            Some(request.start_time),
            Some(request.end_time),
        )
        .await
        .map_err(|e| match e {
            crate::appointments::repository::RepositoryError::InvalidInput(_) => {
                AppointmentError::ValidationError(e.to_string())
            }
            _ => AppointmentError::DatabaseError,
        })?;

    let response: AppointmentResponse = updated_appointment.into();
    Ok(Json(response))
}

/// DELETE /api/v2/appointments/:id - Delete an appointment
pub async fn delete_appointment(
    State(state): State<Arc<AppState>>,
    Path(id): Path<Uuid>,
    principal: PrincipalContext,
) -> Result<StatusCode, AppointmentError> {
    let repo = AppointmentRepository::new(state.db_pool.clone());
    let service = AppointmentService::new(repo);

    // Get user ID from the authenticated principal
    let user_id_str = principal.user_id();
    let user_id = Uuid::parse_str(&user_id_str)
        .map_err(|_| AppointmentError::DatabaseError)?;

    // Fetch the appointment to check authorization
    let appointment = service
        .get_appointment(id)
        .await
        .map_err(|_| AppointmentError::DatabaseError)?
        .ok_or(AppointmentError::NotFound)?;

    // Authorization check - only creator can delete
    if appointment.creator_id != user_id {
        return Err(AppointmentError::Unauthorized);
    }

    // Delete the appointment
    service
        .delete_appointment(id)
        .await
        .map_err(|_| AppointmentError::DatabaseError)?;

    Ok(StatusCode::NO_CONTENT)
}

/// Errors that can occur in appointment handlers
#[derive(Debug)]
pub enum AppointmentError {
    NotFound,
    Unauthorized,
    DatabaseError,
    ValidationError(String),
}

impl IntoResponse for AppointmentError {
    fn into_response(self) -> axum::response::Response {
        let (status, error_message) = match self {
            AppointmentError::NotFound => {
                (StatusCode::NOT_FOUND, "Appointment not found".to_string())
            }
            AppointmentError::Unauthorized => {
                (StatusCode::FORBIDDEN, "Unauthorized".to_string())
            }
            AppointmentError::DatabaseError => {
                (StatusCode::INTERNAL_SERVER_ERROR, "Database error".to_string())
            }
            AppointmentError::ValidationError(msg) => (StatusCode::BAD_REQUEST, msg),
        };

        let error_response = json!({"error": error_message});
        (status, Json(error_response)).into_response()
    }
}
