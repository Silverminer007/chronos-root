use serde::Serialize;
use serde_json::json;
use sqlx::PgPool;
use std::collections::HashMap;
use std::sync::Arc;

use crate::error::AppError;
use crate::event_bus::{Event, EventPublisher};
use crate::groups::authorization;
use crate::groups::events::*;
use crate::groups::models::{
    format_instant, FriendDto, Friendship, FriendshipRequestDto, FriendshipStatus, Group, GroupDto,
    UserDto,
};
use crate::groups::repository::{FriendshipRepository, GroupMemberRepository, GroupRepository};
use crate::users::identity::{IdentityRepository, UserIdentity};

/// Fires `payload` as event `event_type`. The change is already stored, so a failing event bus is
/// logged and does not fail the request (Java: the observers run after the change).
async fn fire_event(bus: &Arc<dyn EventPublisher>, event_type: &str, payload: impl Serialize) {
    let event = Event::new(event_type, json!(payload));
    if let Err(e) = bus.fire(event).await {
        tracing::error!("Failed to fire {}: {}", event_type, e);
    }
}

/// Service for managing groups (Java: `GroupService`)
pub struct GroupService {
    pool: PgPool,
    event_bus: Arc<dyn EventPublisher>,
}

impl GroupService {
    pub fn new(pool: PgPool, event_bus: Arc<dyn EventPublisher>) -> Self {
        Self { pool, event_bus }
    }

    /// Groups the user is a member of, with their members resolved from `user_profiles`
    pub async fn search_groups(
        &self,
        user_id: &str,
        search: Option<&str>,
    ) -> Result<Vec<GroupDto>, AppError> {
        let search = search.filter(|s| !s.is_empty());
        let groups = GroupRepository::search_for_member(&self.pool, user_id, search).await?;
        let group_ids: Vec<i64> = groups.iter().map(|g| g.id).collect();
        let members = GroupMemberRepository::list_for_groups(&self.pool, &group_ids).await?;
        let member_ids: Vec<String> = members.iter().map(|m| m.user_id.clone()).collect();
        let users = IdentityRepository::find_by_ids(&self.pool, &member_ids).await?;

        Ok(groups
            .into_iter()
            .map(|group| {
                let group_members = members
                    .iter()
                    .filter(|m| m.group_id == group.id)
                    .map(|m| UserDto::from(users[&m.user_id].clone()))
                    .collect();
                GroupDto {
                    id: Some(group.id),
                    name: group.name,
                    members: Some(group_members),
                }
            })
            .collect())
    }

    /// Create a group; the creator becomes its first member
    pub async fn create_group(
        &self,
        acting_user_id: &str,
        dto: Option<GroupDto>,
    ) -> Result<Group, AppError> {
        let name = require_group_name(dto)?;

        let mut tx = self.pool.begin().await?;
        let group = GroupRepository::create(&mut *tx, &name, acting_user_id).await?;
        GroupMemberRepository::add(&mut *tx, group.id, acting_user_id).await?;
        tx.commit().await?;

        fire_event(
            &self.event_bus,
            "GroupCreatedEvent",
            GroupCreatedEvent {
                group_id: group.id,
                acting_user_id: acting_user_id.to_string(),
            },
        )
        .await;
        Ok(group)
    }

    /// Rename a group (owner only)
    pub async fn edit_group(
        &self,
        acting_user_id: &str,
        group_id: i64,
        dto: Option<GroupDto>,
    ) -> Result<Group, AppError> {
        authorization::require_edit_group(&self.pool, group_id, acting_user_id).await?;
        let name = require_group_name(dto)?;
        let mut group = GroupRepository::find_by_id(&self.pool, group_id)
            .await?
            .ok_or_else(|| AppError::not_found("group", group_id.to_string()))?;

        GroupRepository::rename(&self.pool, group_id, &name).await?;
        fire_event(
            &self.event_bus,
            "GroupNameChangedEvent",
            GroupNameChangedEvent {
                group_id,
                old_name: group.name.take(),
                new_name: name.clone(),
                acting_user_id: acting_user_id.to_string(),
            },
        )
        .await;
        group.name = Some(name);
        Ok(group)
    }

