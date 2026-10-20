//! Handlers of `/api/v2/admin/*`. Every handler takes an [`AdminPrincipal`], which enforces the
//! `ADMIN_API` role before anything else happens. They delegate to the services of the other
//! features; admin requests skip those services' participant checks like Java's `isAdminRequest()`.

use axum::{
    body::Bytes,
    extract::{Path, Query, State},
    http::StatusCode,
    response::{IntoResponse, Response},
    Json,
};
use chrono::{DateTime, Utc};
use serde::de::DeserializeOwned;
use serde_json::json;
use std::collections::HashMap;
use std::str::FromStr;
use std::sync::Arc;

use crate::admin::models::{
    AdminAddGroupParticipantRequest, AdminChangeParticipationStatusRequest,
    AdminCreateGroupRequest, AdminMessageRequest, AdminStatisticsDto, AdminUserDto,
    AdminUserListResponse, FriendGroupRequest, PageMeta, PagedResponse, Total,
};
use crate::admin::repository::{self, PushLogFilter};
use crate::admin::AdminState;
use crate::appointments::handlers::AppointmentError;
use crate::appointments::models::{
    AppointmentResponse, CreateAppointmentRequest, ParticipationStatus, UserRole,
};
use crate::appointments::repository::AppointmentRepository;
use crate::appointments::services::AppointmentService;
use crate::error::AppError;
use crate::groups::models::{format_instant, GroupDto};
use crate::groups::services::{FriendshipService, GroupService};
use crate::keycloak::{KeycloakError, KeycloakUser};
use crate::security::AdminPrincipal;

/// Default of the `size` query parameter of `GET /appointments` and `GET /users`
const DEFAULT_PAGE_SIZE: i64 = 20;
/// Default of the `size` query parameter of `GET /push/log`
const DEFAULT_LOG_PAGE_SIZE: i64 = 50;

/// Notification the test endpoint sends when the request has no payload
/// (Java text blocks: the quotes and the trailing newline are part of the text)
const TEST_NOTIFICATION_TITLE: &str = "\"Testbenachrichtigung\"\n";
const TEST_NOTIFICATION_BODY: &str = "\"Push\" funktioniert!\n";

/// What an admin handler can fail with
#[derive(Debug)]
pub enum AdminError {
    /// Java `ErrorResponse` body
    App(AppError),
    /// The answers of the appointment endpoints (same mapping as `/api/v2/appointments`)
    Appointment(AppointmentError),
    /// Status without body, e.g. `Response.status(BAD_REQUEST).build()`
    Status(StatusCode),
}

impl From<AppError> for AdminError {
    fn from(error: AppError) -> Self {
        Self::App(error)
    }
}

impl From<sqlx::Error> for AdminError {
    fn from(error: sqlx::Error) -> Self {
        Self::App(error.into())
    }
}

impl From<crate::appointments::services::ServiceError> for AdminError {
    fn from(error: crate::appointments::services::ServiceError) -> Self {
        Self::Appointment(error.into())
    }
}

impl From<crate::appointments::repository::RepositoryError> for AdminError {
    fn from(error: crate::appointments::repository::RepositoryError) -> Self {
        tracing::error!("Admin query failed: {}", error);
        Self::App(AppError::unexpected())
    }
}

impl IntoResponse for AdminError {
    fn into_response(self) -> Response {
        match self {
            Self::App(error) => error.into_response(),
            Self::Appointment(error) => error.into_response(),
            Self::Status(status) => status.into_response(),
        }
    }
}

type AdminResult<T> = Result<T, AdminError>;

/// Java's generic 500 for everything the resource does not anticipate (NPE, `valueOf`, `Instant.parse`)
fn unexpected() -> AdminError {
    AdminError::App(AppError::unexpected())
}

