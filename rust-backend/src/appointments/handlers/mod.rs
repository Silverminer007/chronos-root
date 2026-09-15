use axum::{
    extract::{Path, Query, State},
    http::StatusCode,
    response::IntoResponse,
    Json,
};
use serde::Deserialize;
use serde_json::json;
use std::sync::Arc;
use uuid::Uuid;

use crate::appointments::{
    models::{
        AppointmentResponse, CreateAppointmentRequest, MoveAppointmentRequest,
        UpdateAppointmentRequest,
    },
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
) -> impl IntoResponse {
    let repo = AppointmentRepository::new(state.db_pool.clone());
    let service = AppointmentService::new(repo);

    // Get user ID from the authenticated principal
    let user_id_str = principal.user_id();
    let user_id = match Uuid::parse_str(&user_id_str) {
        Ok(id) => id,
        Err(_) => {
            let err = (StatusCode::INTERNAL_SERVER_ERROR, Json(json!({"error": "Database error"})));
            return err.into_response();
        }
    };

    // Fetch the appointment
    let appointment = match service.get_appointment(id).await {
        Ok(Some(appt)) => appt,
        Ok(None) => {
            let err = (StatusCode::NOT_FOUND, Json(json!({"error": "Appointment not found"})));
            return err.into_response();
        }
        Err(_) => {
            let err = (StatusCode::INTERNAL_SERVER_ERROR, Json(json!({"error": "Database error"})));
            return err.into_response();
        }
    };

    // Authorization check - user must be creator or invited participant
    if appointment.creator_id != user_id {
        let err = (StatusCode::FORBIDDEN, Json(json!({"error": "Unauthorized"})));
        return err.into_response();
    }

    let response: AppointmentResponse = appointment.into();
    (StatusCode::OK, Json(response)).into_response()
}

/// GET /api/v2/appointments - List appointments with pagination
pub async fn list_appointments(
    State(state): State<Arc<AppState>>,
    Query(query): Query<ListQuery>,
    principal: PrincipalContext,
) -> impl IntoResponse {
    let repo = AppointmentRepository::new(state.db_pool.clone());
    let service = AppointmentService::new(repo);

    // Get user ID from the authenticated principal
    let user_id_str = principal.user_id();
    let user_id = match Uuid::parse_str(&user_id_str) {
        Ok(id) => id,
        Err(_) => {
            let err = (StatusCode::INTERNAL_SERVER_ERROR, Json(json!({"error": "Database error"})));
            return err.into_response();
        }
    };

    // List only appointments visible to this user
    let list_query = crate::appointments::services::ListAppointmentsQuery {
        limit: query.limit.or(Some(20)),
        offset: query.offset.or(Some(0)),
        sort_by: query.sort_by.clone(),
        sort_dir: query.sort_dir.clone(),
    };
    let appointments = match service.list_user_appointments(user_id, list_query).await {
        Ok(appts) => appts,
        Err(_) => {
            let err = (StatusCode::INTERNAL_SERVER_ERROR, Json(json!({"error": "Database error"})));
            return err.into_response();
        }
    };

    let responses: Vec<AppointmentResponse> = appointments
        .into_iter()
        .map(|a| a.into())
        .collect();

    (StatusCode::OK, Json(responses)).into_response()
}

/// POST /api/v2/appointments - Create a new appointment
pub async fn create_appointment(
    State(state): State<Arc<AppState>>,
    principal: PrincipalContext,
    Json(request): Json<CreateAppointmentRequest>,
) -> impl IntoResponse {
    let repo = AppointmentRepository::new(state.db_pool.clone());
    let service = AppointmentService::new(repo);

    // Get user ID from the authenticated principal
    let user_id_str = principal.user_id();
    let user_id = match Uuid::parse_str(&user_id_str) {
        Ok(id) => id,
        Err(_) => {
            let err = (StatusCode::INTERNAL_SERVER_ERROR, Json(json!({"error": "Database error"})));
            return err.into_response();
        }
    };

    // Create the appointment
    let appointment = match service
        .create_appointment(
            request.title,
            request.description,
            request.location,
            request.start_time,
            request.end_time,
            user_id,
        )
        .await
    {
        Ok(appt) => appt,
        Err(e) => {
            let (status, msg) = match e {
                crate::appointments::repository::RepositoryError::InvalidInput(_) => {
                    (StatusCode::BAD_REQUEST, e.to_string())
                }
                _ => (StatusCode::INTERNAL_SERVER_ERROR, "Database error".to_string()),
            };
            let err = (status, Json(json!({"error": msg})));
            return err.into_response();
        }
    };

    let response: AppointmentResponse = appointment.into();
    (StatusCode::CREATED, Json(response)).into_response()
}

