use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use sqlx::FromRow;

// ===== Domain Models =====

/// Group entity (table `groups`: `groupname`, `owner_oidcid`)
#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct Group {
    pub id: i64,
    pub name: String,
    /// OIDC subject of the owner
    pub owner_id: String,
}

/// Group member relationship (table `group_member`)
#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct GroupMember {
    pub id: i64,
    pub group_id: i64,
    /// OIDC subject of the member
    pub user_id: String,
}

/// Friendship relationship (table `friendship_requests`: requester and addressee)
#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct Friendship {
    pub id: i64,
    pub requester_id: String,
    pub recipient_id: String,
    pub status: String, // PENDING, ACCEPTED, DECLINED
    pub created_at: DateTime<Utc>,
    pub responded_at: Option<DateTime<Utc>>,
}

// ===== DTOs for API Requests/Responses =====

/// Request to create a new group
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateGroupRequest {
    pub name: String,
}

/// Request to update a group
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UpdateGroupRequest {
    pub name: Option<String>,
}

/// Response for a group
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GroupResponse {
    pub id: i64,
    pub name: String,
    pub owner_id: String,
}

impl From<Group> for GroupResponse {
    fn from(group: Group) -> Self {
        GroupResponse {
            id: group.id,
            name: group.name,
            owner_id: group.owner_id,
        }
    }
}

/// Request to send a friendship request
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SendFriendshipRequestRequest {
    pub recipient_id: String,
}

/// Response for a friendship
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FriendshipResponse {
    pub id: i64,
    pub requester_id: String,
    pub recipient_id: String,
    pub status: String,
    pub created_at: DateTime<Utc>,
    pub responded_at: Option<DateTime<Utc>>,
}

impl From<Friendship> for FriendshipResponse {
    fn from(friendship: Friendship) -> Self {
        FriendshipResponse {
            id: friendship.id,
            requester_id: friendship.requester_id,
            recipient_id: friendship.recipient_id,
            status: friendship.status,
            created_at: friendship.created_at,
            responded_at: friendship.responded_at,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn group(id: i64) -> Group {
        Group {
            id,
            name: "Test Team".to_string(),
            owner_id: "owner-sub".to_string(),
        }
    }

    #[test]
    fn test_group_creation() {
        let group = group(7);
        assert_eq!(group.name, "Test Team");
        assert_eq!(group.owner_id, "owner-sub");
    }

    #[test]
    fn test_group_response_conversion() {
        let group = group(7);
        let response: GroupResponse = group.clone().into();
        assert_eq!(response.id, group.id);
        assert_eq!(response.name, group.name);
        assert_eq!(response.owner_id, group.owner_id);
    }

    #[test]
    fn test_group_response_serializes_numeric_id_and_string_owner() {
        let response: GroupResponse = group(7).into();
        let json = serde_json::to_value(&response).unwrap();
        assert_eq!(json["id"], serde_json::json!(7));
        assert_eq!(json["owner_id"], serde_json::json!("owner-sub"));
    }

    #[test]
    fn test_friendship_response_conversion() {
        let friendship = Friendship {
            id: 3,
            requester_id: "requester-sub".to_string(),
            recipient_id: "recipient-sub".to_string(),
            status: "PENDING".to_string(),
            created_at: Utc::now(),
            responded_at: None,
        };

        let response: FriendshipResponse = friendship.clone().into();
        assert_eq!(response.id, friendship.id);
        assert_eq!(response.status, "PENDING");

        let json = serde_json::to_value(&response).unwrap();
        assert_eq!(json["id"], serde_json::json!(3));
        assert_eq!(json["requester_id"], serde_json::json!("requester-sub"));
        assert_eq!(json["responded_at"], serde_json::Value::Null);
    }

    #[test]
    fn test_send_friendship_request_takes_an_oidc_subject() {
        let req: SendFriendshipRequestRequest =
            serde_json::from_str(r#"{"recipient_id":"some-sub"}"#).unwrap();
        assert_eq!(req.recipient_id, "some-sub");
    }
}