/// An absent body is a `null` DTO; an unreadable one fails like any unexpected exception in Java (500)
fn parse_body<T: DeserializeOwned>(body: &Bytes) -> AdminResult<Option<T>> {
    if body.is_empty() {
        return Ok(None);
    }
    serde_json::from_slice::<Option<T>>(body).map_err(|e| {
        tracing::warn!("Unreadable admin request body: {}", e);
        unexpected()
    })
}

/// A path parameter of type `Long` that cannot be converted is a 404 (Jakarta REST)
fn path_number(raw: &str) -> AdminResult<i64> {
    raw.parse()
        .map_err(|_| AdminError::App(AppError::RouteNotFound))
}

/// An `int` query parameter: absent means `default`, unparsable is a 404 (Jakarta REST)
fn query_number(query: &HashMap<String, String>, name: &str, default: i64) -> AdminResult<i64> {
    match query.get(name) {
        None => Ok(default),
        Some(raw) => raw
            .parse::<i32>()
            .map(i64::from)
            .map_err(|_| AdminError::App(AppError::RouteNotFound)),
    }
}

/// `page` and `size` as Panache's `Page.of` accepts them
fn panache_page(page: i64, size: i64) -> AdminResult<(i64, i64)> {
    if page < 0 || size < 1 {
        return Err(unexpected());
    }
    Ok((page, size))
}

/// Java's `Instant.parse`; a failure is an unexpected exception (500)
fn parse_instant(raw: &str) -> AdminResult<DateTime<Utc>> {
    DateTime::parse_from_rfc3339(raw)
        .map(|time| time.with_timezone(&Utc))
        .map_err(|_| unexpected())
}

fn appointment_service(state: &AdminState) -> AppointmentService {
    AppointmentService::with_events(
        AppointmentRepository::new(state.db_pool.clone()),
        state.event_publisher.clone(),
    )
    .as_admin()
}

// ===== appointments =====

/// POST /api/v2/admin/appointments – the admin becomes the RESPONSIBLE participant; answers 200 like Java
pub async fn create_appointment(
    State(state): State<Arc<AdminState>>,
    admin: AdminPrincipal,
    body: Bytes,
) -> AdminResult<Json<AppointmentResponse>> {
    let request: CreateAppointmentRequest = parse_body(&body)?.ok_or_else(unexpected)?;
    let created = appointment_service(&state)
        .create_appointment(request, admin.user_id().to_string())
        .await?;
    Ok(Json(created))
}

/// POST /api/v2/admin/appointments/{appointmentId}/participants/groups
pub async fn add_group_participant(
    State(state): State<Arc<AdminState>>,
    admin: AdminPrincipal,
    Path(appointment_id): Path<String>,
    body: Bytes,
) -> AdminResult<StatusCode> {
    let appointment_id = path_number(&appointment_id)?;
    let request: AdminAddGroupParticipantRequest = parse_body(&body)?.ok_or_else(unexpected)?;
    let role = request
        .user_role
        .as_deref()
        .and_then(|role| UserRole::from_str(role).ok())
        .ok_or_else(unexpected)?;
    let group_id = request.group_id.ok_or_else(unexpected)?;
    appointment_service(&state)
        .add_group_to_appointment(admin.user_id(), appointment_id, group_id, role)
        .await?;
    Ok(StatusCode::OK)
}

/// POST /api/v2/admin/appointments/participants/status – RSVP on behalf of a user
pub async fn change_participation_status(
    State(state): State<Arc<AdminState>>,
    _admin: AdminPrincipal,
    body: Bytes,
) -> AdminResult<StatusCode> {
    let request: AdminChangeParticipationStatusRequest =
        parse_body(&body)?.ok_or_else(unexpected)?;
    let status: ParticipationStatus = request
        .participation_status
        .map(serde_json::Value::String)
        .and_then(|status| serde_json::from_value(status).ok())
        .ok_or_else(unexpected)?;
    let user_id = request.user_id.ok_or_else(unexpected)?;
    let appointment_id = request.appointment_id.ok_or_else(unexpected)?;
    appointment_service(&state)
        .change_participation_status(appointment_id, &user_id, status)
        .await?;
    Ok(StatusCode::OK)
}

