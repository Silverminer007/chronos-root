// Data models for appointments
use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use sqlx::FromRow;
use std::str::FromStr;

/// Participation status for an appointment
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, sqlx::Type)]
#[serde(rename_all = "UPPERCASE")]
#[sqlx(type_name = "TEXT")]
#[sqlx(rename_all = "UPPERCASE")]
pub enum ParticipationStatus {
    Pending,
    Approved,
    Rejected,
}

impl std::fmt::Display for ParticipationStatus {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            ParticipationStatus::Pending => write!(f, "PENDING"),
            ParticipationStatus::Approved => write!(f, "APPROVED"),
            ParticipationStatus::Rejected => write!(f, "REJECTED"),
        }
    }
}

/// Status of an appointment
///
/// Stored as `SMALLINT` holding the ordinal of the Java enum (`appointment.status`, ADR-2026-10-12-014),
/// serialized as the enum name.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum AppointmentStatus {
    Planned,
    Cancelled,
    Deleted,
    NotEnoughAttendees,
}

impl AppointmentStatus {
    /// Ordinal as stored in `appointment.status` (same order as the Java enum)
    pub fn ordinal(self) -> i16 {
        match self {
            AppointmentStatus::Planned => 0,
            AppointmentStatus::Cancelled => 1,
            AppointmentStatus::Deleted => 2,
            AppointmentStatus::NotEnoughAttendees => 3,
        }
    }

    /// Inverse of [`AppointmentStatus::ordinal`]
    pub fn from_ordinal(ordinal: i16) -> Option<Self> {
        match ordinal {
            0 => Some(AppointmentStatus::Planned),
            1 => Some(AppointmentStatus::Cancelled),
            2 => Some(AppointmentStatus::Deleted),
            3 => Some(AppointmentStatus::NotEnoughAttendees),
            _ => None,
        }
    }
}

impl sqlx::Type<sqlx::Postgres> for AppointmentStatus {
    fn type_info() -> sqlx::postgres::PgTypeInfo {
        <i16 as sqlx::Type<sqlx::Postgres>>::type_info()
    }
}

impl<'q> sqlx::Encode<'q, sqlx::Postgres> for AppointmentStatus {
    fn encode_by_ref(
        &self,
        buf: &mut sqlx::postgres::PgArgumentBuffer,
    ) -> Result<sqlx::encode::IsNull, sqlx::error::BoxDynError> {
        <i16 as sqlx::Encode<sqlx::Postgres>>::encode_by_ref(&self.ordinal(), buf)
    }
}

impl<'r> sqlx::Decode<'r, sqlx::Postgres> for AppointmentStatus {
    fn decode(value: sqlx::postgres::PgValueRef<'r>) -> Result<Self, sqlx::error::BoxDynError> {
        let ordinal = <i16 as sqlx::Decode<sqlx::Postgres>>::decode(value)?;
        AppointmentStatus::from_ordinal(ordinal)
            .ok_or_else(|| format!("Unknown appointment status ordinal: {}", ordinal).into())
    }
}

impl FromStr for AppointmentStatus {
    type Err = String;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        match s {
            "PLANNED" => Ok(AppointmentStatus::Planned),
            "CANCELLED" => Ok(AppointmentStatus::Cancelled),
            "DELETED" => Ok(AppointmentStatus::Deleted),
            "NOT_ENOUGH_ATTENDEES" => Ok(AppointmentStatus::NotEnoughAttendees),
            _ => Err(format!("Unknown appointment status: {}", s)),
        }
    }
}

impl std::fmt::Display for AppointmentStatus {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            AppointmentStatus::Planned => write!(f, "PLANNED"),
            AppointmentStatus::Cancelled => write!(f, "CANCELLED"),
            AppointmentStatus::Deleted => write!(f, "DELETED"),
            AppointmentStatus::NotEnoughAttendees => write!(f, "NOT_ENOUGH_ATTENDEES"),
        }
    }
}

/// Role of a user in an appointment
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, sqlx::Type)]
#[serde(rename_all = "UPPERCASE")]
#[sqlx(type_name = "VARCHAR")]
#[sqlx(rename_all = "UPPERCASE")]
pub enum UserRole {
    None,
    Guest,
    Attendant,
    Helper,
    Responsible,
}

