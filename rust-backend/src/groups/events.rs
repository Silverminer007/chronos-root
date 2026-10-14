use serde::{Deserialize, Serialize};

/// Event fired when a group is created
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GroupCreatedEvent {
    pub group_id: i64,
    pub name: String,
    pub owner_id: String,
}

/// Event fired when a group's name or description changes
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GroupNameChangedEvent {
    pub group_id: i64,
    pub new_name: String,
}

/// Event fired when a group is deleted
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GroupDeletedEvent {
    pub group_id: i64,
}

/// Event fired when a member is added to a group
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GroupMemberAddedEvent {
    pub group_id: i64,
    pub user_id: String,
}

/// Event fired when a member is removed from a group
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GroupMemberRemovedEvent {
    pub group_id: i64,
    pub user_id: String,
}

/// Event fired when a friendship request is sent
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FriendshipRequestSentEvent {
    pub friendship_id: i64,
    pub requester_id: String,
    pub recipient_id: String,
}

/// Event fired when a friendship request is accepted
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FriendshipAcceptedEvent {
    pub friendship_id: i64,
    pub user_id_1: String,
    pub user_id_2: String,
}

/// Event fired when a friendship request is declined
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FriendshipDeclinedEvent {
    pub friendship_id: i64,
    pub requester_id: String,
    pub recipient_id: String,
}

/// Event fired when a friendship is removed
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FriendshipRemovedEvent {
    pub friendship_id: i64,
    pub user_id_1: String,
    pub user_id_2: String,
}