/// PUT /api/v2/appointments/:id - Update an appointment
pub async fn update_appointment(
    State(state): State<Arc<AppState>>,
    Path(id): Path<Uuid>,
    principal: PrincipalContext,
    Json(request): Json<UpdateAppointmentRequest>,
) -> impl IntoResponse {
    let repo = AppointmentRepository::new(state.db_pool.clone());
    let service = AppointmentService::new(repo);

    // Get user ID from the authenticated principal
    let user_id_str = principal.user_id();
    let user_id = match Uuid::parse_str(&user_id_str) {
        Ok(id) => id,
        Err(_) => {
            let err = (StatusCode::INTERNAL_SERVER_ERROR, Json(json!({"error": "Database error"})));
            return err.into_response();
        }
    };

    // Fetch the appointment to check authorization
    let appointment = match service.get_appointment(id).await {
        Ok(Some(appt)) => appt,
        Ok(None) => {
            let err = (StatusCode::NOT_FOUND, Json(json!({"error": "Appointment not found"})));
            return err.into_response();
        }
        Err(_) => {
            let err = (StatusCode::INTERNAL_SERVER_ERROR, Json(json!({"error": "Database error"})));
            return err.into_response();
        }
    };

    // Authorization check - only creator can edit
    if appointment.creator_id != user_id {
        let err = (StatusCode::FORBIDDEN, Json(json!({"error": "Unauthorized"})));
        return err.into_response();
    }

    // Update the appointment
    let updated_appointment = match service
        .update_appointment(
            id,
            request.title,
            request.description,
            request.location,
            request.start_time,
            request.end_time,
        )
        .await
    {
        Ok(appt) => appt,
        Err(e) => {
            let (status, msg) = match e {
                crate::appointments::repository::RepositoryError::InvalidInput(_) => {
                    (StatusCode::BAD_REQUEST, e.to_string())
                }
                _ => (StatusCode::INTERNAL_SERVER_ERROR, "Database error".to_string()),
            };
            let err = (status, Json(json!({"error": msg})));
            return err.into_response();
        }
    };

    let response: AppointmentResponse = updated_appointment.into();
    (StatusCode::OK, Json(response)).into_response()
}

/// PUT /api/v2/appointments/:id/move - Reschedule an appointment
pub async fn move_appointment(
    State(state): State<Arc<AppState>>,
    Path(id): Path<Uuid>,
    principal: PrincipalContext,
    Json(request): Json<MoveAppointmentRequest>,
) -> impl IntoResponse {
    let repo = AppointmentRepository::new(state.db_pool.clone());
    let service = AppointmentService::new(repo);

    // Get user ID from the authenticated principal
    let user_id_str = principal.user_id();
    let user_id = match Uuid::parse_str(&user_id_str) {
        Ok(id) => id,
        Err(_) => {
            let err = (StatusCode::INTERNAL_SERVER_ERROR, Json(json!({"error": "Database error"})));
            return err.into_response();
        }
    };

    // Fetch the appointment to check authorization
    let appointment = match service.get_appointment(id).await {
        Ok(Some(appt)) => appt,
        Ok(None) => {
            let err = (StatusCode::NOT_FOUND, Json(json!({"error": "Appointment not found"})));
            return err.into_response();
        }
        Err(_) => {
            let err = (StatusCode::INTERNAL_SERVER_ERROR, Json(json!({"error": "Database error"})));
            return err.into_response();
        }
    };

    // Authorization check - only creator can move
    if appointment.creator_id != user_id {
        let err = (StatusCode::FORBIDDEN, Json(json!({"error": "Unauthorized"})));
        return err.into_response();
    }

    // Update the appointment (move to new time)
    let updated_appointment = match service
        .update_appointment(
            id,
            None,
            None,
            None,
            Some(request.start_time),
            Some(request.end_time),
        )
        .await
    {
        Ok(appt) => appt,
        Err(e) => {
            let (status, msg) = match e {
                crate::appointments::repository::RepositoryError::InvalidInput(_) => {
                    (StatusCode::BAD_REQUEST, e.to_string())
                }
                _ => (StatusCode::INTERNAL_SERVER_ERROR, "Database error".to_string()),
            };
            let err = (status, Json(json!({"error": msg})));
            return err.into_response();
        }
    };

    let response: AppointmentResponse = updated_appointment.into();
    (StatusCode::OK, Json(response)).into_response()
}

/// DELETE /api/v2/appointments/:id - Delete an appointment
pub async fn delete_appointment(
    State(state): State<Arc<AppState>>,
    Path(id): Path<Uuid>,
    principal: PrincipalContext,
) -> impl IntoResponse {
    let repo = AppointmentRepository::new(state.db_pool.clone());
    let service = AppointmentService::new(repo);

    // Get user ID from the authenticated principal
    let user_id_str = principal.user_id();
    let user_id = match Uuid::parse_str(&user_id_str) {
        Ok(id) => id,
        Err(_) => {
            let err = (StatusCode::INTERNAL_SERVER_ERROR, Json(json!({"error": "Database error"})));
            return err.into_response();
        }
    };

    // Fetch the appointment to check authorization
    let appointment = match service.get_appointment(id).await {
        Ok(Some(appt)) => appt,
        Ok(None) => {
            let err = (StatusCode::NOT_FOUND, Json(json!({"error": "Appointment not found"})));
            return err.into_response();
        }
        Err(_) => {
            let err = (StatusCode::INTERNAL_SERVER_ERROR, Json(json!({"error": "Database error"})));
            return err.into_response();
        }
    };

    // Authorization check - only creator can delete
    if appointment.creator_id != user_id {
        let err = (StatusCode::FORBIDDEN, Json(json!({"error": "Unauthorized"})));
        return err.into_response();
    }

    // Delete the appointment
    match service.delete_appointment(id).await {
        Ok(_) => StatusCode::NO_CONTENT.into_response(),
        Err(_) => {
            let err = (StatusCode::INTERNAL_SERVER_ERROR, Json(json!({"error": "Database error"})));
            err.into_response()
        }
    }
}
