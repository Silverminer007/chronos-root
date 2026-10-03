// Business logic services for push_notifications
use crate::push_notifications::models::{NotificationPayload, PushSubscription};
use serde_json::json;
use std::sync::Arc;
use tracing::{error, info};

/// VAPID configuration for Web Push
#[derive(Clone, Debug)]
pub struct VapidConfig {
    pub public_key: String,
    pub private_key: String,
    pub subject: String,
}

impl VapidConfig {
    /// Create a new VAPID configuration
    pub fn new(public_key: String, private_key: String, subject: String) -> Self {
        Self {
            public_key,
            private_key,
            subject,
        }
    }

    /// Load VAPID configuration from environment variables
    pub fn from_env() -> Result<Self, String> {
        let public_key = std::env::var("VAPID_PUBLIC_KEY")
            .map_err(|_| "VAPID_PUBLIC_KEY environment variable not found".to_string())?;
        let private_key = std::env::var("VAPID_PRIVATE_KEY")
            .map_err(|_| "VAPID_PRIVATE_KEY environment variable not found".to_string())?;
        let subject = std::env::var("VAPID_SUBJECT")
            .map_err(|_| "VAPID_SUBJECT environment variable not found".to_string())?;

        Ok(Self::new(public_key, private_key, subject))
    }
}

/// PushNotificationService handles sending push notifications to users
pub struct PushNotificationService {
    #[allow(dead_code)]
    vapid_config: Arc<VapidConfig>,
}

impl PushNotificationService {
    /// Create a new PushNotificationService
    pub fn new(vapid_config: VapidConfig) -> Self {
        Self {
            vapid_config: Arc::new(vapid_config),
        }
    }

    /// Send a notification to a user via a subscription
    pub async fn send_notification(
        &self,
        subscription: &PushSubscription,
        payload: &NotificationPayload,
    ) -> Result<(), PushNotificationError> {
        // Prepare the notification payload
        let notification_json = json!({
            "title": payload.title.clone(),
            "body": payload.body.clone(),
            "data": payload.data.clone().unwrap_or_else(|| json!({}))
        });

        // Send the notification via HTTP POST to the subscription endpoint
        // This is a simplified implementation that sends the encrypted payload
        let client = reqwest::Client::new();

        let response = client
            .post(&subscription.endpoint)
            .header("content-encoding", "aes128gcm")
            .header("content-type", "application/octet-stream")
            .header("ttl", "43200") // 12 hours
            .header("urgency", "high")
            // In production, payload should be encrypted with the subscription keys
            // For now, send the plain JSON payload
            .body(notification_json.to_string())
            .send()
            .await
            .map_err(|e| {
                error!("Failed to send push notification: {}", e);
                PushNotificationError::SendError(e.to_string())
            })?;

        if !response.status().is_success() {
            error!("Push service returned error status: {}", response.status());
            return Err(PushNotificationError::SendError(format!(
                "Push service returned: {}",
                response.status()
            )));
        }

        info!(
            "Push notification sent to subscription: {}",
            subscription.endpoint
        );
        Ok(())
    }
}

/// Errors that can occur when sending push notifications
#[derive(Debug, Clone)]
pub enum PushNotificationError {
    /// User has no subscriptions
    NoSubscriptions,
    /// VAPID configuration error
    VapidError(String),
    /// Error sending the notification
    SendError(String),
    /// Database error
    DatabaseError(String),
    /// Invalid subscription data
    InvalidSubscription(String),
}

impl std::fmt::Display for PushNotificationError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            PushNotificationError::NoSubscriptions => write!(f, "User has no push subscriptions"),
            PushNotificationError::VapidError(e) => write!(f, "VAPID error: {}", e),
            PushNotificationError::SendError(e) => write!(f, "Failed to send notification: {}", e),
            PushNotificationError::DatabaseError(e) => write!(f, "Database error: {}", e),
            PushNotificationError::InvalidSubscription(e) => {
                write!(f, "Invalid subscription: {}", e)
            }
        }
    }
}

impl std::error::Error for PushNotificationError {}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_vapid_config_creation() {
        let config = VapidConfig::new(
            "public_key".to_string(),
            "private_key".to_string(),
            "mailto:test@example.com".to_string(),
        );

        assert_eq!(config.public_key, "public_key");
        assert_eq!(config.private_key, "private_key");
        assert_eq!(config.subject, "mailto:test@example.com");
    }

    #[test]
    fn test_push_notification_error_display() {
        let err = PushNotificationError::NoSubscriptions;
        assert_eq!(err.to_string(), "User has no push subscriptions");

        let err = PushNotificationError::SendError("Connection failed".to_string());
        assert!(err.to_string().contains("Failed to send notification"));
    }
}
