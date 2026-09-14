use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use sqlx::FromRow;
use uuid::Uuid;

/// Group entity for database persistence
#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
#[sqlx(type_name = "groups")]
pub struct Group {
    pub id: Uuid,
    pub name: String,
    pub description: Option<String>,
    pub owner_id: Uuid,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_group_creation() {
        let now = Utc::now();
        let group = Group {
            id: Uuid::new_v4(),
            name: "Test Team".to_string(),
            description: Some("A test team group".to_string()),
            owner_id: Uuid::new_v4(),
            created_at: now,
            updated_at: now,
        };

        assert_eq!(group.name, "Test Team");
        assert!(group.description.is_some());
    }

    #[test]
    fn test_group_without_description() {
        let now = Utc::now();
        let group = Group {
            id: Uuid::new_v4(),
            name: "Another Group".to_string(),
            description: None,
            owner_id: Uuid::new_v4(),
            created_at: now,
            updated_at: now,
        };

        assert!(group.description.is_none());
    }
}
