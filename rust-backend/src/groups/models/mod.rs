use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use sqlx::FromRow;
use uuid::Uuid;

// ===== Domain Models =====

/// Group entity for database persistence
#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
#[sqlx(type_name = "groups")]
pub struct Group {
    pub id: Uuid,
    pub name: String,
    pub description: Option<String>,
    pub owner_id: Uuid,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
}

/// Group member relationship
#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct GroupMember {
    pub id: Uuid,
    pub group_id: Uuid,
    pub user_id: Uuid,
    pub created_at: DateTime<Utc>,
}

/// Friendship relationship
#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct Friendship {
    pub id: Uuid,
    pub requester_id: Uuid,
    pub recipient_id: Uuid,
    pub status: String, // PENDING, ACCEPTED, DECLINED
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
}

// ===== DTOs for API Requests/Responses =====

/// Request to create a new group
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateGroupRequest {
    pub name: String,
    pub description: Option<String>,
}

/// Request to update a group
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UpdateGroupRequest {
    pub name: Option<String>,
    pub description: Option<String>,
}

/// Response for a group
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GroupResponse {
    pub id: Uuid,
    pub name: String,
    pub description: Option<String>,
    pub owner_id: Uuid,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
}

impl From<Group> for GroupResponse {
    fn from(group: Group) -> Self {
        GroupResponse {
            id: group.id,
            name: group.name,
            description: group.description,
            owner_id: group.owner_id,
            created_at: group.created_at,
            updated_at: group.updated_at,
        }
    }
}

/// Request to send a friendship request
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SendFriendshipRequestRequest {
    pub recipient_id: Uuid,
}

/// Response for a friendship
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FriendshipResponse {
    pub id: Uuid,
    pub requester_id: Uuid,
    pub recipient_id: Uuid,
    pub status: String,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
}

impl From<Friendship> for FriendshipResponse {
    fn from(friendship: Friendship) -> Self {
        FriendshipResponse {
            id: friendship.id,
            requester_id: friendship.requester_id,
            recipient_id: friendship.recipient_id,
            status: friendship.status,
            created_at: friendship.created_at,
            updated_at: friendship.updated_at,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_group_creation() {
        let now = Utc::now();
        let group = Group {
            id: Uuid::new_v4(),
            name: "Test Team".to_string(),
            description: Some("A test team group".to_string()),
            owner_id: Uuid::new_v4(),
            created_at: now,
            updated_at: now,
        };

        assert_eq!(group.name, "Test Team");
        assert!(group.description.is_some());
    }

    #[test]
    fn test_group_without_description() {
        let now = Utc::now();
        let group = Group {
            id: Uuid::new_v4(),
            name: "Another Group".to_string(),
            description: None,
            owner_id: Uuid::new_v4(),
            created_at: now,
            updated_at: now,
        };

        assert!(group.description.is_none());
    }

    #[test]
    fn test_group_response_conversion() {
        let now = Utc::now();
        let group = Group {
            id: Uuid::new_v4(),
            name: "Test Group".to_string(),
            description: Some("Test Description".to_string()),
            owner_id: Uuid::new_v4(),
            created_at: now,
            updated_at: now,
        };

        let response: GroupResponse = group.clone().into();
        assert_eq!(response.id, group.id);
        assert_eq!(response.name, group.name);
    }

    #[test]
    fn test_friendship_response_conversion() {
        let now = Utc::now();
        let friendship = Friendship {
            id: Uuid::new_v4(),
            requester_id: Uuid::new_v4(),
            recipient_id: Uuid::new_v4(),
            status: "PENDING".to_string(),
            created_at: now,
            updated_at: now,
        };

        let response: FriendshipResponse = friendship.clone().into();
        assert_eq!(response.id, friendship.id);
        assert_eq!(response.status, "PENDING");
    }
}