    /// Delete a group (owner only)
    pub async fn delete_group(&self, acting_user_id: &str, group_id: i64) -> Result<(), AppError> {
        authorization::require_delete_group(&self.pool, group_id, acting_user_id).await?;
        if !GroupRepository::delete(&self.pool, group_id).await? {
            return Err(AppError::not_found("group", group_id.to_string()));
        }
        fire_event(
            &self.event_bus,
            "GroupDeletedEvent",
            GroupDeletedEvent {
                group_id,
                acting_user_id: acting_user_id.to_string(),
            },
        )
        .await;
        Ok(())
    }

    /// Members of a group (members only)
    pub async fn group_users(
        &self,
        requesting_user_id: &str,
        group_id: i64,
    ) -> Result<Vec<UserDto>, AppError> {
        authorization::require_read_group_members(&self.pool, group_id, requesting_user_id).await?;
        let members = GroupMemberRepository::list_for_groups(&self.pool, &[group_id]).await?;
        let member_ids: Vec<String> = members.iter().map(|m| m.user_id.clone()).collect();
        let users = IdentityRepository::find_by_ids(&self.pool, &member_ids).await?;

        Ok(members
            .iter()
            .map(|m| UserDto::from(users[&m.user_id].clone()))
            .collect())
    }

    /// Add a friend to a group (members only)
    pub async fn add_group_member(
        &self,
        acting_user_id: &str,
        group_id: i64,
        target_user_id: &str,
    ) -> Result<(), AppError> {
        authorization::require_add_group_member(
            &self.pool,
            group_id,
            acting_user_id,
            target_user_id,
        )
        .await?;
        self.insert_member(acting_user_id, group_id, target_user_id)
            .await
    }

    /// Add a user to a group without the membership and friendship checks of the admin
    /// endpoint (Java passes `null` as acting user and `isAdminRequest()` skips the checks)
    pub async fn add_group_member_as_admin(
        &self,
        group_id: i64,
        target_user_id: &str,
    ) -> Result<(), AppError> {
        self.insert_member("", group_id, target_user_id).await
    }

    async fn insert_member(
        &self,
        acting_user_id: &str,
        group_id: i64,
        target_user_id: &str,
    ) -> Result<(), AppError> {
        if GroupMemberRepository::is_member(&self.pool, group_id, target_user_id).await? {
            return Err(AppError::validation_error(
                "Validierungsfehler bei Feld: user",
                HashMap::from([(
                    "user".to_string(),
                    "This user is already a member of this group".to_string(),
                )]),
            ));
        }
        if GroupRepository::find_by_id(&self.pool, group_id)
            .await?
            .is_none()
        {
            return Err(AppError::not_found("group", group_id.to_string()));
        }

        GroupMemberRepository::add(&self.pool, group_id, target_user_id).await?;
        fire_event(
            &self.event_bus,
            "GroupMemberAddedEvent",
            GroupMemberAddedEvent {
                group_id,
                new_member_id: target_user_id.to_string(),
                acting_user_id: acting_user_id.to_string(),
            },
        )
        .await;
        Ok(())
    }

    /// Remove a member from a group (owner only)
    pub async fn remove_group_member(
        &self,
        acting_user_id: &str,
        group_id: i64,
        target_user_id: &str,
    ) -> Result<(), AppError> {
        authorization::require_remove_group_member(
            &self.pool,
            group_id,
            acting_user_id,
            target_user_id,
        )
        .await?;
        if !GroupMemberRepository::remove(&self.pool, group_id, target_user_id).await? {
            return Err(validation_message("This user is not member of this group"));
        }
        fire_event(
            &self.event_bus,
            "GroupMemberRemovedEvent",
            GroupMemberRemovedEvent {
                group_id,
                removed_member_id: target_user_id.to_string(),
                acting_user_id: acting_user_id.to_string(),
            },
        )
        .await;
        Ok(())
    }
}

