// Data models for appointments
use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use sqlx::FromRow;
use uuid::Uuid;

/// Participation status for an appointment
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "UPPERCASE")]
pub enum ParticipationStatus {
    Pending,
    Approved,
    Rejected,
}

/// Status of an appointment
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum AppointmentStatus {
    Planned,
    Cancelled,
    Deleted,
    NotEnoughAttendees,
}

/// Role of a user in an appointment
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "UPPERCASE")]
pub enum UserRole {
    None,
    Guest,
    Attendant,
    Helper,
    Responsible,
}

/// Appointment entity - the core domain model for scheduling
/// Maps directly to the appointments table in the database
#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct Appointment {
    pub id: Uuid,
    pub title: String,
    pub description: Option<String>,
    pub start_time: DateTime<Utc>,
    pub end_time: DateTime<Utc>,
    pub location: Option<String>,
    pub creator_id: Uuid,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
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

/// User profile cached from Keycloak
#[derive(Debug, Clone, Serialize, Deserialize)]
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

/// Message in an appointment chat
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Message {
    pub id: i64,
    pub body: String,
    pub appointment_id: i64,
    pub sender_oidc_id: String,
    pub timestamp: DateTime<Utc>,
}

// ===== DTOs for API Requests/Responses =====

/// Request to create a new appointment
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateAppointmentRequest {
    pub title: String,
    pub description: Option<String>,
    pub location: Option<String>,
    pub start_time: DateTime<Utc>,
    pub end_time: DateTime<Utc>,
    pub minimal_attendees: Option<i32>,
}

/// Request to update an appointment
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UpdateAppointmentRequest {
    pub title: Option<String>,
    pub description: Option<String>,
    pub location: Option<String>,
    pub start_time: Option<DateTime<Utc>>,
    pub end_time: Option<DateTime<Utc>>,
}

/// Request to move/reschedule an appointment
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MoveAppointmentRequest {
    pub start_time: DateTime<Utc>,
    pub end_time: DateTime<Utc>,
}

/// Response with appointment details
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppointmentResponse {
    pub id: Uuid,
    pub title: String,
    pub description: Option<String>,
    pub start_time: DateTime<Utc>,
    pub end_time: DateTime<Utc>,
    pub location: Option<String>,
    pub creator_id: Uuid,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
}

impl From<Appointment> for AppointmentResponse {
    fn from(appointment: Appointment) -> Self {
        AppointmentResponse {
            id: appointment.id,
            title: appointment.title,
            description: appointment.description,
            start_time: appointment.start_time,
            end_time: appointment.end_time,
            location: appointment.location,
            creator_id: appointment.creator_id,
            created_at: appointment.created_at,
            updated_at: appointment.updated_at,
        }
    }
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

/// Request to send a message
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateMessageRequest {
    pub body: String,
}

/// Response with message details
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MessageResponse {
    pub id: i64,
    pub body: String,
    pub appointment_id: i64,
    pub sender_oidc_id: String,
    pub timestamp: DateTime<Utc>,
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

    #[test]
    fn test_appointment_serialization() {
        let now = Utc::now();
        let creator_id = Uuid::new_v4();
        let appt_id = Uuid::new_v4();
        let appointment = Appointment {
            id: appt_id,
            title: "Team Meeting".to_string(),
            description: Some("Quarterly planning".to_string()),
            location: Some("Conference Room A".to_string()),
            start_time: now,
            end_time: now + chrono::Duration::hours(1),
            creator_id,
            created_at: now,
            updated_at: now,
        };

        let json = serde_json::to_string(&appointment).unwrap();
        assert!(json.contains("\"title\":\"Team Meeting\""));
        assert!(json.contains("\"location\":\"Conference Room A\""));
        assert!(json.contains("\"description\":\"Quarterly planning\""));
    }

    #[test]
    fn test_appointment_response_from_appointment() {
        let now = Utc::now();
        let creator_id = Uuid::new_v4();
        let appt_id = Uuid::new_v4();
        let appointment = Appointment {
            id: appt_id,
            title: "Team Meeting".to_string(),
            description: Some("Quarterly planning".to_string()),
            location: Some("Conference Room A".to_string()),
            start_time: now,
            end_time: now + chrono::Duration::hours(1),
            creator_id,
            created_at: now,
            updated_at: now,
        };

        let response: AppointmentResponse = appointment.into();
        assert_eq!(response.id, appt_id);
        assert_eq!(response.title, "Team Meeting");
        assert_eq!(response.creator_id, creator_id);
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

    #[test]
    fn test_message_serialization() {
        let now = Utc::now();
        let message = Message {
            id: 1,
            body: "Hello everyone!".to_string(),
            appointment_id: 42,
            sender_oidc_id: "user123".to_string(),
            timestamp: now,
        };

        let json = serde_json::to_string(&message).unwrap();
        assert!(json.contains("\"body\":\"Hello everyone!\""));
        assert!(json.contains("\"appointment_id\":42"));
        assert!(json.contains("\"sender_oidc_id\":\"user123\""));
    }

    // ===== DTO Tests =====

    #[test]
    fn test_create_appointment_request_deserialization() {
        let json = r#"{
            "title": "New Meeting",
            "description": "Planning session",
            "location": "Room B",
            "start_time": "2025-09-15T14:00:00Z",
            "end_time": "2025-09-15T15:00:00Z",
            "minimal_attendees": 3
        }"#;

        let request = serde_json::from_str::<CreateAppointmentRequest>(json).unwrap();
        assert_eq!(request.title, "New Meeting");
        assert_eq!(request.description, Some("Planning session".to_string()));
        assert_eq!(request.location, Some("Room B".to_string()));
        assert_eq!(request.minimal_attendees, Some(3));
    }

    #[test]
    fn test_appointment_response_serialization() {
        let now = Utc::now();
        let creator_id = Uuid::new_v4();
        let appt_id = Uuid::new_v4();
        let response = AppointmentResponse {
            id: appt_id,
            title: "Team Meeting".to_string(),
            description: Some("Quarterly planning".to_string()),
            location: Some("Conference Room A".to_string()),
            start_time: now,
            end_time: now + chrono::Duration::hours(1),
            creator_id,
            created_at: now,
            updated_at: now,
        };

        let json = serde_json::to_string(&response).unwrap();
        assert!(json.contains("\"title\":\"Team Meeting\""));
        assert!(json.contains("\"location\":\"Conference Room A\""));
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
        assert_eq!(request.body, "Hello everyone!");
    }

    #[test]
    fn test_message_response_serialization() {
        let now = Utc::now();
        let response = MessageResponse {
            id: 1,
            body: "Hello everyone!".to_string(),
            appointment_id: 42,
            sender_oidc_id: "user123".to_string(),
            timestamp: now,
        };

        let json = serde_json::to_string(&response).unwrap();
        assert!(json.contains("\"body\":\"Hello everyone!\""));
        assert!(json.contains("\"sender_oidc_id\":\"user123\""));
    }
}
