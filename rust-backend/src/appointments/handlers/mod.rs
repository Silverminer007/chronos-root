use axum::{
    extract::{Path, Query, State},
    http::StatusCode,
    response::IntoResponse,
    Json,
};
use serde::Deserialize;
use std::sync::Arc;
use uuid::Uuid;

use crate::appointments::{
    models::AppointmentResponse,
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
}

/// GET /api/v2/appointments/:id - Fetch a single appointment by ID
pub async fn get_appointment(
    State(state): State<Arc<AppState>>,
    Path(id): Path<Uuid>,
    _principal: PrincipalContext,
) -> Result<impl IntoResponse, AppointmentError> {
    let repo = AppointmentRepository::new(state.db_pool.clone());
    let service = AppointmentService::new(repo);

    // Fetch the appointment
    let appointment = service
        .get_appointment(id)
        .await
        .map_err(|_| AppointmentError::DatabaseError)?
        .ok_or(AppointmentError::NotFound)?;

    // TODO: Authorization check - user must be creator or invited participant
    // For now, allow all authenticated users to see all appointments

    let response: AppointmentResponse = appointment.into();
    Ok(Json(response))
}

/// GET /api/v2/appointments - List appointments with pagination
pub async fn list_appointments(
    State(state): State<Arc<AppState>>,
    Query(query): Query<ListQuery>,
    _principal: PrincipalContext,
) -> Result<impl IntoResponse, AppointmentError> {
    let repo = AppointmentRepository::new(state.db_pool.clone());
    let service = AppointmentService::new(repo);

    // TODO: Apply authorization filter to only show appointments user can see
    let appointments = service
        .list_appointments(crate::appointments::services::ListAppointmentsQuery {
            limit: query.limit.or(Some(20)),
            offset: query.offset.or(Some(0)),
        })
        .await
        .map_err(|_| AppointmentError::DatabaseError)?;

    let responses: Vec<AppointmentResponse> = appointments
        .into_iter()
        .map(|a| a.into())
        .collect();

    Ok(Json(responses))
}

/// Errors that can occur in appointment handlers
#[derive(Debug)]
pub enum AppointmentError {
    NotFound,
    Unauthorized,
    DatabaseError,
}

impl IntoResponse for AppointmentError {
    fn into_response(self) -> axum::response::Response {
        let (status, error_message) = match self {
            AppointmentError::NotFound => (StatusCode::NOT_FOUND, "Appointment not found"),
            AppointmentError::Unauthorized => (StatusCode::FORBIDDEN, "Unauthorized"),
            AppointmentError::DatabaseError => {
                (StatusCode::INTERNAL_SERVER_ERROR, "Database error")
            }
        };

        (status, error_message).into_response()
    }
}
