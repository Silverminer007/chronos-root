use crate::event_bus::{Event, EventBus};
use crate::groups::events::*;
use crate::groups::models::{Friendship, Group, GroupMember};
use crate::groups::repository::{FriendshipRepository, GroupMemberRepository, GroupRepository};
use serde_json::json;
use sqlx::PgPool;
use std::sync::Arc;
use uuid::Uuid;

/// Service for managing groups
pub struct GroupService {
    pool: Arc<PgPool>,
    event_bus: Arc<dyn EventBus>,
}

impl GroupService {
    pub fn new(pool: Arc<PgPool>, event_bus: Arc<dyn EventBus>) -> Self {
        Self { pool, event_bus }
    }

    /// Create a new group
    pub async fn create_group(
        &self,
        owner_id: Uuid,
        name: String,
        description: Option<String>,
    ) -> Result<Group, Box<dyn std::error::Error + Send + Sync>> {
        let id = Uuid::new_v4();

        // Create the group in the database
        let group = GroupRepository::create(&self.pool, id, name.clone(), description, owner_id).await?;

        // Fire the GroupCreatedEvent
        let event = Event::new(
            "GroupCreatedEvent",
            json!(GroupCreatedEvent {
                group_id: group.id,
                name: group.name.clone(),
                owner_id: group.owner_id,
            }),
        );
        self.event_bus.fire(event).await?;

        Ok(group)
    }

    /// Get a group by ID
    pub async fn get_group(&self, id: Uuid) -> Result<Option<Group>, Box<dyn std::error::Error + Send + Sync>> {
        Ok(GroupRepository::get_by_id(&self.pool, id).await?)
    }

    /// List all groups for a user (both owned and member of)
    pub async fn list_groups(&self, user_id: Uuid) -> Result<Vec<Group>, Box<dyn std::error::Error + Send + Sync>> {
        Ok(GroupRepository::list_for_user(&self.pool, user_id).await?)
    }

    /// List all members of a group (only owner can list)
    pub async fn list_members(
        &self,
        group_id: Uuid,
        requester_id: Uuid,
    ) -> Result<Vec<GroupMember>, Box<dyn std::error::Error + Send + Sync>> {
        // Check authorization - only owner can list members
        let group = GroupRepository::get_by_id(&self.pool, group_id)
            .await?
            .ok_or("Group not found")?;

        if group.owner_id != requester_id {
            return Err("Not authorized to list group members".into());
        }

        Ok(GroupMemberRepository::get_members(&self.pool, group_id).await?)
    }

    /// Update a group (only owner can update)
    pub async fn update_group(
        &self,
        group_id: Uuid,
        requester_id: Uuid,
        name: Option<String>,
        description: Option<String>,
    ) -> Result<Option<Group>, Box<dyn std::error::Error + Send + Sync>> {
        // Check authorization
        let group = GroupRepository::get_by_id(&self.pool, group_id)
            .await?
            .ok_or("Group not found")?;

        if group.owner_id != requester_id {
            return Err("Not authorized to update this group".into());
        }

        // Update the group
        let updated = GroupRepository::update(&self.pool, group_id, name.clone(), description).await?;

        if let Some(ref updated_group) = updated {
            // Fire the GroupNameChangedEvent if any field was updated
            if name.is_some() || description.is_some() {
                let event = Event::new(
                    "GroupNameChangedEvent",
                    json!(GroupNameChangedEvent {
                        group_id: updated_group.id,
                        new_name: updated_group.name.clone(),
                    }),
                );
                self.event_bus.fire(event).await?;
            }
        }

        Ok(updated)
    }