/// A group needs a non-blank name; a missing body is an unexpected error like Java's NPE
fn require_group_name(dto: Option<GroupDto>) -> Result<String, AppError> {
    let dto = dto.ok_or_else(AppError::unexpected)?;
    match dto.name {
        Some(name) if !name.trim().is_empty() => Ok(name),
        _ => Err(validation_message("Group name is required")),
    }
}

/// Java `new ValidationException(message)`: 400 VALIDATION_ERROR with an empty `fieldErrors`
fn validation_message(message: &str) -> AppError {
    AppError::validation_error(message, HashMap::new())
}

/// Service for managing friendships (Java: `FriendshipService`)
pub struct FriendshipService {
    pool: PgPool,
    event_bus: Arc<dyn EventPublisher>,
}

impl FriendshipService {
    pub fn new(pool: PgPool, event_bus: Arc<dyn EventPublisher>) -> Self {
        Self { pool, event_bus }
    }

    /// Send a friendship request to a user given by id or, if no id is given, by email
    pub async fn send_request(
        &self,
        requester_id: &str,
        addressee_id: Option<String>,
        email: Option<String>,
    ) -> Result<(), AppError> {
        let addressee_id = match (addressee_id, email) {
            (Some(id), _) => id,
            (None, None) => {
                return Err(AppError::bad_request(
                    "You must either set addressee_id or email",
                ))
            }
            (None, Some(email)) => IdentityRepository::find_by_email(&self.pool, &email)
                .await?
                .map(|user| user.oidc_id)
                .ok_or_else(|| {
                    AppError::not_found_message(format!(
                        "Es wurde kein User mit der E-Mail Adresse {} gefunden",
                        email
                    ))
                })?,
        };

        if requester_id == addressee_id {
            return Err(AppError::bad_request(
                "Du kannst dir nicht selbst eine Freundschaftsanfrage senden",
            ));
        }
        // Java's `existsById` falls back to Keycloak, which answers unknown ids with a
        // "deleted" sentinel, so any id is accepted (ADR-019 section 4).

        let mut tx = self.pool.begin().await?;
        if let Some(existing) =
            FriendshipRepository::find_request(&mut *tx, requester_id, &addressee_id).await?
        {
            if existing.has_status(FriendshipStatus::Accepted) {
                return Err(validation_message("Ihr seid bereits befreundet"));
            }
            if existing.has_status(FriendshipStatus::Pending) {
                return Err(validation_message(
                    if existing.requester_id == requester_id {
                        "Du hast bereits eine Freundschaftsanfrage gesendet"
                    } else {
                        "Dieser User hat dir bereits eine Freundschaftsanfrage gesendet. Bitte nimm diese an."
                    },
                ));
            }
            FriendshipRepository::delete(&mut *tx, existing.id).await?;
        }
        let request =
            FriendshipRepository::create_request(&mut *tx, requester_id, &addressee_id).await?;
        tx.commit().await?;

        let requester = IdentityRepository::find_by_id(&self.pool, requester_id).await?;
        fire_event(
            &self.event_bus,
            "FriendshipRequestSentEvent",
            FriendshipRequestSentEvent {
                request_id: request.id,
                requester_id: requester_id.to_string(),
                addressee_id,
                requester_name: requester.name(),
            },
        )
        .await;
        Ok(())
    }

    /// Make two users friends without a request (admin endpoint; Java: `AdminFriendshipService`).
    /// Nothing happens for the same user or an existing request, except a declined one, which
    /// is replaced by an accepted one. No event is fired.
    pub async fn befriend(&self, user_id: &str, other_id: &str) -> Result<(), AppError> {
        if user_id == other_id {
            return Ok(());
        }
        let mut tx = self.pool.begin().await?;
        if let Some(existing) =
            FriendshipRepository::find_request(&mut *tx, user_id, other_id).await?
        {
            if !existing.has_status(FriendshipStatus::Declined) {
                return Ok(());
            }
            FriendshipRepository::delete(&mut *tx, existing.id).await?;
        }
        let request = FriendshipRepository::create_request(&mut *tx, user_id, other_id).await?;
        FriendshipRepository::respond(&mut *tx, request.id, FriendshipStatus::Accepted).await?;
        tx.commit().await?;
        Ok(())
    }