impl FromStr for UserRole {
    type Err = String;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        match s {
            "NONE" => Ok(UserRole::None),
            "GUEST" => Ok(UserRole::Guest),
            "ATTENDANT" => Ok(UserRole::Attendant),
            "HELPER" => Ok(UserRole::Helper),
            "RESPONSIBLE" => Ok(UserRole::Responsible),
            _ => Err(format!("Unknown user role: {}", s)),
        }
    }
}

impl std::fmt::Display for UserRole {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            UserRole::None => write!(f, "NONE"),
            UserRole::Guest => write!(f, "GUEST"),
            UserRole::Attendant => write!(f, "ATTENDANT"),
            UserRole::Helper => write!(f, "HELPER"),
            UserRole::Responsible => write!(f, "RESPONSIBLE"),
        }
    }
}

/// Appointment entity - the core domain model for scheduling
/// Maps to the `appointment` table (see `APPOINTMENT_COLUMNS` in the repository).
/// There is no creator column: the creator is the participant with role RESPONSIBLE.
#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct Appointment {
    pub id: i64,
    pub name: String,
    pub description: Option<String>,
    pub start_time: DateTime<Utc>,
    pub end_time: DateTime<Utc>,
    pub venue: Option<String>,
    pub status: AppointmentStatus,
    pub minimal_attendees: Option<i32>,
    pub created_at: Option<DateTime<Utc>>,
    pub last_update: Option<DateTime<Utc>>,
}

/// Participation of a user in an appointment
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppointmentParticipation {
    pub id: i64,
    pub appointment_id: i64,
    pub user_oidc_id: String,
    pub role: UserRole,
    pub status: ParticipationStatus,
    pub group_participation_id: Option<i64>,
}

/// User profile cached from Keycloak (table `user_profiles`; there is no users table)
#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct UserProfile {
    pub oidc_id: String,
    pub first_name: Option<String>,
    pub last_name: Option<String>,
    pub email: Option<String>,
    pub profile_picture_url: Option<String>,
    pub updated_at: DateTime<Utc>,
}

/// Group for organizing users
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Group {
    pub id: i64,
    pub owner_oidc_id: String,
    pub group_name: String,
}

/// Group member relationship
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GroupMember {
    pub id: i64,
    pub group_id: i64,
    pub user_oidc_id: String,
}

// ===== DTOs for API Requests/Responses =====

/// Request to create a new appointment
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateAppointmentRequest {
    pub name: String,
    pub description: Option<String>,
    pub venue: Option<String>,
    pub start: String,
    pub end: String,
    pub minimal_attendees: Option<i32>,
}

/// Request to update an appointment
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UpdateAppointmentRequest {
    pub name: Option<String>,
    pub description: Option<String>,
    pub venue: Option<String>,
    pub start: Option<String>,
    pub end: Option<String>,
    pub minimal_attendees: Option<i32>,
}

/// Response with appointment details (Java-compatible format)
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppointmentResponse {
    pub id: i64,
    pub name: String,
    pub description: Option<String>,
    pub start: String,
    pub end: String,
    pub venue: Option<String>,
    pub status: AppointmentStatus,
    pub minimal_attendees: Option<i32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub participants: Option<Vec<UserParticipantDto>>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub messages: Option<Vec<MessageDto>>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub group_participants: Option<Vec<GroupDto>>,
}

impl From<Appointment> for AppointmentResponse {
    fn from(appointment: Appointment) -> Self {
        AppointmentResponse {
            id: appointment.id,
            name: appointment.name,
            description: appointment.description,
            start: appointment.start_time.to_rfc3339(),
            end: appointment.end_time.to_rfc3339(),
            venue: appointment.venue,
            status: appointment.status,
            minimal_attendees: appointment.minimal_attendees,
            participants: None,
            messages: None,
            group_participants: None,
        }
    }
}

/// User participant in an appointment response
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UserParticipantDto {
    pub user_id: String,
    pub name: Option<String>,
    pub profile_picture_url: Option<String>,
    pub role: UserRole,
    pub status: ParticipationStatus,
    pub via_group_id: Option<i64>,
    pub via_group_name: Option<String>,
}

/// Group with members
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GroupDto {
    pub id: i64,
    pub name: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub members: Option<Vec<UserDto>>,
}

