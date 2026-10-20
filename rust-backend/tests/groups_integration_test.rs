// Integration tests for groups and friendships services.
// These tests use testcontainers to spin up a PostgreSQL database and skip when none is available.

use async_trait::async_trait;
use chronos_date_api::event_bus::{Event, EventPublisher};
use chronos_date_api::groups::errors::{FriendshipStatus, GroupServiceError};
use chronos_date_api::groups::helpers::{ensure_friendship_recipient, ensure_group_owner};
use chronos_date_api::groups::services::{FriendshipService, GroupService};
use chronos_date_api::test_utils::{TestDb, TestFixtures};
use std::sync::{Arc, Mutex};
use uuid::Uuid;

/// Event publisher that records the type of every fired event
#[derive(Default)]
struct RecordingPublisher {
    events: Mutex<Vec<String>>,
}

impl RecordingPublisher {
    fn count(&self, event_type: &str) -> usize {
        self.events
            .lock()
            .unwrap()
            .iter()
            .filter(|t| *t == event_type)
            .count()
    }
}

#[async_trait]
impl EventPublisher for RecordingPublisher {
    async fn fire(&self, event: Event) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        self.events.lock().unwrap().push(event.event_type);
        Ok(())
    }
}

struct Setup {
    _db: TestDb,
    groups: GroupService,
    friendships: FriendshipService,
    publisher: Arc<RecordingPublisher>,
    owner: Uuid,
    other: Uuid,
}

async fn setup() -> Option<Setup> {
    let db = match TestDb::new().await {
        Ok(db) => db,
        Err(e) => {
            eprintln!("Skipping test: {}", e);
            return None;
        }
    };
    let fixtures = TestFixtures::new(db.pool().clone());
    let owner = fixtures
        .create_user("group_owner", "owner@example.com")
        .await
        .ok()?;
    let other = fixtures
        .create_user("group_other", "other@example.com")
        .await
        .ok()?;
    let pool = Arc::new(db.pool().clone());
    let publisher = Arc::new(RecordingPublisher::default());
    Some(Setup {
        groups: GroupService::new(pool.clone(), publisher.clone()),
        friendships: FriendshipService::new(pool, publisher.clone()),
        publisher,
        _db: db,
        owner,
        other,
    })
}

#[tokio::test]
async fn test_group_lifecycle_fires_events() {
    let Some(s) = setup().await else { return };

    let group = s
        .groups
        .create_group(s.owner, "Jugend".into(), Some("Gruppe".into()))
        .await
        .unwrap();
    assert_eq!(s.publisher.count("GroupCreatedEvent"), 1);
    assert_eq!(
        s.groups.get_group(group.id).await.unwrap().unwrap().name,
        "Jugend"
    );

    let updated = s
        .groups
        .update_group(group.id, s.owner, Some("Neu".into()), None)
        .await
        .unwrap()
        .unwrap();
    assert_eq!(updated.name, "Neu");
    assert_eq!(s.publisher.count("GroupNameChangedEvent"), 1);

    assert!(s.groups.delete_group(group.id, s.owner).await.unwrap());
    assert_eq!(s.publisher.count("GroupDeletedEvent"), 1);
    assert!(s.groups.get_group(group.id).await.unwrap().is_none());
}

#[tokio::test]
async fn test_group_members_and_listing() {
    let Some(s) = setup().await else { return };
    let group = s
        .groups
        .create_group(s.owner, "G".into(), None)
        .await
        .unwrap();

    let member = s
        .groups
        .add_member(group.id, s.owner, s.other)
        .await
        .unwrap();
    assert_eq!(member.user_id, s.other);
    assert_eq!(s.publisher.count("GroupMemberAddedEvent"), 1);

    let members = s.groups.list_members(group.id, s.owner).await.unwrap();
    assert_eq!(members.len(), 1);

    // Both owner and member see the group
    assert_eq!(s.groups.list_groups(s.owner).await.unwrap().len(), 1);
    assert_eq!(s.groups.list_groups(s.other).await.unwrap().len(), 1);

    assert!(s
        .groups
        .remove_member(group.id, s.owner, s.other)
        .await
        .unwrap());
    assert_eq!(s.publisher.count("GroupMemberRemovedEvent"), 1);
    assert!(s.groups.list_groups(s.other).await.unwrap().is_empty());
}

#[tokio::test]
async fn test_group_owner_authorization() {
    let Some(s) = setup().await else { return };
    let group = s
        .groups
        .create_group(s.owner, "G".into(), None)
        .await
        .unwrap();

    assert!(s.groups.list_members(group.id, s.other).await.is_err());
    assert!(s
        .groups
        .update_group(group.id, s.other, Some("x".into()), None)
        .await
        .is_err());
    assert!(s.groups.delete_group(group.id, s.other).await.is_err());
    assert!(s
        .groups
        .add_member(group.id, s.other, s.other)
        .await
        .is_err());
    assert!(s
        .groups
        .remove_member(group.id, s.other, s.owner)
        .await
        .is_err());

    // Unknown group
    let missing = Uuid::new_v4();
    assert!(s.groups.list_members(missing, s.owner).await.is_err());
    assert!(s.groups.delete_group(missing, s.owner).await.is_err());
}

