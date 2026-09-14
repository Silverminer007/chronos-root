use crate::groups::errors::GroupServiceError;
use crate::groups::models::Group;
use uuid::Uuid;

/// Helper to check if user is the owner of a group
pub fn ensure_group_owner(group: &Group, user_id: Uuid) -> Result<(), GroupServiceError> {
    if group.owner_id == user_id {
        Ok(())
    } else {
        Err(GroupServiceError::NotAuthorized(
            "Not authorized to perform this action on this group".to_string(),
        ))
    }
}

/// Helper to check if user is the recipient of a friendship request
pub fn ensure_friendship_recipient(recipient_id: Uuid, user_id: Uuid) -> Result<(), GroupServiceError> {
    if recipient_id == user_id {
        Ok(())
    } else {
        Err(GroupServiceError::NotAuthorized(
            "Not authorized to perform this action on this friendship request".to_string(),
        ))
    }
}
