// Sending of push notifications (Java: `WebPushAdapter`)
use crate::push_notifications::models::PushSubscription;
use crate::push_notifications::repository::{
    PushNotificationLogEntry, PushNotificationLogRepository, PushSubscriptionRepository,
};
use async_trait::async_trait;
use sqlx::PgPool;
use std::sync::Arc;
use tracing::{debug, error, info, warn};
use web_push::{
    ContentEncoding, IsahcWebPushClient, SubscriptionInfo, VapidSignatureBuilder, WebPushClient,
    WebPushError, WebPushMessageBuilder,
};

/// `push_notification_log.error_message` is `VARCHAR(1000)`
const MAX_ERROR_MESSAGE_LENGTH: usize = 1000;
/// The push service reports an expired or unknown subscription with these statuses
const SUBSCRIPTION_GONE_STATUS_CODES: [u16; 2] = [404, 410];
/// Status of a message accepted by a push service
const ACCEPTED_STATUS_CODE: u16 = 201;

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

    /// Load the configuration from `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY` and `VAPID_SUBJECT`.
    /// `None` when one is missing or empty: Java then sends no push notifications at all.
    pub fn from_env() -> Option<Self> {
        Self::from_lookup(|name| std::env::var(name).ok())
    }

    fn from_lookup(get: impl Fn(&str) -> Option<String>) -> Option<Self> {
        let value = |name: &str| get(name).filter(|v| !v.trim().is_empty());
        Some(Self::new(
            value("VAPID_PUBLIC_KEY")?,
            value("VAPID_PRIVATE_KEY")?,
            value("VAPID_SUBJECT")?,
        ))
    }
}

/// Delivers one payload to one subscription. Abstracted so tests use a fake push service.
#[async_trait]
pub trait PushSender: Send + Sync {
    /// `Ok(status)`: HTTP status the push service answered with (also for non-2xx answers).
    /// `Err(message)`: no answer (encryption, signing or network failure), Java's exception path.
    async fn send(&self, subscription: &PushSubscription, payload: &str) -> Result<u16, String>;
}

/// Sends encrypted (RFC 8291) and VAPID-signed (RFC 8292) messages to the push services
pub struct WebPushSender {
    config: VapidConfig,
    client: IsahcWebPushClient,
}

impl WebPushSender {
    pub fn new(config: VapidConfig) -> Result<Self, PushNotificationError> {
        let client = IsahcWebPushClient::new()
            .map_err(|e| PushNotificationError::SendError(e.to_string()))?;
        // Fail at startup, not at the first send, when the private key is unusable
        VapidSignatureBuilder::from_base64_no_sub(&config.private_key)
            .map_err(|e| PushNotificationError::VapidError(e.to_string()))?;
        Ok(Self { config, client })
    }
}

#[async_trait]
impl PushSender for WebPushSender {
    async fn send(&self, subscription: &PushSubscription, payload: &str) -> Result<u16, String> {
        let info = SubscriptionInfo::new(
            &subscription.endpoint,
            &subscription.p256dh,
            &subscription.auth,
        );
        let mut signature = VapidSignatureBuilder::from_base64(&self.config.private_key, &info)
            .map_err(|e| e.to_string())?;
        signature.add_claim("sub", self.config.subject.as_str());
        let signature = signature.build().map_err(|e| e.to_string())?;

        let mut message = WebPushMessageBuilder::new(&info);
        message.set_payload(ContentEncoding::Aes128Gcm, payload.as_bytes());
        message.set_vapid_signature(signature);
        let message = message.build().map_err(|e| e.to_string())?;

        match self.client.send(message).await {
            Ok(()) => Ok(ACCEPTED_STATUS_CODE),
            Err(e) => http_status_of(&e).ok_or_else(|| e.to_string()),
        }
    }
}

/// HTTP status behind a push service error, `None` for failures without an answer
fn http_status_of(error: &WebPushError) -> Option<u16> {
    match error {
        WebPushError::BadRequest(_) => Some(400),
        WebPushError::Unauthorized(_) => Some(401),
        WebPushError::EndpointNotFound(_) => Some(404),
        WebPushError::EndpointNotValid(_) => Some(410),
        WebPushError::PayloadTooLarge => Some(413),
        WebPushError::ServerError { .. } => Some(500),
        WebPushError::NotImplemented(_) => Some(501),
        _ => None,
    }
}

/// Sends a payload to every subscription of a user, logs each attempt and drops dead
/// subscriptions (Java: `WebPushAdapter#send`)
pub struct NotificationDispatcher {
    sender: Option<Arc<dyn PushSender>>,
    subscriptions: PushSubscriptionRepository,
    log: PushNotificationLogRepository,
}

impl NotificationDispatcher {
    /// Without a sender (VAPID keys not configured) notifications are omitted
    pub fn new(pool: PgPool, sender: Option<Arc<dyn PushSender>>) -> Self {
        Self {
            sender,
            subscriptions: PushSubscriptionRepository::new(pool.clone()),
            log: PushNotificationLogRepository::new(pool),
        }
    }

