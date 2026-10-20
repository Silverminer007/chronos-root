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

/// Request model for subscribing to push notifications (Java: `PushSubscriptionDto`). Every
/// field is optional so that a missing one is Java's 500 instead of a deserialisation error.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PushSubscriptionRequest {
    #[serde(default)]
    pub endpoint: Option<String>,
    #[serde(default)]
    pub keys: Option<PushSubscriptionKeys>,
}

/// Push subscription keys (p256dh and auth)
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PushSubscriptionKeys {
    #[serde(default)]
    pub p256dh: Option<String>,
    #[serde(default)]
    pub auth: Option<String>,
}

/// Response of `GET /api/v2/push/status` (Java: `PushSubscriptionStatusDto`)
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PushSubscriptionStatus {
    pub exists: bool,
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
    fn test_subscription_request_tolerates_missing_fields() {
        let request: PushSubscriptionRequest = serde_json::from_str("{}").unwrap();
        assert!(request.endpoint.is_none() && request.keys.is_none());

        let request: PushSubscriptionRequest =
            serde_json::from_str(r#"{"endpoint":"https://e","keys":{"p256dh":"p","auth":"a"}}"#)
                .unwrap();
        assert_eq!(request.keys.unwrap().auth.as_deref(), Some("a"));
    }

    #[test]
    fn test_status_serialization() {
        let json = serde_json::to_string(&PushSubscriptionStatus { exists: true }).unwrap();
        assert_eq!(json, r#"{"exists":true}"#);
    }
}
