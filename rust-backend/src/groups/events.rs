//! Payloads of the group and friendship events (Java: `application/events/*`).
//! Fired through the event bus after the change is stored.

use serde::{Deserialize, Serialize};

/// A group was created
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GroupCreatedEvent {
    pub group_id: i64,
    pub acting_user_id: String,
}

/// A group was renamed
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GroupNameChangedEvent {
    pub group_id: i64,
    pub old_name: Option<String>,
    pub new_name: String,
    pub acting_user_id: String,
}

/// A group was deleted
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GroupDeletedEvent {
    pub group_id: i64,
    pub acting_user_id: String,
}

/// A member was added to a group
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GroupMemberAddedEvent {
    pub group_id: i64,
    pub new_member_id: String,
    pub acting_user_id: String,
}

/// A member was removed from a group
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GroupMemberRemovedEvent {
    pub group_id: i64,
    pub removed_member_id: String,
    pub acting_user_id: String,
}

/// A friendship request was sent
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FriendshipRequestSentEvent {
    pub request_id: i64,
    pub requester_id: String,
    pub addressee_id: String,
    pub requester_name: String,
}

/// A friendship request was accepted
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FriendshipAcceptedEvent {
    pub request_id: i64,
    pub requester_id: String,
    pub addressee_id: String,
}

/// A friendship request was declined
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FriendshipDeclinedEvent {
    pub request_id: i64,
    pub requester_id: String,
    pub addressee_id: String,
}

/// A friendship was ended
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FriendshipRemovedEvent {
    pub acting_user_id: String,
    pub friend_id: String,
}