    /// Delete a group (only owner can delete)
    pub async fn delete_group(
        &self,
        group_id: Uuid,
        requester_id: Uuid,
    ) -> Result<bool, Box<dyn std::error::Error + Send + Sync>> {
        // Check authorization
        let group = GroupRepository::get_by_id(&self.pool, group_id)
            .await?
            .ok_or("Group not found")?;

        if group.owner_id != requester_id {
            return Err("Not authorized to delete this group".into());
        }

        // Delete the group
        let deleted = GroupRepository::delete(&self.pool, group_id).await?;

        if deleted {
            // Fire the GroupDeletedEvent
            let event = Event::new(
                "GroupDeletedEvent",
                json!(GroupDeletedEvent { group_id: group.id }),
            );
            self.event_bus.fire(event).await?;
        }

        Ok(deleted)
    }

    /// Add a member to a group (only owner can add members)
    pub async fn add_member(
        &self,
        group_id: Uuid,
        requester_id: Uuid,
        user_id: Uuid,
    ) -> Result<GroupMember, Box<dyn std::error::Error + Send + Sync>> {
        // Check authorization
        let group = GroupRepository::get_by_id(&self.pool, group_id)
            .await?
            .ok_or("Group not found")?;

        if group.owner_id != requester_id {
            return Err("Not authorized to add members to this group".into());
        }

        let id = Uuid::new_v4();
        let member = GroupMemberRepository::add_member(&self.pool, id, group_id, user_id).await?;

        // Fire the GroupMemberAddedEvent
        let event = Event::new(
            "GroupMemberAddedEvent",
            json!(GroupMemberAddedEvent {
                group_id: member.group_id,
                user_id: member.user_id,
            }),
        );
        self.event_bus.fire(event).await?;

        Ok(member)
    }

    /// Remove a member from a group (only owner can remove members)
    pub async fn remove_member(
        &self,
        group_id: Uuid,
        requester_id: Uuid,
        user_id: Uuid,
    ) -> Result<bool, Box<dyn std::error::Error + Send + Sync>> {
        // Check authorization
        let group = GroupRepository::get_by_id(&self.pool, group_id)
            .await?
            .ok_or("Group not found")?;

        if group.owner_id != requester_id {
            return Err("Not authorized to remove members from this group".into());
        }

        let removed = GroupMemberRepository::remove_member(&self.pool, group_id, user_id).await?;

        if removed {
            // Fire the GroupMemberRemovedEvent
            let event = Event::new(
                "GroupMemberRemovedEvent",
                json!(GroupMemberRemovedEvent {
                    group_id,
                    user_id,
                }),
            );
            self.event_bus.fire(event).await?;
        }

        Ok(removed)
    }
}

/// Service for managing friendships
pub struct FriendshipService {
    pool: Arc<PgPool>,
    event_bus: Arc<dyn EventBus>,
}

impl FriendshipService {
    pub fn new(pool: Arc<PgPool>, event_bus: Arc<dyn EventBus>) -> Self {
        Self { pool, event_bus }
    }

    /// Send a friendship request
    pub async fn send_request(
        &self,
        requester_id: Uuid,
        recipient_id: Uuid,
    ) -> Result<Friendship, Box<dyn std::error::Error + Send + Sync>> {
        if requester_id == recipient_id {
            return Err("Cannot send friendship request to yourself".into());
        }

        let id = Uuid::new_v4();
        let friendship =
            FriendshipRepository::create_request(&self.pool, id, requester_id, recipient_id).await?;

        // Fire the FriendshipRequestSentEvent
        let event = Event::new(
            "FriendshipRequestSentEvent",
            json!(FriendshipRequestSentEvent {
                friendship_id: friendship.id,
                requester_id: friendship.requester_id,
                recipient_id: friendship.recipient_id,
            }),
        );
        self.event_bus.fire(event).await?;

        Ok(friendship)
    }

