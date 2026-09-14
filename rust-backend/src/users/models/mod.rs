use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use sqlx::FromRow;
use uuid::Uuid;

/// User entity for database persistence
#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
#[sqlx(type_name = "users")]
pub struct User {
    pub id: Uuid,
    pub keycloak_id: String,
    pub email: String,
    pub first_name: Option<String>,
    pub last_name: Option<String>,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_user_creation() {
        let now = Utc::now();
        let user = User {
            id: Uuid::new_v4(),
            keycloak_id: "keycloak_123".to_string(),
            email: "user@example.com".to_string(),
            first_name: Some("John".to_string()),
            last_name: Some("Doe".to_string()),
            created_at: now,
            updated_at: now,
        };

        assert_eq!(user.email, "user@example.com");
        assert!(user.first_name.is_some());
    }

    #[test]
    fn test_user_without_name() {
        let now = Utc::now();
        let user = User {
            id: Uuid::new_v4(),
            keycloak_id: "keycloak_456".to_string(),
            email: "user2@example.com".to_string(),
            first_name: None,
            last_name: None,
            created_at: now,
            updated_at: now,
        };

        assert!(user.first_name.is_none());
        assert!(user.last_name.is_none());
    }
}
