//! Domain events the push notification listener reacts to (Java: `WebPushService` observers).
//!
//! The event type is the Java event class name (`PostgresEventBus` channel) and the payload carries
//! the Java record components as snake_case fields, user references as OIDC subjects. See
//! ADR-2026-10-20-019 for the event -> publisher table.

use crate::appointments::models::ParticipationStatus;
use chrono::{DateTime, Utc};
use serde::Deserialize;

pub const MESSAGE_SENT: &str = "MessageSentEvent";
pub const APPOINTMENT_MOVED: &str = "AppointmentMovedEvent";
pub const APPOINTMENT_CANCELLED: &str = "AppointmentCancelledEvent";
pub const PARTICIPATION_ADDED: &str = "AppointmentParticipationAddedEvent";
pub const GROUP_PARTICIPATION_ADDED: &str = "AppointmentGroupParticipationAddedEvent";
pub const FRIENDSHIP_REQUEST_SENT: &str = "FriendshipRequestSentEvent";
pub const FRIENDSHIP_ACCEPTED: &str = "FriendshipAcceptedEvent";
pub const FRIENDSHIP_DECLINED: &str = "FriendshipDeclinedEvent";
pub const FRIENDSHIP_REMOVED: &str = "FriendshipRemovedEvent";
pub const GROUP_MEMBER_ADDED: &str = "GroupMemberAddedEvent";
pub const PARTICIPATION_STATUS_CHANGED: &str = "AppointmentParticipationStatusChangedEvent";
pub const PARTICIPATION_INVALID: &str = "AppointmentParticipationInvalidEvent";
pub const PARTICIPATION_STATUS_PENDING_REMINDER: &str =
    "AppointmentParticipationStatusPendingReminderEvent";
pub const APPOINTMENT_REMINDER: &str = "AppointmentReminderEvent";
pub const PARTICIPATION_STATUS_RECHECK_REQUESTED: &str =
    "AppointmentParticipationStatusRecheckRequestedEvent";

/// Every event type Java sends a push notification for
pub const ALL: [&str; 15] = [
    MESSAGE_SENT,
    APPOINTMENT_MOVED,
    APPOINTMENT_CANCELLED,
    PARTICIPATION_ADDED,
    GROUP_PARTICIPATION_ADDED,
    FRIENDSHIP_REQUEST_SENT,
    FRIENDSHIP_ACCEPTED,
    FRIENDSHIP_DECLINED,
    FRIENDSHIP_REMOVED,
    GROUP_MEMBER_ADDED,
    PARTICIPATION_STATUS_CHANGED,
    PARTICIPATION_INVALID,
    PARTICIPATION_STATUS_PENDING_REMINDER,
    APPOINTMENT_REMINDER,
    PARTICIPATION_STATUS_RECHECK_REQUESTED,
];

#[derive(Debug, Deserialize)]
pub struct MessageSent {
    pub message_id: i64,
}

#[derive(Debug, Deserialize)]
pub struct AppointmentMoved {
    pub appointment_id: i64,
    pub old_start: DateTime<Utc>,
    pub old_end: DateTime<Utc>,
    pub acting_user_id: String,
}

#[derive(Debug, Deserialize)]
pub struct AppointmentCancelled {
    pub appointment_id: i64,
    pub acting_user_id: String,
}

#[derive(Debug, Deserialize)]
pub struct ParticipationAdded {
    pub appointment_id: i64,
    pub target_user_id: String,
    pub acting_user_id: String,
}

#[derive(Debug, Deserialize)]
pub struct GroupParticipationAdded {
    pub appointment_id: i64,
    pub group_id: i64,
    pub acting_user_id: String,
}

#[derive(Debug, Deserialize)]
pub struct FriendshipRequestSent {
    pub request_id: i64,
    pub requester_id: String,
    pub addressee_id: String,
    pub requester_name: String,
}

/// Shared by `FriendshipAcceptedEvent` and `FriendshipDeclinedEvent`
#[derive(Debug, Deserialize)]
pub struct FriendshipAnswered {
    pub requester_id: String,
    pub addressee_id: String,
}

#[derive(Debug, Deserialize)]
pub struct FriendshipRemoved {
    pub acting_user_id: String,
    pub friend_id: String,
}

/// `user_id` is the field the groups service already publishes
#[derive(Debug, Deserialize)]
pub struct GroupMemberAdded {
    pub group_id: i64,
    #[serde(alias = "user_id")]
    pub new_member_id: String,
}

/// `user_id` is the field the appointments service already publishes (the acting user)
#[derive(Debug, Deserialize)]
pub struct ParticipationStatusChanged {
    pub appointment_id: i64,
    #[serde(alias = "acting_user_id")]
    pub user_id: String,
    pub new_status: ParticipationStatus,
}

/// Invalid, pending-reminder, reminder and recheck events only reference the appointment
#[derive(Debug, Deserialize)]
pub struct AppointmentRef {
    pub appointment_id: i64,
}