/// POST /api/v2/admin/appointments/message – store a chat message for any sender and time
pub async fn send_message(
    State(state): State<Arc<AdminState>>,
    _admin: AdminPrincipal,
    body: Bytes,
) -> AdminResult<StatusCode> {
    let request: AdminMessageRequest = parse_body(&body)?.ok_or_else(unexpected)?;
    let appointment_id = request.appointment_id.ok_or_else(unexpected)?;
    let timestamp = parse_instant(request.timestamp.as_deref().ok_or_else(unexpected)?)?;
    let sender_id = request.sender_id.ok_or_else(unexpected)?;
    appointment_service(&state)
        .send_message(
            appointment_id,
            request.body.as_deref().unwrap_or_default(),
            &sender_id,
            timestamp,
        )
        .await?;
    Ok(StatusCode::OK)
}

/// GET /api/v2/admin/appointments – all appointments of any status, paged
pub async fn list_appointments(
    State(state): State<Arc<AdminState>>,
    _admin: AdminPrincipal,
    Query(query): Query<HashMap<String, String>>,
) -> AdminResult<Json<PagedResponse<AppointmentResponse>>> {
    let page = query_number(&query, "page", 0)?;
    let size = query_number(&query, "size", DEFAULT_PAGE_SIZE)?;
    let (page, size) = panache_page(page, size)?;
    let (appointments, total) = appointment_service(&state).list_page(page, size).await?;
    Ok(Json(PagedResponse {
        data: appointments.into_iter().map(Into::into).collect(),
        meta: PageMeta { page, size, total },
    }))
}

// ===== friendship =====

/// POST /api/v2/admin/friendship/befriend – everybody in the list becomes friends with everybody
pub async fn befriend(
    State(state): State<Arc<AdminState>>,
    _admin: AdminPrincipal,
    body: Bytes,
) -> AdminResult<StatusCode> {
    let request: FriendGroupRequest =
        parse_body(&body)?.ok_or(AdminError::Status(StatusCode::BAD_REQUEST))?;
    let user_ids = request.user_ids.ok_or_else(unexpected)?;
    let friendships = FriendshipService::new(state.db_pool.clone(), state.event_publisher.clone());
    for (i, user_id) in user_ids.iter().enumerate() {
        for other_id in &user_ids[i..] {
            friendships.befriend(user_id, other_id).await?;
        }
    }
    Ok(StatusCode::OK)
}

// ===== groups =====

/// POST /api/v2/admin/groups – answers 201 with the new group's id as body
pub async fn create_group(
    State(state): State<Arc<AdminState>>,
    _admin: AdminPrincipal,
    body: Bytes,
) -> AdminResult<(StatusCode, Json<i64>)> {
    let request = parse_body::<AdminCreateGroupRequest>(&body)?
        .filter(|request| request.owner_id.is_some())
        .ok_or(AdminError::Status(StatusCode::BAD_REQUEST))?;
    let owner_id = request.owner_id.unwrap_or_default();
    let group = GroupService::new(state.db_pool.clone(), state.event_publisher.clone())
        .create_group(
            &owner_id,
            Some(GroupDto {
                name: request.group_name,
                ..GroupDto::default()
            }),
        )
        .await?;
    Ok((StatusCode::CREATED, Json(group.id)))
}

/// POST /api/v2/admin/groups/{groupId}/users/{userId} – always 200 unless the group is unknown;
/// a user who already belongs to the group is not an error
pub async fn add_group_member(
    State(state): State<Arc<AdminState>>,
    _admin: AdminPrincipal,
    Path((group_id, user_id)): Path<(String, String)>,
) -> AdminResult<StatusCode> {
    let group_id = path_number(&group_id)?;
    let result = GroupService::new(state.db_pool.clone(), state.event_publisher.clone())
        .add_group_member_as_admin(group_id, &user_id)
        .await;
    match result {
        Ok(()) | Err(AppError::ValidationError { .. }) => Ok(StatusCode::OK),
        Err(error) => Err(error.into()),
    }
}

