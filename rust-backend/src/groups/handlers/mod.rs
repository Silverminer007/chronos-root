//! Handlers of `/api/v2/groups` and `/api/v2/friendships` (Java: `GroupsResource`, `FriendshipResource`).
//! Errors are `AppError`s, which render the Java `ErrorResponse` body.

use axum::{
    body::Bytes,
    extract::{Path, Query, State},
    http::StatusCode,
    response::IntoResponse,
    Json,
};
use serde::Deserialize;
use std::sync::Arc;

use crate::appointments::handlers::AppState;
use crate::error::AppError;
use crate::groups::models::{GroupDto, UserDto};
use crate::groups::services::{FriendshipService, GroupService};
use crate::security::PrincipalContext;

/// Suggestions returned when the search term is missing or too short
const RECENT_SUGGESTIONS_LIMIT: i64 = 5;
/// Shortest search term that triggers a search
const MIN_SEARCH_LENGTH: usize = 2;
/// Default of the `limit` query parameter
const DEFAULT_SUGGESTIONS_LIMIT: i64 = 20;

fn group_service(state: &AppState) -> GroupService {
    GroupService::new(state.db_pool.clone(), state.event_publisher.clone())
}

fn friendship_service(state: &AppState) -> FriendshipService {
    FriendshipService::new(state.db_pool.clone(), state.event_publisher.clone())
}

/// A path or query parameter of type `Long`/`int` that cannot be converted is a 404 (Jakarta REST)
fn parse_number(raw: &str) -> Result<i64, AppError> {
    raw.parse().map_err(|_| AppError::RouteNotFound)
}

/// Parses a `GroupDto` body. A missing body is a `null` DTO; an unreadable one fails like any
/// unexpected exception in Java (500).
fn parse_group_body(body: &Bytes) -> Result<Option<GroupDto>, AppError> {
    if body.is_empty() {
        return Ok(None);
    }
    serde_json::from_slice::<Option<GroupDto>>(body).map_err(|e| {
        tracing::warn!("Unreadable group body: {}", e);
        AppError::unexpected()
    })
}

// ===== Group Handlers =====

#[derive(Debug, Deserialize)]
pub struct GroupSearchQuery {
    pub search: Option<String>,
}

/// GET /api/v2/groups - Groups of the user
pub async fn list_groups(
    State(state): State<Arc<AppState>>,
    principal: PrincipalContext,
    Query(query): Query<GroupSearchQuery>,
) -> Result<impl IntoResponse, AppError> {
    let groups = group_service(&state)
        .search_groups(principal.user_id(), query.search.as_deref())
        .await?;
    Ok(Json(groups))
}

/// POST /api/v2/groups - Create a group
pub async fn create_group(
    State(state): State<Arc<AppState>>,
    principal: PrincipalContext,
    body: Bytes,
) -> Result<impl IntoResponse, AppError> {
    let group = group_service(&state)
        .create_group(principal.user_id(), parse_group_body(&body)?)
        .await?;
    Ok(Json(GroupDto::without_members(group)))
}

/// DELETE /api/v2/groups/:group - Delete a group
pub async fn delete_group(
    State(state): State<Arc<AppState>>,
    Path(group_id): Path<String>,
    principal: PrincipalContext,
) -> Result<impl IntoResponse, AppError> {
    group_service(&state)
        .delete_group(principal.user_id(), parse_number(&group_id)?)
        .await?;
    Ok(StatusCode::NO_CONTENT)
}

/// PATCH /api/v2/groups/:group - Rename a group
pub async fn update_group(
    State(state): State<Arc<AppState>>,
    Path(group_id): Path<String>,
    principal: PrincipalContext,
    body: Bytes,
) -> Result<impl IntoResponse, AppError> {
    let group = group_service(&state)
        .edit_group(
            principal.user_id(),
            parse_number(&group_id)?,
            parse_group_body(&body)?,
        )
        .await?;
    Ok(Json(GroupDto::without_members(group)))
}

/// GET /api/v2/groups/:group/users - Members of a group
pub async fn list_group_users(
    State(state): State<Arc<AppState>>,
    Path(group_id): Path<String>,
    principal: PrincipalContext,
) -> Result<impl IntoResponse, AppError> {
    let users: Vec<UserDto> = group_service(&state)
        .group_users(principal.user_id(), parse_number(&group_id)?)
        .await?;
    Ok(Json(users))
}

/// POST /api/v2/groups/:groupId/user/:userId - Add a member to a group
pub async fn add_group_member(
    State(state): State<Arc<AppState>>,
    Path((group_id, user_id)): Path<(String, String)>,
    principal: PrincipalContext,
) -> Result<impl IntoResponse, AppError> {
    group_service(&state)
        .add_group_member(principal.user_id(), parse_number(&group_id)?, &user_id)
        .await?;
    Ok(StatusCode::NO_CONTENT)
}

