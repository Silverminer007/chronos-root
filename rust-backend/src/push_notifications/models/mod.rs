// Data models for push_notifications
use serde::{Deserialize, Serialize};

/// Represents a user's web push subscription (table `pushsubscription`)
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct PushSubscription {
    pub id: i64,
    /// OIDC subject of the subscriber
    pub user_id: String,
    pub endpoint: String,
    pub p256dh: String,
    pub auth: String,
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
            id: 1,
            user_id: "user-sub".to_string(),
            endpoint: "https://example.com/endpoint".to_string(),
            p256dh: "test_p256dh".to_string(),
            auth: "test_auth".to_string(),
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