    /// Accept a request (addressee only)
    pub async fn accept_request(&self, request_id: i64, user_id: &str) -> Result<(), AppError> {
        let request = self
            .answerable_request(
                request_id,
                user_id,
                "Du kannst nur deine eigenen Freundschaftsanfragen annehmen",
            )
            .await?;
        FriendshipRepository::respond(&self.pool, request_id, FriendshipStatus::Accepted).await?;
        fire_event(
            &self.event_bus,
            "FriendshipAcceptedEvent",
            FriendshipAcceptedEvent {
                request_id,
                requester_id: request.requester_id,
                addressee_id: request.addressee_id,
            },
        )
        .await;
        Ok(())
    }

    /// Decline a request (addressee only)
    pub async fn decline_request(&self, request_id: i64, user_id: &str) -> Result<(), AppError> {
        let request = self
            .answerable_request(
                request_id,
                user_id,
                "Du kannst nur deine eigenen Freundschaftsanfragen ablehnen",
            )
            .await?;
        FriendshipRepository::respond(&self.pool, request_id, FriendshipStatus::Declined).await?;
        fire_event(
            &self.event_bus,
            "FriendshipDeclinedEvent",
            FriendshipDeclinedEvent {
                request_id,
                requester_id: request.requester_id,
                addressee_id: request.addressee_id,
            },
        )
        .await;
        Ok(())
    }

    /// Withdraw a pending request (requester only)
    pub async fn cancel_request(&self, request_id: i64, user_id: &str) -> Result<(), AppError> {
        let request = self.find_request(request_id).await?;
        if request.requester_id != user_id {
            return Err(AppError::forbidden(
                "Du kannst nur deine eigenen Freundschaftsanfragen zurückziehen",
            ));
        }
        ensure_pending(&request)?;
        FriendshipRepository::delete(&self.pool, request_id).await?;
        Ok(())
    }

    /// End a friendship
    pub async fn remove_friendship(&self, user_id: &str, friend_id: &str) -> Result<(), AppError> {
        let friendship = FriendshipRepository::find_friendship(&self.pool, user_id, friend_id)
            .await?
            .ok_or_else(|| AppError::not_found_message("Ihr seid nicht befreundet"))?;

        FriendshipRepository::delete(&self.pool, friendship.id).await?;
        fire_event(
            &self.event_bus,
            "FriendshipRemovedEvent",
            FriendshipRemovedEvent {
                acting_user_id: user_id.to_string(),
                friend_id: friend_id.to_string(),
            },
        )
        .await;
        Ok(())
    }

    /// Friends of the user, sorted by name
    pub async fn friends(&self, user_id: &str) -> Result<Vec<FriendDto>, AppError> {
        let friendships = FriendshipRepository::list_friendships(&self.pool, user_id).await?;
        let mut by_friend: Vec<(&str, &Friendship)> = Vec::new();
        for friendship in &friendships {
            let friend_id = friendship.other_party(user_id);
            if !by_friend.iter().any(|(id, _)| *id == friend_id) {
                by_friend.push((friend_id, friendship));
            }
        }
        let friend_ids: Vec<String> = by_friend.iter().map(|(id, _)| id.to_string()).collect();
        let users = IdentityRepository::find_by_ids(&self.pool, &friend_ids).await?;

        let mut friends: Vec<FriendDto> = by_friend
            .into_iter()
            .map(|(friend_id, friendship)| {
                let friend = &users[friend_id];
                FriendDto {
                    user_id: friend.oidc_id.clone(),
                    name: friend.name(),
                    email: friend.email.clone(),
                    profile_picture_url: friend.profile_picture_url.clone(),
                    friends_since: friendship.responded_at.map(format_instant),
                }
            })
            .collect();
        friends.sort_by(|a, b| a.name.cmp(&b.name));
        Ok(friends)
    }

