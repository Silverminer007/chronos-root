pub mod postgres;

use async_trait::async_trait;
use chrono::Utc;
use serde_json::Value;
use uuid::Uuid;

/// Represents a publishable event in the system
#[derive(Debug, Clone)]
pub struct Event {
    pub id: String,
    pub event_type: String,
    pub payload: Value,
    pub timestamp: i64,
}

impl Event {
    pub fn new(event_type: impl Into<String>, payload: Value) -> Self {
        Self {
            id: Uuid::new_v4().to_string(),
            event_type: event_type.into(),
            payload,
            timestamp: Utc::now().timestamp(),
        }
    }
}

/// Abstract event bus trait for async side-effects
/// Implementations can be swapped (PostgreSQL, Kafka, RabbitMQ, etc.)
#[async_trait]
pub trait EventBus: Send + Sync {
    /// Fire an event - publish it for subscribers to handle
    async fn fire(&self, event: Event) -> Result<(), Box<dyn std::error::Error + Send + Sync>>;

    /// Subscribe to events of a specific type
    async fn subscribe<F>(
        &self,
        event_type: String,
        callback: F,
    ) -> Result<(), Box<dyn std::error::Error + Send + Sync>>
    where
        F: Fn(Event) + Send + Sync + 'static;
}
