use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use sqlx::FromRow;
use uuid::Uuid;

/// Appointment entity for database persistence
#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
#[sqlx(type_name = "appointments")]
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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_appointment_creation() {
        let now = Utc::now();
        let appointment = Appointment {
            id: Uuid::new_v4(),
            title: "Test Meeting".to_string(),
            description: Some("A test meeting".to_string()),
            start_time: now,
            end_time: now + chrono::Duration::hours(1),
            location: Some("Office".to_string()),
            creator_id: Uuid::new_v4(),
            created_at: now,
            updated_at: now,
        };

        assert_eq!(appointment.title, "Test Meeting");
        assert!(appointment.description.is_some());
    }

    #[test]
    fn test_appointment_without_description() {
        let now = Utc::now();
        let appointment = Appointment {
            id: Uuid::new_v4(),
            title: "Meeting".to_string(),
            description: None,
            start_time: now,
            end_time: now + chrono::Duration::hours(2),
            location: None,
            creator_id: Uuid::new_v4(),
            created_at: now,
            updated_at: now,
        };

        assert!(appointment.description.is_none());
        assert!(appointment.location.is_none());
    }
}
