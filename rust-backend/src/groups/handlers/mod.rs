use axum::{
    extract::{Path, State},
    http::StatusCode,
    response::IntoResponse,
    Json,
};
use serde_json::json;
use std::sync::Arc;
use uuid::Uuid;

use crate::groups::models::{CreateGroupRequest, FriendshipResponse, GroupMember, GroupResponse, SendFriendshipRequestRequest, UpdateGroupRequest};
use crate::groups::services::{FriendshipService, GroupService};
use crate::security::PrincipalContext;

#[derive(Clone)]
pub struct GroupHandlerState {
    pub group_service: Arc<GroupService>,
    pub friendship_service: Arc<FriendshipService>,
}

// ===== Error Handling Helper =====

/// Convert service error messages to HTTP responses
fn error_to_response(error_msg: &str) -> (StatusCode, Json<serde_json::Value>) {
    if error_msg.contains("Not authorized") {
        (StatusCode::FORBIDDEN, Json(json!({"error": "Not authorized to perform this action"})))
    } else if error_msg.contains("not found") || error_msg.contains("Not found") {
        (StatusCode::NOT_FOUND, Json(json!({"error": "Resource not found"})))
    } else if error_msg.contains("yourself") {
        (StatusCode::BAD_REQUEST, Json(json!({"error": error_msg})))
    } else {
        (StatusCode::INTERNAL_SERVER_ERROR, Json(json!({"error": "Internal server error"})))
    }
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
            let (status, response) = error_to_response(&e.to_string());
            (status, response).into_response()
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
            let (status, response) = error_to_response(&e.to_string());
            (status, response).into_response()
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
            let (status, response) = error_to_response(&e.to_string());
            (status, response).into_response()
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
            let (status, response) = error_to_response(&e.to_string());
            (status, response).into_response()
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
            let (status, response) = error_to_response(&e.to_string());
            (status, response).into_response()
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
            let (status, response) = error_to_response(&e.to_string());
            (status, response).into_response()
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
            let (status, response) = error_to_response(&e.to_string());
            (status, response).into_response()
        }
    }
}

// ===== Additional Endpoints =====

/// GET /api/v2/groups/:id/members - List members of a group (owner only)
pub async fn list_group_members(
    State(state): State<GroupHandlerState>,
    Path(group_id): Path<Uuid>,
    principal: PrincipalContext,
) -> impl IntoResponse {
    match state.group_service.list_members(group_id, principal.user_id()).await {
        Ok(members) => {
            let member_ids: Vec<Uuid> = members.iter().map(|m| m.user_id).collect();
            (StatusCode::OK, Json(json!({"members": member_ids}))).into_response()
        }
        Err(e) => {
            tracing::error!("Failed to list group members: {}", e);
            let (status, response) = error_to_response(&e.to_string());
            (status, response).into_response()
        }
    }
}

/// GET /api/v2/friendships - List pending friendship requests for the user
pub async fn list_pending_friendships(
    State(state): State<GroupHandlerState>,
    principal: PrincipalContext,
) -> impl IntoResponse {
    match state.friendship_service.list_pending_requests(principal.user_id()).await {
        Ok(requests) => {
            let responses: Vec<FriendshipResponse> = requests.into_iter().map(|f| f.into()).collect();
            (StatusCode::OK, Json(responses)).into_response()
        }
        Err(e) => {
            tracing::error!("Failed to list pending friendships: {}", e);
            let (status, response) = error_to_response(&e.to_string());
            (status, response).into_response()
        }
    }
}