// ===== push =====

/// POST /api/v2/admin/push/test/{userId} – sends the body as payload, or a fixed greeting; 204
pub async fn send_test_push(
    State(state): State<Arc<AdminState>>,
    _admin: AdminPrincipal,
    Path(user_id): Path<String>,
    body: Bytes,
) -> AdminResult<StatusCode> {
    let payload = if body.is_empty() {
        json!({ "title": TEST_NOTIFICATION_TITLE, "body": TEST_NOTIFICATION_BODY }).to_string()
    } else {
        String::from_utf8_lossy(&body).into_owned()
    };
    state.push.send(&user_id, &payload).await;
    Ok(StatusCode::NO_CONTENT)
}

/// GET /api/v2/admin/push/log – filtered log of send attempts, newest first
pub async fn list_push_log(
    State(state): State<Arc<AdminState>>,
    _admin: AdminPrincipal,
    Query(query): Query<HashMap<String, String>>,
) -> AdminResult<Response> {
    let page = query_number(&query, "page", 0)?;
    let size = query_number(&query, "size", DEFAULT_LOG_PAGE_SIZE)?;
    let filter = PushLogFilter {
        user_id: query.get("user_id").cloned(),
        from: query
            .get("from")
            .map(|raw| parse_instant(raw))
            .transpose()?,
        to: query.get("to").map(|raw| parse_instant(raw)).transpose()?,
        // `Boolean.valueOf`: everything but "true" is false
        success: query
            .get("success")
            .map(|raw| raw.eq_ignore_ascii_case("true")),
        notification_type: query.get("notification_type").cloned(),
    };
    let (page, size) = panache_page(page, size)?;
    let entries =
        repository::find_push_log(&state.db_pool, &filter, page.saturating_mul(size), size).await?;
    Ok(Json(entries).into_response())
}

// ===== statistics =====

/// GET /api/v2/admin/stats – row counts of the database and the user count of Keycloak
pub async fn get_statistics(
    State(state): State<Arc<AdminState>>,
    _admin: AdminPrincipal,
) -> AdminResult<Json<AdminStatisticsDto>> {
    let pool = &state.db_pool;
    let users = state
        .keycloak
        .count_users()
        .await
        .map_err(keycloak_failure)?;
    Ok(Json(AdminStatisticsDto {
        users: Total { total: users },
        appointments: repository::appointment_statistics(pool).await?,
        participations: repository::participation_statistics(pool).await?,
        groups: repository::group_statistics(pool).await?,
        friendships: repository::friendship_statistics(pool).await?,
        messages: repository::message_statistics(pool).await?,
        push_subscriptions: repository::push_subscription_statistics(pool).await?,
    }))
}

// ===== users (Keycloak) =====

fn user_dto(user: KeycloakUser) -> AdminUserDto {
    AdminUserDto {
        id: user.id,
        first_name: user.first_name,
        last_name: user.last_name,
        email: user.email,
        created_at: user
            .created_timestamp
            .and_then(DateTime::<Utc>::from_timestamp_millis)
            .map(format_instant),
    }
}

fn keycloak_failure(error: KeycloakError) -> AdminError {
    tracing::error!("Keycloak admin call failed: {}", error);
    unexpected()
}

