//! Request and response shapes of the admin endpoints (Java: `de.chronos_live.admin.dto`).
//! Jackson serializes `null` fields, so none of the response structs skips them.

use serde::{Deserialize, Serialize};

/// `POST /appointments/{id}/participants/groups`
#[derive(Debug, Deserialize)]
pub struct AdminAddGroupParticipantRequest {
    pub group_id: Option<i64>,
    pub user_role: Option<String>,
}

/// `POST /appointments/participants/status`
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminChangeParticipationStatusRequest {
    pub user_id: Option<String>,
    pub appointment_id: Option<i64>,
    pub participation_status: Option<String>,
}

/// `POST /appointments/message` (Java: `MessageDto`)
#[derive(Debug, Deserialize)]
pub struct AdminMessageRequest {
    pub appointment_id: Option<i64>,
    pub body: Option<String>,
    pub sender_id: Option<String>,
    pub timestamp: Option<String>,
}

/// `POST /friendship/befriend`
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct FriendGroupRequest {
    pub user_ids: Option<Vec<String>>,
}

/// `POST /groups`
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminCreateGroupRequest {
    pub owner_id: Option<String>,
    pub group_name: Option<String>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminUserDto {
    pub id: Option<String>,
    pub first_name: Option<String>,
    pub last_name: Option<String>,
    pub email: Option<String>,
    pub created_at: Option<String>,
}

#[derive(Debug, Serialize)]
pub struct AdminUserListResponse {
    pub items: Vec<AdminUserDto>,
    pub page: i64,
    pub size: i64,
    pub total: i64,
}

/// One row of `GET /push/log` (Java: `PushNotificationLogDto`)
#[derive(Debug, Serialize)]
pub struct PushNotificationLogDto {
    pub id: i64,
    pub user_id: String,
    pub notification_type: Option<String>,
    pub payload: String,
    pub endpoint: String,
    pub http_status_code: Option<i32>,
    pub success: bool,
    pub error_message: Option<String>,
    pub created_at: Option<String>,
}

/// `GET /stats` (Java: `AdminStatisticsDto`)
#[derive(Debug, Serialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct AdminStatisticsDto {
    pub users: Total,
    pub appointments: AppointmentStatistics,
    pub participations: ParticipationStatistics,
    pub groups: GroupStatistics,
    pub friendships: FriendshipStatistics,
    pub messages: Total,
    pub push_subscriptions: Total,
}

#[derive(Debug, Serialize, PartialEq, Eq)]
pub struct Total {
    pub total: i64,
}

#[derive(Debug, Serialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct AppointmentStatistics {
    pub total: i64,
    pub planned: i64,
    pub cancelled: i64,
    pub deleted: i64,
    pub not_enough_attendees: i64,
}

#[derive(Debug, Serialize, PartialEq, Eq)]
pub struct ParticipationStatistics {
    pub total: i64,
    pub pending: i64,
    pub approved: i64,
    pub rejected: i64,
}

#[derive(Debug, Serialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct GroupStatistics {
    pub total: i64,
    pub total_members: i64,
}

#[derive(Debug, Serialize, PartialEq, Eq)]
pub struct FriendshipStatistics {
    pub total: i64,
    pub pending: i64,
    pub accepted: i64,
    pub declined: i64,
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn statistics_serialize_with_the_java_field_names() {
        let stats = AdminStatisticsDto {
            users: Total { total: 1 },
            appointments: AppointmentStatistics {
                total: 4,
                planned: 1,
                cancelled: 1,
                deleted: 1,
                not_enough_attendees: 1,
            },
            participations: ParticipationStatistics {
                total: 3,
                pending: 1,
                approved: 1,
                rejected: 1,
            },
            groups: GroupStatistics {
                total: 2,
                total_members: 5,
            },
            friendships: FriendshipStatistics {
                total: 3,
                pending: 1,
                accepted: 1,
                declined: 1,
            },
            messages: Total { total: 7 },
            push_subscriptions: Total { total: 8 },
        };
        let value = serde_json::to_value(stats).unwrap();
        assert_eq!(value["appointments"]["notEnoughAttendees"], json!(1));
        assert_eq!(value["groups"]["totalMembers"], json!(5));
        assert_eq!(value["pushSubscriptions"]["total"], json!(8));
        assert_eq!(value["messages"], json!({"total": 7}));
    }

    #[test]
    fn user_dto_keeps_null_fields() {
        let dto = AdminUserDto {
            id: Some("u1".into()),
            first_name: None,
            last_name: None,
            email: None,
            created_at: None,
        };
        assert_eq!(
            serde_json::to_value(dto).unwrap(),
            json!({"id": "u1", "firstName": null, "lastName": null, "email": null, "createdAt": null})
        );
    }

    #[test]
    fn request_bodies_tolerate_missing_and_unknown_fields() {
        let request: AdminChangeParticipationStatusRequest =
            serde_json::from_str(r#"{"userId":"u","unknown":1}"#).unwrap();
        assert_eq!(request.user_id.as_deref(), Some("u"));
        assert!(request.appointment_id.is_none());
        let request: AdminAddGroupParticipantRequest =
            serde_json::from_str(r#"{"group_id":3,"user_role":"GUEST"}"#).unwrap();
        assert_eq!(request.group_id, Some(3));
    }
}
