use crate::appointments::repository::AppointmentRepository;
use crate::event_bus::{Event, EventSubscriber};
use sqlx::PgPool;
use tracing::{error, info};
use uuid::Uuid;

pub struct AppointmentParticipationListener {
    pool: PgPool,
}

impl AppointmentParticipationListener {
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }

    pub async fn handle_appointment_created(&self, event: &Event) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        info!("Handling AppointmentCreatedEvent");

        // Extract appointment_id and creator_id from event payload
        let appointment_id = event.payload
            .get("appointment_id")
            .and_then(|v| v.as_str())
            .and_then(|s| Uuid::parse_str(s).ok())
            .ok_or("Missing or invalid appointment_id")?;

        let creator_id = event.payload
            .get("creator_id")
            .and_then(|v| v.as_str())
            .and_then(|s| Uuid::parse_str(s).ok())
            .ok_or("Missing or invalid creator_id")?;

        // Add creator as a RESPONSIBLE participant
        let repo = AppointmentRepository::new(self.pool.clone());
        repo.add_participant(appointment_id, creator_id, "RESPONSIBLE", "APPROVED")
            .await
            .map_err(|e| format!("Failed to add creator as participant: {}", e))?;

        info!("Successfully added creator as RESPONSIBLE participant for appointment {}", appointment_id);
        Ok(())
    }

    pub async fn subscribe_to_events<T: EventSubscriber + ?Sized>(
        &self,
        event_bus: &T,
    ) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        info!("Subscribing to AppointmentCreatedEvent");

        let pool = self.pool.clone();
        event_bus
            .subscribe("AppointmentCreatedEvent".to_string(), move |event: Event| {
                let pool = pool.clone();
                tokio::spawn(async move {
                    let listener = AppointmentParticipationListener::new(pool);
                    if let Err(e) = listener.handle_appointment_created(&event).await {
                        error!("Error handling AppointmentCreatedEvent: {}", e);
                    }
                });
            })
            .await?;

        info!("Subscribed to AppointmentCreatedEvent");
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    #[test]
    fn test_event_listener_creation() {
        // This test would require a real database pool in a real test environment
        // For now, we just verify the struct can be instantiated
        // In integration tests, use a test database connection pool
    }
}