/// GET /api/v2/admin/users – a page of the Keycloak users
pub async fn list_users(
    State(state): State<Arc<AdminState>>,
    _admin: AdminPrincipal,
    Query(query): Query<HashMap<String, String>>,
) -> AdminResult<Json<AdminUserListResponse>> {
    let page = query_number(&query, "page", 0)?;
    let size = query_number(&query, "size", DEFAULT_PAGE_SIZE)?;
    let users = state
        .keycloak
        .list_users(page.saturating_mul(size), size)
        .await
        .map_err(keycloak_failure)?;
    let total = state
        .keycloak
        .count_users()
        .await
        .map_err(keycloak_failure)?;
    Ok(Json(AdminUserListResponse {
        items: users.into_iter().map(user_dto).collect(),
        page,
        size,
        total,
    }))
}

/// GET /api/v2/admin/users/{id} – 404 without body for an unknown user
pub async fn get_user(
    State(state): State<Arc<AdminState>>,
    _admin: AdminPrincipal,
    Path(id): Path<String>,
) -> AdminResult<Json<AdminUserDto>> {
    match state.keycloak.get_user(&id).await {
        Ok(user) => Ok(Json(user_dto(user))),
        Err(KeycloakError::NotFound) => Err(AdminError::Status(StatusCode::NOT_FOUND)),
        Err(error) => Err(keycloak_failure(error)),
    }
}

/// DELETE /api/v2/admin/users/{id} – 204. The Keycloak client of Java hands back the response of
/// the delete call without throwing, so an unknown user is a 204 as well.
pub async fn delete_user(
    State(state): State<Arc<AdminState>>,
    _admin: AdminPrincipal,
    Path(id): Path<String>,
) -> AdminResult<StatusCode> {
    match state.keycloak.delete_user(&id).await {
        Ok(()) | Err(KeycloakError::NotFound) => Ok(StatusCode::NO_CONTENT),
        Err(error) => Err(keycloak_failure(error)),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn is_route_not_found<T: std::fmt::Debug>(result: AdminResult<T>) -> bool {
        matches!(result, Err(AdminError::App(AppError::RouteNotFound)))
    }

    #[test]
    fn path_numbers_that_are_not_numbers_are_a_404() {
        assert_eq!(path_number("12").unwrap(), 12);
        assert!(is_route_not_found(path_number("abc")));
    }

    #[test]
    fn query_numbers_default_when_absent_and_404_when_unparsable() {
        let query = HashMap::from([
            ("page".to_string(), "3".to_string()),
            ("size".to_string(), "x".to_string()),
        ]);
        assert_eq!(query_number(&query, "page", 0).unwrap(), 3);
        assert_eq!(query_number(&query, "other", 20).unwrap(), 20);
        assert!(is_route_not_found(query_number(&query, "size", 20)));
        let too_big = HashMap::from([("size".to_string(), "99999999999".to_string())]);
        assert!(is_route_not_found(query_number(&too_big, "size", 20)));
    }

    #[test]
    fn panache_rejects_negative_pages_and_empty_sizes() {
        assert_eq!(panache_page(0, 1).unwrap(), (0, 1));
        assert!(panache_page(-1, 20).is_err());
        assert!(panache_page(0, 0).is_err());
    }

    #[test]
    fn instants_follow_instant_parse() {
        assert!(parse_instant("2030-01-01T10:00:00Z").is_ok());
        assert!(parse_instant("2030-01-01").is_err());
    }

    #[test]
    fn bodies_are_optional_but_must_be_readable() {
        let none: Option<FriendGroupRequest> = parse_body(&Bytes::new()).unwrap();
        assert!(none.is_none());
        let null: Option<FriendGroupRequest> = parse_body(&Bytes::from("null")).unwrap();
        assert!(null.is_none());
        assert!(parse_body::<FriendGroupRequest>(&Bytes::from("{")).is_err());
    }

    #[test]
    fn user_dto_formats_the_creation_time_like_instant_to_string() {
        let user = KeycloakUser {
            created_timestamp: Some(1_700_000_000_000),
            ..KeycloakUser::default()
        };
        assert_eq!(
            user_dto(user).created_at.as_deref(),
            Some("2023-11-14T22:13:20Z")
        );
    }
}
