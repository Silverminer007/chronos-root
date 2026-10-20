// Registers the push event handler on the event bus
use crate::event_bus::{Event, EventSubscriber};
use crate::push_notifications::events;
use crate::push_notifications::handlers::PushEventHandler;
use std::sync::Arc;
use tracing::{error, info};

/// Subscribes the [`PushEventHandler`] to every event Java sends a push notification for
pub struct PushNotificationEventListener {
    handler: Arc<PushEventHandler>,
}

impl PushNotificationEventListener {
    pub fn new(handler: Arc<PushEventHandler>) -> Self {
        Self { handler }
    }

    /// Subscribe to all push notification events
    pub async fn subscribe_all<T: EventSubscriber + ?Sized>(
        &self,
        event_bus: &T,
    ) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        for event_type in events::ALL {
            let handler = self.handler.clone();
            event_bus
                .subscribe(event_type.to_string(), move |event: Event| {
                    let handler = handler.clone();
                    tokio::spawn(async move {
                        if let Err(e) = handler.handle(&event).await {
                            error!("Error handling {}: {}", event.event_type, e);
                        }
                    });
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
    fn test_event_types_are_unique() {
        let unique: std::collections::HashSet<_> = events::ALL.iter().collect();
        assert_eq!(unique.len(), events::ALL.len());
    }
}
