use axum::{
    extract::{Path, State},
    http::StatusCode,
    response::IntoResponse,
    Json,
};
use serde_json::json;
use std::sync::Arc;
use uuid::Uuid;

use crate::groups::models::{CreateGroupRequest, FriendshipResponse, GroupResponse, SendFriendshipRequestRequest, UpdateGroupRequest};
use crate::groups::services::{FriendshipService, GroupService};
use crate::security::PrincipalContext;

#[derive(Clone)]
pub struct GroupHandlerState {
    pub group_service: Arc<GroupService>,
    pub friendship_service: Arc<FriendshipService>,
}

// ===== Group Handlers =====

/// POST /api/v2/groups - Create a new group
pub async fn create_group(
    State(state): State<GroupHandlerState>,
    principal: PrincipalContext,
    Json(req): Json<CreateGroupRequest>,
) -> impl IntoResponse {
    match state.group_service.create_group(principal.user_id(), req.name, req.description).await {
        Ok(group) => (StatusCode::CREATED, Json(GroupResponse::from(group))).into_response(),
        Err(e) => {
            tracing::error!("Failed to create group: {}", e);
            (StatusCode::INTERNAL_SERVER_ERROR, Json(json!({"error": "Failed to create group"}))).into_response()
        }
    }
}

/// GET /api/v2/groups - List user's groups
pub async fn list_groups(
    State(state): State<GroupHandlerState>,
    principal: PrincipalContext,
) -> impl IntoResponse {
    match state.group_service.list_groups(principal.user_id()).await {
        Ok(groups) => {
            let responses: Vec<GroupResponse> = groups.into_iter().map(|g| g.into()).collect();
            (StatusCode::OK, Json(responses)).into_response()
        }
        Err(e) => {
            tracing::error!("Failed to list groups: {}", e);
            (StatusCode::INTERNAL_SERVER_ERROR, Json(json!({"error": "Failed to list groups"}))).into_response()
        }
    }
}

/// PUT /api/v2/groups/:id - Update a group
pub async fn update_group(
    State(state): State<GroupHandlerState>,
    Path(group_id): Path<Uuid>,
    principal: PrincipalContext,
    Json(req): Json<UpdateGroupRequest>,
) -> impl IntoResponse {
    match state.group_service.update_group(group_id, principal.user_id(), req.name, req.description).await {
        Ok(Some(group)) => (StatusCode::OK, Json(GroupResponse::from(group))).into_response(),
        Ok(None) => (StatusCode::NOT_FOUND, Json(json!({"error": "Group not found"}))).into_response(),
        Err(e) => {
            tracing::error!("Failed to update group: {}", e);
            if e.to_string().contains("Not authorized") {
                (StatusCode::FORBIDDEN, Json(json!({"error": "Not authorized"}))).into_response()
            } else {
                (StatusCode::INTERNAL_SERVER_ERROR, Json(json!({"error": "Failed to update group"}))).into_response()
            }
        }
    }
}

/// DELETE /api/v2/groups/:id - Delete a group
pub async fn delete_group(
    State(state): State<GroupHandlerState>,
    Path(group_id): Path<Uuid>,
    principal: PrincipalContext,
) -> impl IntoResponse {
    match state.group_service.delete_group(group_id, principal.user_id()).await {
        Ok(true) => StatusCode::NO_CONTENT.into_response(),
        Ok(false) => (StatusCode::NOT_FOUND, Json(json!({"error": "Group not found"}))).into_response(),
        Err(e) => {
            tracing::error!("Failed to delete group: {}", e);
            if e.to_string().contains("Not authorized") {
                (StatusCode::FORBIDDEN, Json(json!({"error": "Not authorized"}))).into_response()
            } else {
                (StatusCode::INTERNAL_SERVER_ERROR, Json(json!({"error": "Failed to delete group"}))).into_response()
            }
        }
    }
}

/// POST /api/v2/groups/:id/members/:userId - Add a member to a group
pub async fn add_group_member(
    State(state): State<GroupHandlerState>,
    Path((group_id, user_id)): Path<(Uuid, Uuid)>,
    principal: PrincipalContext,
) -> impl IntoResponse {
    match state.group_service.add_member(group_id, principal.user_id(), user_id).await {
        Ok(_) => (StatusCode::CREATED, Json(json!({"success": true}))).into_response(),
        Err(e) => {
            tracing::error!("Failed to add group member: {}", e);
            if e.to_string().contains("Not authorized") {
                (StatusCode::FORBIDDEN, Json(json!({"error": "Not authorized"}))).into_response()
            } else if e.to_string().contains("not found") {
                (StatusCode::NOT_FOUND, Json(json!({"error": "Group not found"}))).into_response()
            } else {
                (StatusCode::INTERNAL_SERVER_ERROR, Json(json!({"error": "Failed to add member"}))).into_response()
            }
        }
    }
}

