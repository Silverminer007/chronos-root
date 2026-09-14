// Event listener for push notifications
use crate::event_bus::{Event, EventBus};
use crate::push_notifications::handlers::AppointmentEventHandler;
use crate::push_notifications::PushNotificationService;
use crate::push_notifications::repository::PushSubscriptionRepository;
use std::sync::Arc;
use tracing::{error, info};

/// Event types that trigger push notifications
pub mod event_types {
    pub const APPOINTMENT_CREATED: &str = "appointment_created";
    pub const APPOINTMENT_UPDATED: &str = "appointment_updated";
    pub const APPOINTMENT_DELETED: &str = "appointment_deleted";
    pub const PARTICIPATION_ADDED: &str = "participation_added";
    pub const PARTICIPATION_REMOVED: &str = "participation_removed";
    pub const PARTICIPATION_STATUS_CHANGED: &str = "participation_status_changed";
    pub const REMINDER_SCHEDULED: &str = "reminder_scheduled";
    pub const REMINDER_SENT: &str = "reminder_sent";
    pub const FRIENDSHIP_REQUEST_SENT: &str = "friendship_request_sent";
    pub const FRIENDSHIP_REQUEST_ACCEPTED: &str = "friendship_request_accepted";
    pub const FRIENDSHIP_REMOVED: &str = "friendship_removed";
    pub const GROUP_CREATED: &str = "group_created";
    pub const GROUP_UPDATED: &str = "group_updated";
    pub const GROUP_MEMBER_ADDED: &str = "group_member_added";
    pub const GROUP_MEMBER_REMOVED: &str = "group_member_removed";
    pub const MESSAGE_RECEIVED: &str = "message_received";
}

/// Push notification event listener
pub struct PushNotificationEventListener {
    event_bus: Arc<dyn EventBus>,
    notification_service: Arc<PushNotificationService>,
    subscription_repo: Arc<PushSubscriptionRepository>,
    appointment_handler: Arc<AppointmentEventHandler>,
}

impl PushNotificationEventListener {
    /// Create a new push notification event listener
    pub fn new(
        event_bus: Arc<dyn EventBus>,
        notification_service: Arc<PushNotificationService>,
        subscription_repo: Arc<PushSubscriptionRepository>,
    ) -> Self {
        let appointment_handler = Arc::new(AppointmentEventHandler::new(
            notification_service.clone(),
            subscription_repo.clone(),
        ));

        Self {
            event_bus,
            notification_service,
            subscription_repo,
            appointment_handler,
        }
    }

    /// Subscribe to all push notification events
    pub async fn subscribe_all(&self) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        info!("Subscribing to push notification events");

        // Subscribe to appointment events
        let handler = self.appointment_handler.clone();
        let event_types = vec![
            event_types::APPOINTMENT_CREATED,
            event_types::APPOINTMENT_UPDATED,
            event_types::APPOINTMENT_DELETED,
            event_types::PARTICIPATION_ADDED,
            event_types::PARTICIPATION_REMOVED,
            event_types::PARTICIPATION_STATUS_CHANGED,
            event_types::REMINDER_SCHEDULED,
            event_types::REMINDER_SENT,
            event_types::FRIENDSHIP_REQUEST_SENT,
            event_types::FRIENDSHIP_REQUEST_ACCEPTED,
            event_types::FRIENDSHIP_REMOVED,
            event_types::GROUP_CREATED,
            event_types::GROUP_UPDATED,
            event_types::GROUP_MEMBER_ADDED,
            event_types::GROUP_MEMBER_REMOVED,
            event_types::MESSAGE_RECEIVED,
        ];

        for event_type in event_types {
            let event_type_clone = event_type.to_string();
            let handler_clone = handler.clone();

            self.event_bus
                .subscribe(event_type_clone.clone(), move |event: Event| {
                    let handler = handler_clone.clone();
                    let event_type = event_type_clone.clone();

                    // Handle events based on type
                    match event_type.as_str() {
                        event_types::APPOINTMENT_CREATED => {
                            let handler = handler.clone();
                            let event = event.clone();
                            tokio::spawn(async move {
                                if let Err(e) = handler.handle_appointment_created(&event).await {
                                    error!("Error handling appointment created event: {}", e);
                                }
                            });
                        }
                        event_types::PARTICIPATION_STATUS_CHANGED => {
                            let handler = handler.clone();
                            let event = event.clone();
                            tokio::spawn(async move {
                                if let Err(e) = handler.handle_participation_status_changed(&event).await {
                                    error!("Error handling participation status changed event: {}", e);
                                }
                            });
                        }
                        _ => {
                            info!("Received event of type: {}", event_type);
                        }
                    }
                })
                .await?;

            info!("Subscribed to event type: {}", event_type);
        }

        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_event_types_defined() {
        assert_eq!(event_types::APPOINTMENT_CREATED, "appointment_created");
        assert_eq!(event_types::APPOINTMENT_UPDATED, "appointment_updated");
        assert_eq!(event_types::PARTICIPATION_STATUS_CHANGED, "participation_status_changed");
    }

    #[test]
    fn test_event_types_are_unique() {
        let event_types_vec = vec![
            event_types::APPOINTMENT_CREATED,
            event_types::APPOINTMENT_UPDATED,
            event_types::APPOINTMENT_DELETED,
            event_types::PARTICIPATION_ADDED,
            event_types::PARTICIPATION_REMOVED,
            event_types::PARTICIPATION_STATUS_CHANGED,
            event_types::REMINDER_SCHEDULED,
            event_types::REMINDER_SENT,
            event_types::FRIENDSHIP_REQUEST_SENT,
            event_types::FRIENDSHIP_REQUEST_ACCEPTED,
            event_types::FRIENDSHIP_REMOVED,
            event_types::GROUP_CREATED,
            event_types::GROUP_UPDATED,
            event_types::GROUP_MEMBER_ADDED,
            event_types::GROUP_MEMBER_REMOVED,
            event_types::MESSAGE_RECEIVED,
        ];

        assert_eq!(event_types_vec.len(), 16, "Should have 16 event types for push notifications");

        // Verify all are unique
        let mut unique = std::collections::HashSet::new();
        for event_type in event_types_vec {
            assert!(unique.insert(event_type), "Event type {} is not unique", event_type);
        }
    }
}