/// User DTO for group members
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UserDto {
    pub id: String,
    pub first_name: Option<String>,
    pub last_name: Option<String>,
}

/// Request to participate in an appointment
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateParticipationRequest {
    pub role: UserRole,
}

/// Response with participation details
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ParticipationResponse {
    pub id: i64,
    pub appointment_id: i64,
    pub user_oidc_id: String,
    pub role: UserRole,
    pub status: ParticipationStatus,
}

/// Request to update participation status
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UpdateParticipationStatusRequest {
    pub status: ParticipationStatus,
}

/// Response for GET /api/v2/appointments/:id/participation/status
/// (matches Java AppointmentDto with only `status` set)
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ParticipationStatusResponse {
    pub status: ParticipationStatus,
}

/// A row of the Java-compatible `message` table
#[derive(Debug, Clone, FromRow)]
pub struct Message {
    pub id: i64,
    pub sender_oidcid: String,
    pub appointment_id: Option<i64>,
    pub body: Option<String>,
    pub timestamp: Option<DateTime<Utc>>,
}

/// Response element of GET /api/v2/appointments/:id/messages (Java `MessageDto`).
/// Null fields are serialized as `null`, like Java does.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MessageDto {
    pub id: i64,
    pub sender_id: String,
    pub sender_name: String,
    pub appointment_id: Option<i64>,
    pub body: Option<String>,
    /// ISO-8601 UTC like Java `Instant.toString()`
    pub timestamp: Option<String>,
}

/// Request to create a group
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateGroupRequest {
    pub group_name: String,
}

/// Response with group details
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GroupResponse {
    pub id: i64,
    pub owner_oidc_id: String,
    pub group_name: String,
}

/// Request body of POST /api/v2/appointments/:id/messages (Java: `MessageDto`, only `body` is read).
/// A missing body is stored as NULL and other fields are ignored, as in Java.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateMessageRequest {
    #[serde(default)]
    pub body: Option<String>,
}

/// Request to add a participant to an appointment
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AddParticipantRequest {
    pub user_role: UserRole,
}

/// Request to change a participant's role (Java: `ParticipantRoleDto`)
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ChangeParticipantRoleRequest {
    pub role: String,
}

/// Request to add a group to an appointment
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AddGroupToAppointmentRequest {
    #[serde(alias = "user_role")]
    pub role: String,
}

#[cfg(test)]
mod tests {
    use super::*;

    // ===== Enum Tests =====

    #[test]
    fn test_participation_status_serialization() {
        assert_eq!(
            serde_json::to_string(&ParticipationStatus::Pending).unwrap(),
            "\"PENDING\""
        );
        assert_eq!(
            serde_json::to_string(&ParticipationStatus::Approved).unwrap(),
            "\"APPROVED\""
        );
        assert_eq!(
            serde_json::to_string(&ParticipationStatus::Rejected).unwrap(),
            "\"REJECTED\""
        );
    }

    #[test]
    fn test_participation_status_deserialization() {
        assert_eq!(
            serde_json::from_str::<ParticipationStatus>("\"PENDING\"").unwrap(),
            ParticipationStatus::Pending
        );
        assert_eq!(
            serde_json::from_str::<ParticipationStatus>("\"APPROVED\"").unwrap(),
            ParticipationStatus::Approved
        );
        assert_eq!(
            serde_json::from_str::<ParticipationStatus>("\"REJECTED\"").unwrap(),
            ParticipationStatus::Rejected
        );
    }

    #[test]
    fn test_appointment_status_serialization() {
        assert_eq!(
            serde_json::to_string(&AppointmentStatus::Planned).unwrap(),
            "\"PLANNED\""
        );
        assert_eq!(
            serde_json::to_string(&AppointmentStatus::Cancelled).unwrap(),
            "\"CANCELLED\""
        );
        assert_eq!(
            serde_json::to_string(&AppointmentStatus::Deleted).unwrap(),
            "\"DELETED\""
        );
        assert_eq!(
            serde_json::to_string(&AppointmentStatus::NotEnoughAttendees).unwrap(),
            "\"NOT_ENOUGH_ATTENDEES\""
        );
    }

