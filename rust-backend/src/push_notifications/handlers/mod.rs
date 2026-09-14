// Handler functions for push_notifications
pub mod event_listener;

use crate::database::repository::Repository;
use crate::event_bus::Event;
use crate::push_notifications::models::NotificationPayload;
use crate::push_notifications::repository::PushSubscriptionRepository;
use crate::push_notifications::PushNotificationService;
use serde_json::Value;
use sqlx::PgPool;
use std::sync::Arc;
use tracing::{error, info, warn};
use uuid::Uuid;

pub use event_listener::{PushNotificationEventListener, event_types};

/// Event handler for appointment-related events
pub struct AppointmentEventHandler {
    notification_service: Arc<PushNotificationService>,
    subscription_repo: Arc<PushSubscriptionRepository>,
}

impl AppointmentEventHandler {
    pub fn new(
        notification_service: Arc<PushNotificationService>,
        subscription_repo: Arc<PushSubscriptionRepository>,
    ) -> Self {
        Self {
            notification_service,
            subscription_repo,
        }
    }

    /// Handle appointment created event
    pub async fn handle_appointment_created(&self, event: &Event) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        info!("Handling appointment created event: {}", event.id);

        // Extract appointment data from event payload
        let appointment_id = event.payload
            .get("appointment_id")
            .and_then(|v| v.as_str())
            .ok_or("Missing appointment_id in event payload")?;

        let title = event.payload
            .get("title")
            .and_then(|v| v.as_str())
            .unwrap_or("Neuer Termin");

        let participant_ids: Vec<Uuid> = event.payload
            .get("participant_ids")
            .and_then(|v| v.as_array())
            .map(|arr| arr.iter().filter_map(|id| {
                id.as_str().and_then(|s| Uuid::parse_str(s).ok())
            }).collect())
            .unwrap_or_default();

        // Send notifications to participants
        let payload = NotificationPayload {
            title: format!("Termin: {}", title),
            body: "Sie wurden zu einem Termin eingeladen".to_string(),
            data: Some(serde_json::json!({
                "appointment_id": appointment_id,
                "type": "appointment_created"
            })),
        };

        for user_id in participant_ids {
            self.send_notification_to_user(&user_id, &payload).await;
        }

        Ok(())
    }

    /// Handle participation status changed event
    pub async fn handle_participation_status_changed(&self, event: &Event) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        info!("Handling participation status changed event: {}", event.id);

        let user_id = event.payload
            .get("user_id")
            .and_then(|v| v.as_str())
            .ok_or("Missing user_id in event payload")?;

        let status = event.payload
            .get("status")
            .and_then(|v| v.as_str())
            .unwrap_or("unknown");

        let user_uuid = Uuid::parse_str(user_id)?;
        let payload = NotificationPayload {
            title: "Teilnahmestatus geändert".to_string(),
            body: format!("Ihr Status wurde auf {} geändert", status),
            data: Some(serde_json::json!({
                "type": "participation_status_changed",
                "status": status
            })),
        };

        self.send_notification_to_user(&user_uuid, &payload).await;
        Ok(())
    }

    /// Handle appointment updated event
    pub async fn handle_appointment_updated(&self, event: &Event) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        info!("Handling appointment updated event: {}", event.id);

        let appointment_id = event.payload
            .get("appointment_id")
            .and_then(|v| v.as_str())
            .ok_or("Missing appointment_id")?;

        let title = event.payload
            .get("title")
            .and_then(|v| v.as_str())
            .unwrap_or("Termin");

        let participant_ids: Vec<Uuid> = event.payload
            .get("participant_ids")
            .and_then(|v| v.as_array())
            .map(|arr| arr.iter().filter_map(|id| {
                id.as_str().and_then(|s| Uuid::parse_str(s).ok())
            }).collect())
            .unwrap_or_default();

        let payload = NotificationPayload {
            title: format!("Termin aktualisiert: {}", title),
            body: "Ein Termin wurde aktualisiert".to_string(),
            data: Some(serde_json::json!({
                "appointment_id": appointment_id,
                "type": "appointment_updated"
            })),
        };

        for user_id in participant_ids {
            self.send_notification_to_user(&user_id, &payload).await;
        }

        Ok(())
    }

    /// Handle friendship request sent event
    pub async fn handle_friendship_request_sent(&self, event: &Event) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        info!("Handling friendship request sent event: {}", event.id);

        let recipient_id = event.payload
            .get("recipient_id")
            .and_then(|v| v.as_str())
            .ok_or("Missing recipient_id")?;

        let sender_name = event.payload
            .get("sender_name")
            .and_then(|v| v.as_str())
            .unwrap_or("Jemand");

        let recipient_uuid = Uuid::parse_str(recipient_id)?;
        let payload = NotificationPayload {
            title: "Freundschaftsanfrage".to_string(),
            body: format!("{} möchte dein Freund sein", sender_name),
            data: Some(serde_json::json!({
                "type": "friendship_request_sent"
            })),
        };

        self.send_notification_to_user(&recipient_uuid, &payload).await;
        Ok(())
    }

    /// Handle reminder event
    pub async fn handle_reminder(&self, event: &Event) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        info!("Handling reminder event: {}", event.id);

        let user_id = event.payload
            .get("user_id")
            .and_then(|v| v.as_str())
            .ok_or("Missing user_id")?;

        let appointment_title = event.payload
            .get("appointment_title")
            .and_then(|v| v.as_str())
            .unwrap_or("Termin");

        let user_uuid = Uuid::parse_str(user_id)?;
        let payload = NotificationPayload {
            title: "Erinnerung".to_string(),
            body: format!("Erinnerung für: {}", appointment_title),
            data: Some(serde_json::json!({
                "type": "reminder"
            })),
        };

        self.send_notification_to_user(&user_uuid, &payload).await;
        Ok(())
    }

    /// Send a notification to a user
    async fn send_notification_to_user(
        &self,
        user_id: &Uuid,
        payload: &NotificationPayload,
    ) {
        match self.subscription_repo.find_by_user_id(*user_id).await {
            Ok(subscriptions) => {
                if subscriptions.is_empty() {
                    warn!("User {} has no push subscriptions", user_id);
                    return;
                }

                for subscription in subscriptions {
                    if let Err(e) = self.notification_service.send_notification(&subscription, payload).await {
                        error!("Failed to send notification to user {}: {}", user_id, e);
                    }
                }
            }
            Err(e) => {
                error!("Failed to fetch subscriptions for user {}: {}", user_id, e);
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_notification_payload_creation() {
        let payload = NotificationPayload {
            title: "Test Termin".to_string(),
            body: "Test Beschreibung".to_string(),
            data: Some(serde_json::json!({"test": "data"})),
        };

        assert_eq!(payload.title, "Test Termin");
        assert_eq!(payload.body, "Test Beschreibung");
    }
}
