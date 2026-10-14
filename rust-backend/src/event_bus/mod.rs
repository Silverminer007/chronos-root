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

/// Reads `appointment_id` from an event payload. Producers send a JSON number;
/// a numeric string is accepted as well.
pub fn appointment_id_from(payload: &Value) -> Option<i64> {
    let value = payload.get("appointment_id")?;
    value.as_i64().or_else(|| value.as_str()?.parse().ok())
}

/// Dyn-compatible trait for publishing events
/// Used by services and handlers that only need to fire events
#[async_trait]
pub trait EventPublisher: Send + Sync {
    /// Fire an event - publish it for subscribers to handle
    async fn fire(&self, event: Event) -> Result<(), Box<dyn std::error::Error + Send + Sync>>;
}

/// Trait for subscribing to events with a generic callback
/// Not dyn-compatible; must be used with concrete types only
#[async_trait]
pub trait EventSubscriber: Send + Sync {
    /// Subscribe to events of a specific type
    async fn subscribe<F>(
        &self,
        event_type: String,
        callback: F,
    ) -> Result<(), Box<dyn std::error::Error + Send + Sync>>
    where
        F: Fn(Event) + Send + Sync + 'static;
}

/// Combined trait for convenience in code that has access to the concrete type
pub trait EventBus: EventPublisher + EventSubscriber {}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn test_appointment_id_from_accepts_numbers_and_numeric_strings() {
        assert_eq!(
            appointment_id_from(&json!({"appointment_id": 12})),
            Some(12)
        );
        assert_eq!(
            appointment_id_from(&json!({"appointment_id": "13"})),
            Some(13)
        );
    }

    #[test]
    fn test_appointment_id_from_rejects_missing_and_non_numeric() {
        assert_eq!(appointment_id_from(&json!({})), None);
        assert_eq!(appointment_id_from(&json!({"appointment_id": "abc"})), None);
    }
}
