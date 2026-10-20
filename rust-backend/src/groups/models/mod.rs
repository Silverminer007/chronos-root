use chrono::{DateTime, SecondsFormat, Utc};
use serde::{Deserialize, Serialize};
use sqlx::FromRow;

use crate::users::identity::UserIdentity;

// ===== Domain Models =====

/// Group entity (table `groups`: `groupname`, `owner_oidcid`)
#[derive(Debug, Clone, FromRow)]
pub struct Group {
    pub id: i64,
    /// Nullable column in the Java schema
    pub name: Option<String>,
    /// OIDC subject of the owner
    pub owner_id: String,
}

/// Group member relationship (table `group_member`)
#[derive(Debug, Clone, FromRow)]
pub struct GroupMember {
    pub id: i64,
    pub group_id: i64,
    /// OIDC subject of the member
    pub user_id: String,
}

/// Status of a friendship request (stored as the enum name, like Hibernate's `EnumType.STRING`)
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "UPPERCASE")]
pub enum FriendshipStatus {
    Pending,
    Accepted,
    Declined,
}

impl FriendshipStatus {
    /// The stored value
    pub fn as_str(&self) -> &'static str {
        match self {
            FriendshipStatus::Pending => "PENDING",
            FriendshipStatus::Accepted => "ACCEPTED",
            FriendshipStatus::Declined => "DECLINED",
        }
    }
}

/// Friendship request (table `friendship_requests`: requester and addressee)
#[derive(Debug, Clone, FromRow)]
pub struct Friendship {
    pub id: i64,
    pub requester_id: String,
    pub addressee_id: String,
    pub status: String,
    pub created_at: DateTime<Utc>,
    pub responded_at: Option<DateTime<Utc>>,
}

impl Friendship {
    /// True if the request has the given status
    pub fn has_status(&self, status: FriendshipStatus) -> bool {
        self.status == status.as_str()
    }

    /// The party that is not `user_id`
    pub fn other_party(&self, user_id: &str) -> &str {
        if self.requester_id == user_id {
            &self.addressee_id
        } else {
            &self.requester_id
        }
    }
}

// ===== DTOs for API Requests/Responses (JSON shapes of the Java DTOs) =====

/// A user as `{id, first_name, last_name}` (Java: `UserDto`, nulls are serialized)
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct UserDto {
    pub id: String,
    pub first_name: Option<String>,
    pub last_name: Option<String>,
}

impl From<UserIdentity> for UserDto {
    fn from(user: UserIdentity) -> Self {
        Self {
            id: user.oidc_id,
            first_name: user.first_name,
            last_name: user.last_name,
        }
    }
}

/// Request and response body of the group endpoints (Java: `GroupDto`, nulls are serialized)
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct GroupDto {
    #[serde(default)]
    pub id: Option<i64>,
    #[serde(default)]
    pub name: Option<String>,
    #[serde(default)]
    pub members: Option<Vec<UserDto>>,
}

impl GroupDto {
    /// A group without its members (create and edit responses)
    pub fn without_members(group: Group) -> Self {
        Self {
            id: Some(group.id),
            name: group.name,
            members: None,
        }
    }
}

/// A friend (Java: `FriendDto`, nulls are omitted)
#[derive(Debug, Clone, Serialize)]
pub struct FriendDto {
    pub user_id: String,
    pub name: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub email: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub profile_picture_url: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub friends_since: Option<String>,
}

/// An open friendship request seen from one side (Java: `FriendshipRequestDto`, nulls are omitted)
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct FriendshipRequestDto {
    pub request_id: i64,
    pub user_id: String,
    pub user_name: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub user_email: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub profile_picture_url: Option<String>,
    pub status: FriendshipStatus,
    pub created_at: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub responded_at: Option<String>,
    pub incoming: bool,
}

/// Formats a timestamp like Java's `Instant.toString()`: UTC, `Z`, and a fraction of 3, 6 or 9 digits
/// only if needed
pub fn format_instant(instant: DateTime<Utc>) -> String {
    use chrono::Timelike;
    let nanos = instant.nanosecond();
    let format = if nanos == 0 {
        SecondsFormat::Secs
    } else if nanos.is_multiple_of(1_000_000) {
        SecondsFormat::Millis
    } else if nanos.is_multiple_of(1_000) {
        SecondsFormat::Micros
    } else {
        SecondsFormat::Nanos
    };
    instant.to_rfc3339_opts(format, true)
}

#[cfg(test)]
mod tests {
    use super::*;
    use chrono::TimeZone;

    #[test]
    fn test_format_instant_matches_java_instant_to_string() {
        let whole = Utc.with_ymd_and_hms(2026, 10, 20, 6, 15, 5).unwrap();
        assert_eq!(format_instant(whole), "2026-10-20T06:15:05Z");

        let millis = whole + chrono::Duration::milliseconds(120);
        assert_eq!(format_instant(millis), "2026-10-20T06:15:05.120Z");

        let micros = whole + chrono::Duration::microseconds(123_456);
        assert_eq!(format_instant(micros), "2026-10-20T06:15:05.123456Z");

        let nanos = whole + chrono::Duration::nanoseconds(1);
        assert_eq!(format_instant(nanos), "2026-10-20T06:15:05.000000001Z");
    }

    #[test]
    fn test_group_dto_serializes_null_members_and_names() {
        let dto = GroupDto {
            id: Some(7),
            name: Some("Team".to_string()),
            members: None,
        };
        assert_eq!(
            serde_json::to_value(&dto).unwrap(),
            serde_json::json!({"id": 7, "name": "Team", "members": null})
        );
    }

    #[test]
    fn test_group_dto_ignores_unknown_and_missing_fields() {
        let dto: GroupDto = serde_json::from_str(r#"{"name":"Team","owner":"x"}"#).unwrap();
        assert_eq!(dto.name.as_deref(), Some("Team"));
        assert!(dto.id.is_none() && dto.members.is_none());
    }

    #[test]
    fn test_friendship_request_dto_uses_java_property_names_and_omits_nulls() {
        let dto = FriendshipRequestDto {
            request_id: 3,
            user_id: "bob".to_string(),
            user_name: "Bob".to_string(),
            user_email: None,
            profile_picture_url: None,
            status: FriendshipStatus::Pending,
            created_at: "2026-10-20T06:15:05Z".to_string(),
            responded_at: None,
            incoming: true,
        };
        assert_eq!(
            serde_json::to_value(&dto).unwrap(),
            serde_json::json!({
                "requestId": 3, "userId": "bob", "userName": "Bob", "status": "PENDING",
                "createdAt": "2026-10-20T06:15:05Z", "incoming": true
            })
        );
    }

    #[test]
    fn test_other_party_returns_the_counterpart() {
        let friendship = Friendship {
            id: 1,
            requester_id: "a".to_string(),
            addressee_id: "b".to_string(),
            status: "ACCEPTED".to_string(),
            created_at: Utc::now(),
            responded_at: None,
        };
        assert_eq!(friendship.other_party("a"), "b");
        assert_eq!(friendship.other_party("b"), "a");
        assert!(friendship.has_status(FriendshipStatus::Accepted));
    }
}