    /// Pending requests addressed to the user, newest first
    pub async fn incoming_requests(
        &self,
        user_id: &str,
    ) -> Result<Vec<FriendshipRequestDto>, AppError> {
        let requests = FriendshipRepository::list_incoming(&self.pool, user_id).await?;
        self.request_dtos(requests, true).await
    }

    /// Pending requests sent by the user, newest first
    pub async fn outgoing_requests(
        &self,
        user_id: &str,
    ) -> Result<Vec<FriendshipRequestDto>, AppError> {
        let requests = FriendshipRepository::list_outgoing(&self.pool, user_id).await?;
        self.request_dtos(requests, false).await
    }

    /// Users matching `search` that are not the user and not (accepted) friends
    pub async fn find_non_friends(
        &self,
        user_id: &str,
        search: &str,
        limit: i64,
    ) -> Result<Vec<UserDto>, AppError> {
        let limit = limit.max(0);
        let friend_ids = FriendshipRepository::friend_ids(&self.pool, user_id).await?;
        // Over-fetch like Java, then drop the user and the friends
        let candidates =
            IdentityRepository::search(&self.pool, search, limit.saturating_mul(3)).await?;
        Ok(candidates
            .into_iter()
            .filter(|u| u.oidc_id != user_id && !friend_ids.contains(&u.oidc_id))
            .take(limit as usize)
            .map(UserDto::from)
            .collect())
    }

    async fn find_request(&self, request_id: i64) -> Result<Friendship, AppError> {
        FriendshipRepository::find_by_id(&self.pool, request_id)
            .await?
            .ok_or_else(|| AppError::not_found("Freundschaftsanfrage", request_id.to_string()))
    }

    /// The pending request `request_id` if `user_id` is its addressee
    async fn answerable_request(
        &self,
        request_id: i64,
        user_id: &str,
        forbidden_message: &str,
    ) -> Result<Friendship, AppError> {
        let request = self.find_request(request_id).await?;
        if request.addressee_id != user_id {
            return Err(AppError::forbidden(forbidden_message));
        }
        ensure_pending(&request)?;
        Ok(request)
    }

    async fn request_dtos(
        &self,
        requests: Vec<Friendship>,
        incoming: bool,
    ) -> Result<Vec<FriendshipRequestDto>, AppError> {
        let other_id = |r: &Friendship| {
            if incoming {
                r.requester_id.clone()
            } else {
                r.addressee_id.clone()
            }
        };
        let other_ids: Vec<String> = requests.iter().map(other_id).collect();
        let users = IdentityRepository::find_by_ids(&self.pool, &other_ids).await?;

        let mut dtos: Vec<FriendshipRequestDto> = requests
            .iter()
            .map(|request| {
                let other: &UserIdentity = &users[&other_id(request)];
                FriendshipRequestDto {
                    request_id: request.id,
                    user_id: other.oidc_id.clone(),
                    user_name: other.name(),
                    user_email: other.email.clone(),
                    profile_picture_url: other.profile_picture_url.clone(),
                    status: FriendshipStatus::Pending,
                    created_at: format_instant(request.created_at),
                    responded_at: request.responded_at.map(format_instant),
                    incoming,
                }
            })
            .collect();
        // Java sorts by the ISO string of `createdAt`, newest first
        dtos.sort_by(|a, b| b.created_at.cmp(&a.created_at));
        Ok(dtos)
    }
}

fn ensure_pending(request: &Friendship) -> Result<(), AppError> {
    if request.has_status(FriendshipStatus::Pending) {
        Ok(())
    } else {
        Err(validation_message(
            "Diese Freundschaftsanfrage wurde bereits bearbeitet",
        ))
    }
}