/// DELETE /api/v2/groups/:id/members/:userId - Remove a member from a group
pub async fn remove_group_member(
    State(state): State<GroupHandlerState>,
    Path((group_id, user_id)): Path<(Uuid, Uuid)>,
    principal: PrincipalContext,
) -> impl IntoResponse {
    match state.group_service.remove_member(group_id, principal.user_id(), user_id).await {
        Ok(true) => StatusCode::NO_CONTENT.into_response(),
        Ok(false) => (StatusCode::NOT_FOUND, Json(json!({"error": "Member not found"}))).into_response(),
        Err(e) => {
            tracing::error!("Failed to remove group member: {}", e);
            if e.to_string().contains("Not authorized") {
                (StatusCode::FORBIDDEN, Json(json!({"error": "Not authorized"}))).into_response()
            } else if e.to_string().contains("not found") {
                (StatusCode::NOT_FOUND, Json(json!({"error": "Group not found"}))).into_response()
            } else {
                (StatusCode::INTERNAL_SERVER_ERROR, Json(json!({"error": "Failed to remove member"}))).into_response()
            }
        }
    }
}

// ===== Friendship Handlers =====

/// POST /api/v2/friendships - Send a friendship request
pub async fn send_friendship_request(
    State(state): State<GroupHandlerState>,
    principal: PrincipalContext,
    Json(req): Json<SendFriendshipRequestRequest>,
) -> impl IntoResponse {
    match state.friendship_service.send_request(principal.user_id(), req.recipient_id).await {
        Ok(friendship) => (StatusCode::CREATED, Json(FriendshipResponse::from(friendship))).into_response(),
        Err(e) => {
            tracing::error!("Failed to send friendship request: {}", e);
            (StatusCode::INTERNAL_SERVER_ERROR, Json(json!({"error": e.to_string()}))).into_response()
        }
    }
}

/// POST /api/v2/friendships/:id/accept - Accept a friendship request
pub async fn accept_friendship(
    State(state): State<GroupHandlerState>,
    Path(friendship_id): Path<Uuid>,
    principal: PrincipalContext,
) -> impl IntoResponse {
    match state.friendship_service.accept_request(friendship_id, principal.user_id()).await {
        Ok(friendship) => (StatusCode::OK, Json(FriendshipResponse::from(friendship))).into_response(),
        Err(e) => {
            tracing::error!("Failed to accept friendship: {}", e);
            if e.to_string().contains("Not authorized") {
                (StatusCode::FORBIDDEN, Json(json!({"error": "Not authorized"}))).into_response()
            } else if e.to_string().contains("not found") {
                (StatusCode::NOT_FOUND, Json(json!({"error": "Friendship request not found"}))).into_response()
            } else {
                (StatusCode::INTERNAL_SERVER_ERROR, Json(json!({"error": e.to_string()}))).into_response()
            }
        }
    }
}

/// POST /api/v2/friendships/:id/decline - Decline a friendship request
pub async fn decline_friendship(
    State(state): State<GroupHandlerState>,
    Path(friendship_id): Path<Uuid>,
    principal: PrincipalContext,
) -> impl IntoResponse {
    match state.friendship_service.decline_request(friendship_id, principal.user_id()).await {
        Ok(friendship) => (StatusCode::OK, Json(FriendshipResponse::from(friendship))).into_response(),
        Err(e) => {
            tracing::error!("Failed to decline friendship: {}", e);
            if e.to_string().contains("Not authorized") {
                (StatusCode::FORBIDDEN, Json(json!({"error": "Not authorized"}))).into_response()
            } else if e.to_string().contains("not found") {
                (StatusCode::NOT_FOUND, Json(json!({"error": "Friendship request not found"}))).into_response()
            } else {
                (StatusCode::INTERNAL_SERVER_ERROR, Json(json!({"error": e.to_string()}))).into_response()
            }
        }
    }
}

/// DELETE /api/v2/friendships/:id - Remove a friendship
pub async fn delete_friendship(
    State(state): State<GroupHandlerState>,
    Path(friendship_id): Path<Uuid>,
    principal: PrincipalContext,
) -> impl IntoResponse {
    match state.friendship_service.remove_friendship(friendship_id, principal.user_id()).await {
        Ok(true) => StatusCode::NO_CONTENT.into_response(),
        Ok(false) => (StatusCode::NOT_FOUND, Json(json!({"error": "Friendship not found"}))).into_response(),
        Err(e) => {
            tracing::error!("Failed to delete friendship: {}", e);
            if e.to_string().contains("Not authorized") {
                (StatusCode::FORBIDDEN, Json(json!({"error": "Not authorized"}))).into_response()
            } else {
                (StatusCode::INTERNAL_SERVER_ERROR, Json(json!({"error": e.to_string()}))).into_response()
            }
        }
    }
}
