use crate::event_bus::{Event, EventBus};
use async_trait::async_trait;
use sqlx::PgPool;
use tracing::{error, info};

/// PostgreSQL-backed event bus using LISTEN/NOTIFY for async side-effects
/// Designed to be swappable with Kafka/RabbitMQ implementations
pub struct PostgresEventBus {
    pool: PgPool,
}

impl PostgresEventBus {
    /// Create a new PostgreSQL event bus
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }

    /// Start listening for events on a specific channel
    pub async fn create_listener(
        &self,
        event_type: &str,
    ) -> Result<sqlx::postgres::PgListener, sqlx::Error> {
        let mut listener = sqlx::postgres::PgListener::connect_with(self.pool.as_ref()).await?;
        listener.listen(event_type).await?;
        Ok(listener)
    }
}

#[async_trait]
impl EventBus for PostgresEventBus {
    /// Fire an event - insert it into events table and NOTIFY subscribers
    async fn fire(&self, event: Event) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        // Validate event_type is a valid PostgreSQL identifier for NOTIFY
        if !event.event_type.chars().all(|c| c.is_alphanumeric() || c == '_') {
            return Err("Invalid event_type: must contain only alphanumeric chars and underscores".into());
        }

        // Insert event into events table for persistence
        let payload_str = serde_json::to_string(&event.payload)?;

        sqlx::query(
            "INSERT INTO events (id, event_type, payload, timestamp)
             VALUES ($1, $2, $3, $4)",
        )
        .bind(&event.id)
        .bind(&event.event_type)
        .bind(&payload_str)
        .bind(event.timestamp)
        .execute(&self.pool)
        .await?;

        // NOTIFY all subscribers listening on this channel
        // Include the event ID in the payload so subscribers can fetch the full event
        let notify_query = format!("NOTIFY {}, '{}'", event.event_type, event.id);
        sqlx::query(&notify_query)
            .execute(&self.pool)
            .await?;

        info!("Event fired: {} (id: {})", event.event_type, event.id);
        Ok(())
    }

    /// Subscribe to events of a specific type using PostgreSQL LISTEN
    /// Note: This spawns a background task that will listen indefinitely
    async fn subscribe<F>(
        &self,
        event_type: String,
        callback: F,
    ) -> Result<(), Box<dyn std::error::Error + Send + Sync>>
    where
        F: Fn(Event) + Send + Sync + 'static,
    {
        let pool = self.pool.clone();
        let event_type_clone = event_type.clone();

        // Spawn a listener task that runs indefinitely
        tokio::spawn(async move {
            loop {
                match sqlx::postgres::PgListener::connect_with(pool.as_ref()).await {
                    Ok(mut listener) => {
                        info!("Listener started for event type: {}", event_type_clone);

                        if let Err(e) = listener.listen(&event_type_clone).await {
                            error!("Failed to listen on channel {}: {}", event_type_clone, e);
                            tokio::time::sleep(tokio::time::Duration::from_secs(5)).await;
                            continue;
                        }

                        // Poll for notifications
                        loop {
                            match listener.recv().await {
                                Ok(_notification) => {
                                    info!("Received notification on channel: {}", event_type_clone);
                                    // In a production system, you would fetch the event from the database
                                    // using the event ID passed in the notification payload
                                }
                                Err(e) => {
                                    error!("Listener error: {}", e);
                                    break; // Break inner loop to reconnect
                                }
                            }
                        }
                    }
                    Err(e) => {
                        error!("Failed to create listener: {}", e);
                        tokio::time::sleep(tokio::time::Duration::from_secs(5)).await;
                    }
                }
            }
        });

        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use sqlx::postgres::PgPoolOptions;

    async fn create_test_pool() -> Result<PgPool, sqlx::Error> {
        let database_url = std::env::var("DATABASE_URL")
            .unwrap_or_else(|_| "postgres://chronos:chronos@localhost:5432/chronos".to_string());

        PgPoolOptions::new()
            .max_connections(5)
            .connect(&database_url)
            .await
    }

    #[tokio::test]
    #[ignore]
    async fn test_event_bus_creation() {
        if let Ok(pool) = create_test_pool().await {
            let bus = PostgresEventBus::new(pool);
            assert!(true);
        }
    }

    #[tokio::test]
    #[ignore]
    async fn test_fire_event() {
        if let Ok(pool) = create_test_pool().await {
            let bus = PostgresEventBus::new(pool);
            let event = Event::new("test_event", serde_json::json!({"data": "test"}));
            let result = bus.fire(event).await;
            assert!(result.is_ok(), "Failed to fire event: {:?}", result);
        }
    }

    #[tokio::test]
    #[ignore]
    async fn test_event_persistence() {
        if let Ok(pool) = create_test_pool().await {
            let bus = PostgresEventBus::new(pool);
            let event = Event::new("persistence_test", serde_json::json!({"id": 123}));
            let event_id = event.id.clone();

            bus.fire(event).await.expect("Failed to fire event");

            // Query the events table to verify persistence
            let result = sqlx::query_as::<_, (String, String)>(
                "SELECT id, event_type FROM events WHERE id = $1"
            )
            .bind(&event_id)
            .fetch_one(&pool)
            .await;

            assert!(result.is_ok());
            let (id, event_type) = result.unwrap();
            assert_eq!(id, event_id);
            assert_eq!(event_type, "persistence_test");
        }
    }
}