    /// Accept a friendship request
    pub async fn accept_request(
        &self,
        friendship_id: Uuid,
        requester_id: Uuid,
    ) -> Result<Friendship, Box<dyn std::error::Error + Send + Sync>> {
        // Check authorization
        let friendship = FriendshipRepository::get_by_id(&self.pool, friendship_id)
            .await?
            .ok_or("Friendship request not found")?;

        if friendship.recipient_id != requester_id {
            return Err("Not authorized to accept this friendship request".into());
        }

        let accepted = FriendshipRepository::accept(&self.pool, friendship_id)
            .await?
            .ok_or("Failed to accept friendship request")?;

        // Fire the FriendshipAcceptedEvent
        let event = Event::new(
            "FriendshipAcceptedEvent",
            json!(FriendshipAcceptedEvent {
                friendship_id: accepted.id,
                user_id_1: accepted.requester_id,
                user_id_2: accepted.recipient_id,
            }),
        );
        self.event_bus.fire(event).await?;

        Ok(accepted)
    }

    /// Decline a friendship request
    pub async fn decline_request(
        &self,
        friendship_id: Uuid,
        requester_id: Uuid,
    ) -> Result<Friendship, Box<dyn std::error::Error + Send + Sync>> {
        // Check authorization
        let friendship = FriendshipRepository::get_by_id(&self.pool, friendship_id)
            .await?
            .ok_or("Friendship request not found")?;

        if friendship.recipient_id != requester_id {
            return Err("Not authorized to decline this friendship request".into());
        }

        let declined = FriendshipRepository::decline(&self.pool, friendship_id)
            .await?
            .ok_or("Failed to decline friendship request")?;

        // Fire the FriendshipDeclinedEvent
        let event = Event::new(
            "FriendshipDeclinedEvent",
            json!(FriendshipDeclinedEvent {
                friendship_id: declined.id,
                requester_id: declined.requester_id,
                recipient_id: declined.recipient_id,
            }),
        );
        self.event_bus.fire(event).await?;

        Ok(declined)
    }

    /// Remove a friendship
    pub async fn remove_friendship(
        &self,
        friendship_id: Uuid,
        requester_id: Uuid,
    ) -> Result<bool, Box<dyn std::error::Error + Send + Sync>> {
        // Check authorization
        let friendship = FriendshipRepository::get_by_id(&self.pool, friendship_id)
            .await?
            .ok_or("Friendship not found")?;

        if friendship.requester_id != requester_id && friendship.recipient_id != requester_id {
            return Err("Not authorized to remove this friendship".into());
        }

        let removed = FriendshipRepository::delete(&self.pool, friendship_id).await?;

        if removed {
            // Fire the FriendshipRemovedEvent
            let event = Event::new(
                "FriendshipRemovedEvent",
                json!(FriendshipRemovedEvent {
                    friendship_id,
                    user_id_1: friendship.requester_id,
                    user_id_2: friendship.recipient_id,
                }),
            );
            self.event_bus.fire(event).await?;
        }

        Ok(removed)
    }

    /// Get friendship between two users
    pub async fn get_friendship(
        &self,
        user1_id: Uuid,
        user2_id: Uuid,
    ) -> Result<Option<Friendship>, Box<dyn std::error::Error + Send + Sync>> {
        Ok(FriendshipRepository::get_friendship(&self.pool, user1_id, user2_id).await?)
    }

    /// List all friendships for a user
    pub async fn list_friendships(&self, user_id: Uuid) -> Result<Vec<Friendship>, Box<dyn std::error::Error + Send + Sync>> {
        Ok(FriendshipRepository::list_for_user(&self.pool, user_id).await?)
    }

    /// List pending friendship requests for a user (only as recipient)
    pub async fn list_pending_requests(
        &self,
        user_id: Uuid,
    ) -> Result<Vec<Friendship>, Box<dyn std::error::Error + Send + Sync>> {
        let friendships = FriendshipRepository::list_for_user(&self.pool, user_id).await?;
        // Filter to only show pending requests where user is the recipient
        let pending: Vec<Friendship> = friendships
            .into_iter()
            .filter(|f| f.recipient_id == user_id && f.status == "PENDING")
            .collect();
        Ok(pending)
    }
}
