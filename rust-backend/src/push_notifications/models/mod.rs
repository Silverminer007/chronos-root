// Data models for push_notifications
use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

/// Represents a user's web push subscription
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct PushSubscription {
    pub id: Uuid,
    pub user_id: Uuid,
    pub endpoint: String,
    pub p256dh: String,
    pub auth: String,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
}

/// Request model for subscribing to push notifications
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PushSubscriptionRequest {
    pub endpoint: String,
    pub keys: PushSubscriptionKeys,
}

/// Push subscription keys (p256dh and auth)
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PushSubscriptionKeys {
    pub p256dh: String,
    pub auth: String,
}

/// Notification payload to be sent to the client
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct NotificationPayload {
    pub title: String,
    pub body: String,
    #[serde(default)]
    pub data: Option<serde_json::Value>,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_push_subscription_creation() {
        let subscription = PushSubscription {
            id: Uuid::new_v4(),
            user_id: Uuid::new_v4(),
            endpoint: "https://example.com/endpoint".to_string(),
            p256dh: "test_p256dh".to_string(),
            auth: "test_auth".to_string(),
            created_at: Utc::now(),
            updated_at: Utc::now(),
        };

        assert!(!subscription.endpoint.is_empty());
        assert!(!subscription.p256dh.is_empty());
        assert!(!subscription.auth.is_empty());
    }

    #[test]
    fn test_notification_payload_serialization() {
        let payload = NotificationPayload {
            title: "Test Title".to_string(),
            body: "Test Body".to_string(),
            data: Some(serde_json::json!({ "key": "value" })),
        };

        let json = serde_json::to_string(&payload).expect("Failed to serialize");
        assert!(json.contains("Test Title"));
        assert!(json.contains("Test Body"));
    }
}