/// DELETE /api/v2/groups/:groupId/user/:userId - Remove a member from a group
pub async fn remove_group_member(
    State(state): State<Arc<AppState>>,
    Path((group_id, user_id)): Path<(String, String)>,
    principal: PrincipalContext,
) -> Result<impl IntoResponse, AppError> {
    group_service(&state)
        .remove_group_member(principal.user_id(), parse_number(&group_id)?, &user_id)
        .await?;
    Ok(StatusCode::NO_CONTENT)
}

// ===== Friendship Handlers =====

#[derive(Debug, Deserialize)]
pub struct SuggestionsQuery {
    pub search: Option<String>,
    pub limit: Option<String>,
}

/// GET /api/v2/friendships/suggestions - Users that are not yet friends
pub async fn friendship_suggestions(
    State(state): State<Arc<AppState>>,
    principal: PrincipalContext,
    Query(query): Query<SuggestionsQuery>,
) -> Result<impl IntoResponse, AppError> {
    let limit = match query.limit.as_deref() {
        Some(raw) => parse_number(raw)?,
        None => DEFAULT_SUGGESTIONS_LIMIT,
    };
    let service = friendship_service(&state);
    let suggestions = match query.search.as_deref().map(str::trim) {
        Some(search) if search.chars().count() >= MIN_SEARCH_LENGTH => {
            service
                .find_non_friends(principal.user_id(), search, limit)
                .await?
        }
        _ => {
            service
                .find_non_friends(principal.user_id(), "", RECENT_SUGGESTIONS_LIMIT)
                .await?
        }
    };
    Ok(Json(suggestions))
}

#[derive(Debug, Deserialize)]
pub struct SendRequestQuery {
    pub email: Option<String>,
    pub adresse_id: Option<String>,
}

/// POST /api/v2/friendships/requests - Send a friendship request
pub async fn send_friendship_request(
    State(state): State<Arc<AppState>>,
    principal: PrincipalContext,
    Query(query): Query<SendRequestQuery>,
) -> Result<impl IntoResponse, AppError> {
    friendship_service(&state)
        .send_request(principal.user_id(), query.adresse_id, query.email)
        .await?;
    Ok(StatusCode::CREATED)
}

/// POST /api/v2/friendships/requests/:requestId/accept - Accept a request
pub async fn accept_friendship_request(
    State(state): State<Arc<AppState>>,
    Path(request_id): Path<String>,
    principal: PrincipalContext,
) -> Result<impl IntoResponse, AppError> {
    friendship_service(&state)
        .accept_request(parse_number(&request_id)?, principal.user_id())
        .await?;
    Ok(StatusCode::OK)
}

/// POST /api/v2/friendships/requests/:requestId/decline - Decline a request
pub async fn decline_friendship_request(
    State(state): State<Arc<AppState>>,
    Path(request_id): Path<String>,
    principal: PrincipalContext,
) -> Result<impl IntoResponse, AppError> {
    friendship_service(&state)
        .decline_request(parse_number(&request_id)?, principal.user_id())
        .await?;
    Ok(StatusCode::OK)
}

/// DELETE /api/v2/friendships/requests/:requestId - Withdraw a request
pub async fn cancel_friendship_request(
    State(state): State<Arc<AppState>>,
    Path(request_id): Path<String>,
    principal: PrincipalContext,
) -> Result<impl IntoResponse, AppError> {
    friendship_service(&state)
        .cancel_request(parse_number(&request_id)?, principal.user_id())
        .await?;
    Ok(StatusCode::NO_CONTENT)
}

/// GET /api/v2/friendships/requests/incoming - Pending requests addressed to the user
pub async fn incoming_friendship_requests(
    State(state): State<Arc<AppState>>,
    principal: PrincipalContext,
) -> Result<impl IntoResponse, AppError> {
    Ok(Json(
        friendship_service(&state)
            .incoming_requests(principal.user_id())
            .await?,
    ))
}

/// GET /api/v2/friendships/requests/outgoing - Pending requests sent by the user
pub async fn outgoing_friendship_requests(
    State(state): State<Arc<AppState>>,
    principal: PrincipalContext,
) -> Result<impl IntoResponse, AppError> {
    Ok(Json(
        friendship_service(&state)
            .outgoing_requests(principal.user_id())
            .await?,
    ))
}

/// GET /api/v2/friendships/friends - Friends of the user
pub async fn list_friends(
    State(state): State<Arc<AppState>>,
    principal: PrincipalContext,
) -> Result<impl IntoResponse, AppError> {
    Ok(Json(
        friendship_service(&state)
            .friends(principal.user_id())
            .await?,
    ))
}

/// DELETE /api/v2/friendships/friends/:friendId - End a friendship
pub async fn remove_friend(
    State(state): State<Arc<AppState>>,
    Path(friend_id): Path<String>,
    principal: PrincipalContext,
) -> Result<impl IntoResponse, AppError> {
    friendship_service(&state)
        .remove_friendship(principal.user_id(), &friend_id)
        .await?;
    Ok(StatusCode::NO_CONTENT)
}