    pub async fn send(&self, user_id: &str, payload: &str) {
        let Some(sender) = &self.sender else {
            warn!("Omitting push notification because VAPID keys are not set");
            return;
        };
        debug!("[Notifications] Sending notification to user [{user_id}]: {payload}");
        let notification_type = notification_type(payload);
        let subscriptions = match self.subscriptions.find_by_user_id(user_id).await {
            Ok(subscriptions) => subscriptions,
            Err(e) => {
                error!("[Notifications] Failed to load subscriptions of {user_id}: {e}");
                return;
            }
        };

        for subscription in subscriptions {
            let endpoint = subscription.endpoint.as_str();
            let mut entry = PushNotificationLogEntry {
                user_id,
                notification_type: notification_type.as_deref(),
                payload,
                endpoint,
                http_status_code: None,
                success: false,
                error_message: None,
            };
            let failure;
            let remove = match sender.send(&subscription, payload).await {
                Ok(status) => {
                    entry.http_status_code = Some(i32::from(status));
                    entry.success = (200..300).contains(&status);
                    let gone = SUBSCRIPTION_GONE_STATUS_CODES.contains(&status);
                    if gone {
                        info!("[Notifications] Subscription expired (HTTP {status}), removing endpoint {endpoint}");
                    } else if !entry.success {
                        warn!("[Notifications] Push service returned HTTP {status} for endpoint {endpoint}");
                    }
                    gone
                }
                Err(message) => {
                    error!("[Notifications] Failed to send notification to endpoint {endpoint}: {message}");
                    failure = truncate(&message, MAX_ERROR_MESSAGE_LENGTH);
                    entry.error_message = Some(&failure);
                    true
                }
            };

            if let Err(e) = self.log.log(&entry).await {
                error!("[Notifications] Failed to write the notification log: {e}");
            }
            if remove {
                if let Err(e) = self.subscriptions.delete_by_endpoint(endpoint).await {
                    error!("[Notifications] Failed to remove endpoint {endpoint}: {e}");
                }
            }
        }
    }
}

/// The `type` field of a JSON payload (`null` for messages without one)
fn notification_type(payload: &str) -> Option<String> {
    serde_json::from_str::<serde_json::Value>(payload)
        .ok()?
        .get("type")?
        .as_str()
        .map(str::to_string)
}

fn truncate(message: &str, max_chars: usize) -> String {
    message.chars().take(max_chars).collect()
}

/// Errors that can occur when setting up or sending push notifications
#[derive(Debug, Clone)]
pub enum PushNotificationError {
    /// VAPID configuration error
    VapidError(String),
    /// Error sending the notification
    SendError(String),
}

impl std::fmt::Display for PushNotificationError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            PushNotificationError::VapidError(e) => write!(f, "VAPID error: {}", e),
            PushNotificationError::SendError(e) => write!(f, "Failed to send notification: {}", e),
        }
    }
}

impl std::error::Error for PushNotificationError {}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashMap;

    fn lookup(values: &[(&str, &str)]) -> impl Fn(&str) -> Option<String> {
        let map: HashMap<String, String> = values
            .iter()
            .map(|(k, v)| (k.to_string(), v.to_string()))
            .collect();
        move |name| map.get(name).cloned()
    }

    #[test]
    fn test_vapid_config_from_complete_environment() {
        let config = VapidConfig::from_lookup(lookup(&[
            ("VAPID_PUBLIC_KEY", "pub"),
            ("VAPID_PRIVATE_KEY", "priv"),
            ("VAPID_SUBJECT", "mailto:test@example.com"),
        ]))
        .expect("complete configuration");

        assert_eq!(config.public_key, "pub");
        assert_eq!(config.private_key, "priv");
        assert_eq!(config.subject, "mailto:test@example.com");
    }

    #[test]
    fn test_vapid_config_is_none_when_a_value_is_missing_or_blank() {
        assert!(VapidConfig::from_lookup(lookup(&[
            ("VAPID_PUBLIC_KEY", "pub"),
            ("VAPID_PRIVATE_KEY", "priv"),
        ]))
        .is_none());
        assert!(VapidConfig::from_lookup(lookup(&[
            ("VAPID_PUBLIC_KEY", "pub"),
            ("VAPID_PRIVATE_KEY", " "),
            ("VAPID_SUBJECT", "mailto:test@example.com"),
        ]))
        .is_none());
    }

    #[test]
    fn test_notification_type_is_read_from_the_payload() {
        assert_eq!(
            notification_type(r#"{"type":"APPOINTMENT_MOVED","x":1}"#).as_deref(),
            Some("APPOINTMENT_MOVED")
        );
        assert_eq!(notification_type(r#"{"title":"t","body":"b"}"#), None);
        assert_eq!(notification_type("not json"), None);
        assert_eq!(notification_type(r#"{"type":5}"#), None);
    }

    #[test]
    fn test_truncate_counts_characters() {
        assert_eq!(truncate("äöü", 2), "äö");
        assert_eq!(truncate("abc", 10), "abc");
    }

    #[test]
    fn test_http_status_of_push_errors() {
        assert_eq!(http_status_of(&WebPushError::PayloadTooLarge), Some(413));
        assert_eq!(http_status_of(&WebPushError::InvalidCryptoKeys), None);
    }

    #[test]
    fn test_push_notification_error_display() {
        let err = PushNotificationError::SendError("Connection failed".to_string());
        assert!(err.to_string().contains("Failed to send notification"));
    }
}