#[tokio::test]
async fn test_friendship_accept_and_remove() {
    let Some(s) = setup().await else { return };

    let request = s.friendships.send_request(s.owner, s.other).await.unwrap();
    assert_eq!(request.status, "PENDING");
    assert_eq!(s.publisher.count("FriendshipRequestSentEvent"), 1);
    assert_eq!(
        s.friendships
            .list_pending_requests(s.other)
            .await
            .unwrap()
            .len(),
        1
    );
    assert!(s
        .friendships
        .list_pending_requests(s.owner)
        .await
        .unwrap()
        .is_empty());

    // Only the recipient may accept
    assert!(s
        .friendships
        .accept_request(request.id, s.owner)
        .await
        .is_err());
    let accepted = s
        .friendships
        .accept_request(request.id, s.other)
        .await
        .unwrap();
    assert_eq!(accepted.status, "ACCEPTED");
    assert_eq!(s.publisher.count("FriendshipAcceptedEvent"), 1);

    assert!(s
        .friendships
        .get_friendship(s.owner, s.other)
        .await
        .unwrap()
        .is_some());
    assert_eq!(
        s.friendships.list_friendships(s.owner).await.unwrap().len(),
        1
    );

    assert!(s
        .friendships
        .remove_friendship(request.id, s.owner)
        .await
        .unwrap());
    assert_eq!(s.publisher.count("FriendshipRemovedEvent"), 1);
    assert!(s
        .friendships
        .get_friendship(s.owner, s.other)
        .await
        .unwrap()
        .is_none());
}

#[tokio::test]
async fn test_friendship_decline_and_validation() {
    let Some(s) = setup().await else { return };

    assert!(s.friendships.send_request(s.owner, s.owner).await.is_err());

    let request = s.friendships.send_request(s.owner, s.other).await.unwrap();
    assert!(s
        .friendships
        .decline_request(request.id, s.owner)
        .await
        .is_err());
    let declined = s
        .friendships
        .decline_request(request.id, s.other)
        .await
        .unwrap();
    assert_eq!(declined.status, "DECLINED");

    let stranger = Uuid::new_v4();
    assert!(s
        .friendships
        .remove_friendship(request.id, stranger)
        .await
        .is_err());
    assert!(s
        .friendships
        .accept_request(Uuid::new_v4(), s.other)
        .await
        .is_err());
}

#[test]
fn test_ensure_helpers() {
    let id = Uuid::new_v4();
    assert!(ensure_friendship_recipient(id, id).is_ok());
    assert!(ensure_friendship_recipient(id, Uuid::new_v4()).is_err());

    let now = chrono::Utc::now();
    let group = chronos_date_api::groups::models::Group {
        id: Uuid::new_v4(),
        name: "g".into(),
        description: None,
        owner_id: id,
        created_at: now,
        updated_at: now,
    };
    assert!(ensure_group_owner(&group, id).is_ok());
    assert!(ensure_group_owner(&group, Uuid::new_v4()).is_err());
}

#[test]
fn test_group_error_display_and_conversions() {
    assert_eq!(
        GroupServiceError::NotFound("x".into()).to_string(),
        "Not found: x"
    );
    assert_eq!(
        GroupServiceError::NotAuthorized("x".into()).to_string(),
        "Not authorized: x"
    );
    assert_eq!(
        GroupServiceError::from("bad").to_string(),
        "Invalid operation: bad"
    );
    assert_eq!(
        GroupServiceError::from(String::from("bad")).to_string(),
        "Invalid operation: bad"
    );
    assert_eq!(
        GroupServiceError::DatabaseError("db".into()).to_string(),
        "Database error: db"
    );
    assert!(GroupServiceError::from(sqlx::Error::RowNotFound)
        .to_string()
        .starts_with("Database error"));
}

#[test]
fn test_friendship_status_conversions() {
    assert_eq!(
        FriendshipStatus::from("ACCEPTED"),
        FriendshipStatus::Accepted
    );
    assert_eq!(
        FriendshipStatus::from(String::from("DECLINED")),
        FriendshipStatus::Declined
    );
    assert_eq!(
        FriendshipStatus::from("whatever"),
        FriendshipStatus::Pending
    );
    assert_eq!(FriendshipStatus::Pending.to_string(), "PENDING");
    assert_eq!(FriendshipStatus::Accepted.to_string(), "ACCEPTED");
    assert_eq!(FriendshipStatus::Declined.to_string(), "DECLINED");
}
