//! Group authorization rules (Java: the group methods of `AuthorizationService`).
//! There is no admin bypass: the Rust principal carries no admin flag (ADR-2026-10-20-018).

use sqlx::PgPool;

use crate::error::AppError;
use crate::groups::repository::{FriendshipRepository, GroupMemberRepository, GroupRepository};

/// Only members of a group may add members, and only their friends
pub async fn require_add_group_member(
    pool: &PgPool,
    group_id: i64,
    acting_user_id: &str,
    target_user_id: &str,
) -> Result<(), AppError> {
    if !GroupMemberRepository::is_member(pool, group_id, acting_user_id).await? {
        return Err(AppError::forbidden(
            "You can only add members to group you are a member of",
        ));
    }
    if !FriendshipRepository::are_friends(pool, acting_user_id, target_user_id).await? {
        return Err(AppError::forbidden("You can only add friends to groups"));
    }
    Ok(())
}

/// Only the owner may remove members, and nobody may remove themselves
pub async fn require_remove_group_member(
    pool: &PgPool,
    group_id: i64,
    acting_user_id: &str,
    target_user_id: &str,
) -> Result<(), AppError> {
    if acting_user_id == target_user_id {
        return Err(AppError::forbidden(
            "You cannot remove yourself from a group",
        ));
    }
    if GroupRepository::is_owner(pool, group_id, acting_user_id).await? {
        return Ok(());
    }
    Err(AppError::forbidden(
        "Only the group owner can remove members",
    ))
}

/// Only members may read the member list
pub async fn require_read_group_members(
    pool: &PgPool,
    group_id: i64,
    requesting_user_id: &str,
) -> Result<(), AppError> {
    if GroupMemberRepository::is_member(pool, group_id, requesting_user_id).await? {
        return Ok(());
    }
    Err(AppError::forbidden("You are not a member of this group"))
}

/// Only the owner may edit a group
pub async fn require_edit_group(
    pool: &PgPool,
    group_id: i64,
    acting_user_id: &str,
) -> Result<(), AppError> {
    if GroupRepository::is_owner(pool, group_id, acting_user_id).await? {
        return Ok(());
    }
    Err(AppError::forbidden("Only the group owner can edit a group"))
}

/// Only the owner may delete a group
pub async fn require_delete_group(
    pool: &PgPool,
    group_id: i64,
    acting_user_id: &str,
) -> Result<(), AppError> {
    if GroupRepository::is_owner(pool, group_id, acting_user_id).await? {
        return Ok(());
    }
    Err(AppError::forbidden(
        "Only the group owner can delete a group",
    ))
}