    #[test]
    fn test_appointment_status_deserialization() {
        assert_eq!(
            serde_json::from_str::<AppointmentStatus>("\"PLANNED\"").unwrap(),
            AppointmentStatus::Planned
        );
        assert_eq!(
            serde_json::from_str::<AppointmentStatus>("\"CANCELLED\"").unwrap(),
            AppointmentStatus::Cancelled
        );
        assert_eq!(
            serde_json::from_str::<AppointmentStatus>("\"DELETED\"").unwrap(),
            AppointmentStatus::Deleted
        );
        assert_eq!(
            serde_json::from_str::<AppointmentStatus>("\"NOT_ENOUGH_ATTENDEES\"").unwrap(),
            AppointmentStatus::NotEnoughAttendees
        );
    }

    #[test]
    fn test_user_role_serialization() {
        assert_eq!(serde_json::to_string(&UserRole::None).unwrap(), "\"NONE\"");
        assert_eq!(
            serde_json::to_string(&UserRole::Guest).unwrap(),
            "\"GUEST\""
        );
        assert_eq!(
            serde_json::to_string(&UserRole::Attendant).unwrap(),
            "\"ATTENDANT\""
        );
        assert_eq!(
            serde_json::to_string(&UserRole::Helper).unwrap(),
            "\"HELPER\""
        );
        assert_eq!(
            serde_json::to_string(&UserRole::Responsible).unwrap(),
            "\"RESPONSIBLE\""
        );
    }

    #[test]
    fn test_user_role_deserialization() {
        assert_eq!(
            serde_json::from_str::<UserRole>("\"NONE\"").unwrap(),
            UserRole::None
        );
        assert_eq!(
            serde_json::from_str::<UserRole>("\"GUEST\"").unwrap(),
            UserRole::Guest
        );
        assert_eq!(
            serde_json::from_str::<UserRole>("\"ATTENDANT\"").unwrap(),
            UserRole::Attendant
        );
        assert_eq!(
            serde_json::from_str::<UserRole>("\"HELPER\"").unwrap(),
            UserRole::Helper
        );
        assert_eq!(
            serde_json::from_str::<UserRole>("\"RESPONSIBLE\"").unwrap(),
            UserRole::Responsible
        );
    }

    // ===== Entity Struct Tests =====

    fn sample_appointment(id: i64) -> Appointment {
        let now = Utc::now();
        Appointment {
            id,
            name: "Team Meeting".to_string(),
            description: Some("Quarterly planning".to_string()),
            venue: Some("Conference Room A".to_string()),
            start_time: now,
            end_time: now + chrono::Duration::hours(1),
            status: AppointmentStatus::Planned,
            minimal_attendees: Some(5),
            created_at: Some(now),
            last_update: Some(now),
        }
    }

    #[test]
    fn test_appointment_serialization() {
        let json = serde_json::to_string(&sample_appointment(12)).unwrap();
        assert!(json.contains("\"id\":12"));
        assert!(json.contains("\"name\":\"Team Meeting\""));
        assert!(json.contains("\"venue\":\"Conference Room A\""));
        assert!(json.contains("\"description\":\"Quarterly planning\""));
        assert!(json.contains("\"status\":\"PLANNED\""));
    }

    #[test]
    fn test_appointment_response_from_appointment() {
        let response: AppointmentResponse = sample_appointment(12).into();
        assert_eq!(response.id, 12);
        assert_eq!(response.name, "Team Meeting");
        assert_eq!(response.venue.as_deref(), Some("Conference Room A"));
        assert_eq!(response.status, AppointmentStatus::Planned);
    }

    #[test]
    fn test_appointment_response_id_is_a_json_number() {
        let response: AppointmentResponse = sample_appointment(12).into();
        let json = serde_json::to_value(&response).unwrap();
        assert_eq!(json["id"], serde_json::json!(12));
    }

    #[test]
    fn test_appointment_status_ordinals_match_java_enum() {
        // Java: PLANNED, CANCELLED, DELETED, NOT_ENOUGH_ATTENDEES (stored by ordinal)
        assert_eq!(AppointmentStatus::Planned.ordinal(), 0);
        assert_eq!(AppointmentStatus::Cancelled.ordinal(), 1);
        assert_eq!(AppointmentStatus::Deleted.ordinal(), 2);
        assert_eq!(AppointmentStatus::NotEnoughAttendees.ordinal(), 3);
        for status in [
            AppointmentStatus::Planned,
            AppointmentStatus::Cancelled,
            AppointmentStatus::Deleted,
            AppointmentStatus::NotEnoughAttendees,
        ] {
            assert_eq!(
                AppointmentStatus::from_ordinal(status.ordinal()),
                Some(status)
            );
        }
        assert_eq!(AppointmentStatus::from_ordinal(4), None);
    }

    #[test]
    fn test_appointment_participation_serialization() {
        let participation = AppointmentParticipation {
            id: 1,
            appointment_id: 42,
            user_oidc_id: "user123".to_string(),
            role: UserRole::Attendant,
            status: ParticipationStatus::Approved,
            group_participation_id: None,
        };

        let json = serde_json::to_string(&participation).unwrap();
        assert!(json.contains("\"appointment_id\":42"));
        assert!(json.contains("\"user_oidc_id\":\"user123\""));
        assert!(json.contains("\"role\":\"ATTENDANT\""));
        assert!(json.contains("\"status\":\"APPROVED\""));
    }

    #[test]
    fn test_appointment_participation_deserialization() {
        let json = r#"{
            "id": 5,
            "appointment_id": 42,
            "user_oidc_id": "user123",
            "role": "HELPER",
            "status": "PENDING",
            "group_participation_id": 10
        }"#;

        let participation = serde_json::from_str::<AppointmentParticipation>(json).unwrap();
        assert_eq!(participation.id, 5);
        assert_eq!(participation.appointment_id, 42);
        assert_eq!(participation.role, UserRole::Helper);
        assert_eq!(participation.status, ParticipationStatus::Pending);
        assert_eq!(participation.group_participation_id, Some(10));
    }

    #[test]
    fn test_user_profile_serialization() {
        let now = Utc::now();
        let profile = UserProfile {
            oidc_id: "user123".to_string(),
            first_name: Some("John".to_string()),
            last_name: Some("Doe".to_string()),
            email: Some("john.doe@example.com".to_string()),
            profile_picture_url: Some("https://example.com/pic.jpg".to_string()),
            updated_at: now,
        };

        let json = serde_json::to_string(&profile).unwrap();
        assert!(json.contains("\"oidc_id\":\"user123\""));
        assert!(json.contains("\"first_name\":\"John\""));
        assert!(json.contains("\"email\":\"john.doe@example.com\""));
    }

    #[test]
    fn test_group_serialization() {
        let group = Group {
            id: 1,
            owner_oidc_id: "owner123".to_string(),
            group_name: "Youth Group".to_string(),
        };

        let json = serde_json::to_string(&group).unwrap();
        assert!(json.contains("\"owner_oidc_id\":\"owner123\""));
        assert!(json.contains("\"group_name\":\"Youth Group\""));
    }

    #[test]
    fn test_group_member_serialization() {
        let member = GroupMember {
            id: 1,
            group_id: 5,
            user_oidc_id: "member123".to_string(),
        };

        let json = serde_json::to_string(&member).unwrap();
        assert!(json.contains("\"group_id\":5"));
        assert!(json.contains("\"user_oidc_id\":\"member123\""));
    }

    // ===== DTO Tests =====

    #[test]
    fn test_create_appointment_request_deserialization() {
        let json = r#"{
            "name": "New Meeting",
            "description": "Planning session",
            "venue": "Room B",
            "start": "2025-09-15T14:00:00Z",
            "end": "2025-09-15T15:00:00Z",
            "minimal_attendees": 3
        }"#;

        let request = serde_json::from_str::<CreateAppointmentRequest>(json).unwrap();
        assert_eq!(request.name, "New Meeting");
        assert_eq!(request.description, Some("Planning session".to_string()));
        assert_eq!(request.start, "2025-09-15T14:00:00Z");
        assert_eq!(request.end, "2025-09-15T15:00:00Z");
        assert_eq!(request.minimal_attendees, Some(3));
    }

    #[test]
    fn test_appointment_response_serialization() {
        let response = AppointmentResponse {
            id: 12,
            name: "Team Meeting".to_string(),
            description: Some("Quarterly planning".to_string()),
            venue: Some("Conference Room A".to_string()),
            start: "2025-09-15T14:00:00Z".to_string(),
            end: "2025-09-15T15:00:00Z".to_string(),
            status: AppointmentStatus::Planned,
            minimal_attendees: Some(5),
            participants: None,
            messages: None,
            group_participants: None,
        };

        let json = serde_json::to_string(&response).unwrap();
        assert!(json.contains("\"name\":\"Team Meeting\""));
        assert!(json.contains("\"venue\":\"Conference Room A\""));
        assert!(json.contains("\"status\":\"PLANNED\""));
    }

    #[test]
    fn test_update_appointment_request_deserialization() {
        let json = r#"{
            "name": "Updated Meeting",
            "start": "2025-09-16T14:00:00Z",
            "end": "2025-09-16T15:00:00Z"
        }"#;

        let request = serde_json::from_str::<UpdateAppointmentRequest>(json).unwrap();
        assert_eq!(request.name, Some("Updated Meeting".to_string()));
        assert_eq!(request.start, Some("2025-09-16T14:00:00Z".to_string()));
        assert_eq!(request.end, Some("2025-09-16T15:00:00Z".to_string()));
        assert_eq!(request.description, None);
    }

    #[test]
    fn test_participation_response_serialization() {
        let response = ParticipationResponse {
            id: 1,
            appointment_id: 42,
            user_oidc_id: "user123".to_string(),
            role: UserRole::Attendant,
            status: ParticipationStatus::Approved,
        };

        let json = serde_json::to_string(&response).unwrap();
        assert!(json.contains("\"role\":\"ATTENDANT\""));
        assert!(json.contains("\"status\":\"APPROVED\""));
    }

    #[test]
    fn test_create_participation_request_deserialization() {
        let json = r#"{
            "role": "HELPER"
        }"#;

        let request = serde_json::from_str::<CreateParticipationRequest>(json).unwrap();
        assert_eq!(request.role, UserRole::Helper);
    }

    #[test]
    fn test_change_participant_role_request_deserialization() {
        let request =
            serde_json::from_str::<ChangeParticipantRoleRequest>(r#"{"role": "HELPER"}"#).unwrap();
        assert_eq!(request.role, "HELPER");
    }

    #[test]
    fn test_update_participation_status_request_deserialization() {
        let json = r#"{
            "status": "APPROVED"
        }"#;

        let request = serde_json::from_str::<UpdateParticipationStatusRequest>(json).unwrap();
        assert_eq!(request.status, ParticipationStatus::Approved);
    }

    #[test]
    fn test_create_group_request_deserialization() {
        let json = r#"{
            "group_name": "Scout Group"
        }"#;

        let request = serde_json::from_str::<CreateGroupRequest>(json).unwrap();
        assert_eq!(request.group_name, "Scout Group");
    }

    #[test]
    fn test_group_response_serialization() {
        let response = GroupResponse {
            id: 1,
            owner_oidc_id: "owner123".to_string(),
            group_name: "Youth Group".to_string(),
        };

        let json = serde_json::to_string(&response).unwrap();
        assert!(json.contains("\"group_name\":\"Youth Group\""));
    }

    #[test]
    fn test_create_message_request_deserialization() {
        let json = r#"{
            "body": "Hello everyone!"
        }"#;

        let request = serde_json::from_str::<CreateMessageRequest>(json).unwrap();
        assert_eq!(request.body.as_deref(), Some("Hello everyone!"));
    }

    #[test]
    fn test_create_message_request_ignores_other_dto_fields_and_allows_missing_body() {
        let request =
            serde_json::from_str::<CreateMessageRequest>(r#"{"id": 5, "sender_id": "x"}"#).unwrap();
        assert_eq!(request.body, None);
    }

    #[test]
    fn test_message_dto_serializes_java_field_names() {
        let dto = MessageDto {
            id: 1,
            sender_id: "user123".to_string(),
            sender_name: "Max Muster".to_string(),
            appointment_id: Some(42),
            body: Some("Hello everyone!".to_string()),
            timestamp: None,
        };

        let json = serde_json::to_value(&dto).unwrap();
        assert_eq!(
            json,
            serde_json::json!({
                "id": 1,
                "sender_id": "user123",
                "sender_name": "Max Muster",
                "appointment_id": 42,
                "body": "Hello everyone!",
                "timestamp": null
            })
        );
    }
}
