use crate::event_bus::{Event, EventBus, EventPublisher, EventSubscriber};
use async_trait::async_trait;
use sqlx::postgres::PgListener;
use sqlx::PgPool;
use std::collections::HashMap;
use std::sync::{Arc, Mutex};
use std::time::Duration;
use tokio::sync::mpsc;
use tracing::{error, info};

type Callback = Arc<dyn Fn(Event) + Send + Sync>;

/// Delay before the dispatcher retries after losing or failing to open its connection
const RECONNECT_DELAY: Duration = Duration::from_secs(5);

/// Registers a callback for an event type at the dispatcher task
struct Subscription {
    event_type: String,
    callback: Callback,
}

/// PostgreSQL-backed event bus using LISTEN/NOTIFY for async side-effects
/// Designed to be swappable with Kafka/RabbitMQ implementations
///
/// All subscriptions share one `PgListener` (one LISTEN connection for every event type), so the
/// pool footprint of the bus stays constant however many event types exist.
pub struct PostgresEventBus {
    pool: PgPool,
    /// Channel to the dispatcher task; the task is spawned by the first `subscribe`
    dispatcher: Mutex<Option<mpsc::UnboundedSender<Subscription>>>,
}

impl PostgresEventBus {
    /// Create a new PostgreSQL event bus
    pub fn new(pool: PgPool) -> Self {
        Self {
            pool,
            dispatcher: Mutex::new(None),
        }
    }

    fn send_subscription(&self, subscription: Subscription) -> Result<(), String> {
        let mut dispatcher = self.dispatcher.lock().map_err(|e| e.to_string())?;
        let sender = dispatcher.get_or_insert_with(|| {
            let (sender, receiver) = mpsc::unbounded_channel();
            tokio::spawn(run_dispatcher(self.pool.clone(), receiver));
            sender
        });
        sender
            .send(subscription)
            .map_err(|_| "event dispatcher stopped".to_string())
    }
}

/// Owns the single LISTEN connection: listens on every subscribed channel and hands each
/// notification to the callbacks of its event type. Ends when the bus (the sender) is dropped.
async fn run_dispatcher(pool: PgPool, mut subscriptions: mpsc::UnboundedReceiver<Subscription>) {
    let mut callbacks: HashMap<String, Vec<Callback>> = HashMap::new();
    loop {
        let mut listener = match PgListener::connect_with(&pool).await {
            Ok(listener) => listener,
            Err(e) => {
                error!("Failed to create listener: {}", e);
                tokio::time::sleep(RECONNECT_DELAY).await;
                continue;
            }
        };
        if let Err(e) = listener
            .listen_all(callbacks.keys().map(String::as_str))
            .await
        {
            error!("Failed to listen on channels: {}", e);
            tokio::time::sleep(RECONNECT_DELAY).await;
            continue;
        }
        info!("Listener started for {} event types", callbacks.len());

        loop {
            tokio::select! {
                subscription = subscriptions.recv() => {
                    let Some(Subscription { event_type, callback }) = subscription else {
                        return;
                    };
                    let is_new_channel = !callbacks.contains_key(&event_type);
                    callbacks.entry(event_type.clone()).or_default().push(callback);
                    if is_new_channel {
                        if let Err(e) = listener.listen(&event_type).await {
                            error!("Failed to listen on channel {}: {}", event_type, e);
                            break; // Reconnect; the channel is re-listened with all others
                        }
                        info!("Listener started for event type: {}", event_type);
                    }
                }
                notification = listener.recv() => match notification {
                    Ok(notification) => {
                        info!("Received notification on channel: {}", notification.channel());
                        if let Some(handlers) = callbacks.get(notification.channel()) {
                            dispatch(&pool, notification.payload(), handlers).await;
                        }
                    }
                    Err(e) => {
                        error!("Listener error: {}", e);
                        break; // Reconnect
                    }
                },
            }
        }
        // Only reached after a listener error: back off so a persistent failure is no hot loop
        tokio::time::sleep(RECONNECT_DELAY).await;
    }
}

/// Loads the event announced by a notification and passes it to the callbacks
async fn dispatch(pool: &PgPool, event_id: &str, handlers: &[Callback]) {
    match sqlx::query_as::<_, (String, String, String)>(
        "SELECT id, event_type, payload FROM events WHERE id = $1",
    )
    .bind(event_id)
    .fetch_optional(pool)
    .await
    {
        Ok(Some((id, event_type, payload_str))) => {
            match serde_json::from_str::<serde_json::Value>(&payload_str) {
                Ok(payload) => {
                    let timestamp = chrono::Utc::now().timestamp();
                    for callback in handlers {
                        callback(Event {
                            id: id.clone(),
                            event_type: event_type.clone(),
                            payload: payload.clone(),
                            timestamp,
                        });
                    }
                }
                Err(e) => error!("Failed to parse event payload: {}", e),
            }
        }
        Ok(None) => error!("Event not found in database: {}", event_id),
        Err(e) => error!("Failed to fetch event from database: {}", e),
    }
}

#[async_trait]
impl EventPublisher for PostgresEventBus {
    /// Fire an event - insert it into events table and NOTIFY subscribers
    async fn fire(&self, event: Event) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        // Validate event_type is a valid PostgreSQL identifier for NOTIFY
        if !event
            .event_type
            .chars()
            .all(|c| c.is_alphanumeric() || c == '_')
        {
            return Err(
                "Invalid event_type: must contain only alphanumeric chars and underscores".into(),
            );
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

        // NOTIFY all subscribers listening on this channel. `pg_notify` keeps the channel name
        // case-sensitive, like the quoted `LISTEN` of `PgListener` (a plain `NOTIFY Name` folds it
        // to lower case, so CamelCase event types were never delivered).
        // Include the event ID in the payload so subscribers can fetch the full event
        sqlx::query("SELECT pg_notify($1, $2)")
            .bind(&event.event_type)
            .bind(&event.id)
            .execute(&self.pool)
            .await?;

        info!("Event fired: {} (id: {})", event.event_type, event.id);
        Ok(())
    }
}

#[async_trait]
impl EventSubscriber for PostgresEventBus {
    /// Subscribe to events of a specific type using PostgreSQL LISTEN
    /// The callback runs on the shared dispatcher task, which listens indefinitely
    async fn subscribe<F>(
        &self,
        event_type: String,
        callback: F,
    ) -> Result<(), Box<dyn std::error::Error + Send + Sync>>
    where
        F: Fn(Event) + Send + Sync + 'static,
    {
        self.send_subscription(Subscription {
            event_type,
            callback: Arc::new(callback),
        })?;
        Ok(())
    }
}

impl EventBus for PostgresEventBus {}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::test_utils::{TestDb, TestDbConfig};

    /// Each test gets its own migrated database, so the `events` table exists and tests cannot collide
    async fn create_test_pool() -> PgPool {
        TestDb::new()
            .await
            .expect("Failed to create test database")
            .pool()
            .clone()
    }

    async fn create_test_db() -> TestDb {
        TestDb::new().await.expect("Failed to create test database")
    }

    #[tokio::test]
    #[ignore]
    async fn test_event_bus_creation() {
        let pool = create_test_pool().await;
        let bus = PostgresEventBus::new(pool);
        // Event bus created successfully with valid pool
        assert!(!bus.pool.is_closed());
    }

    #[tokio::test]
    #[ignore]
    async fn test_fire_event() {
        let pool = create_test_pool().await;
        let bus = PostgresEventBus::new(pool);
        let event = Event::new("test_event", serde_json::json!({"data": "test"}));
        let result = bus.fire(event).await;
        assert!(result.is_ok(), "Failed to fire event: {:?}", result);
    }

    #[tokio::test]
    #[ignore]
    async fn test_event_persistence() {
        let pool = create_test_pool().await;
        let bus = PostgresEventBus::new(pool.clone());
        let event = Event::new("persistence_test", serde_json::json!({"id": 123}));
        let event_id = event.id.clone();

        bus.fire(event).await.expect("Failed to fire event");

        // Query the events table to verify persistence
        let result = sqlx::query_as::<_, (String, String)>(
            "SELECT id, event_type FROM events WHERE id = $1",
        )
        .bind(&event_id)
        .fetch_one(&pool)
        .await;

        assert!(result.is_ok());
        let (id, event_type) = result.unwrap();
        assert_eq!(id, event_id);
        assert_eq!(event_type, "persistence_test");
    }

    /// Regression #153: every subscription used to hold its own pooled connection, so as many
    /// event types as pool connections left nothing for queries.
    #[tokio::test]
    async fn test_subscriptions_leave_pool_connections_free() {
        const EVENT_TYPES: usize = 16;
        let db = TestDb::with_config(TestDbConfig {
            max_connections: EVENT_TYPES as u32,
            connection_timeout: Duration::from_secs(3),
        })
        .await
        .expect("Failed to create test database");
        let bus = PostgresEventBus::new(db.pool().clone());
        let (tx, mut rx) = tokio::sync::mpsc::unbounded_channel();

        for i in 0..EVENT_TYPES {
            let tx = tx.clone();
            bus.subscribe(format!("pool_test_{i}"), move |event| {
                let _ = tx.send(event.event_type);
            })
            .await
            .expect("subscribe failed");
        }
        // Delivery proves the subscription is active, so no fixed wait is needed
        fire_until_delivered(&bus, "pool_test_7", &mut rx).await;

        let one: i32 = sqlx::query_scalar("SELECT 1")
            .fetch_one(db.pool())
            .await
            .expect("no free pool connection left for a query");
        assert_eq!(one, 1);
    }

    /// Fires `event_type` until a subscriber reports it: a NOTIFY sent before the dispatcher has
    /// executed its LISTEN is lost, so the first attempts may legitimately go unnoticed.
    async fn fire_until_delivered(
        bus: &PostgresEventBus,
        event_type: &str,
        rx: &mut tokio::sync::mpsc::UnboundedReceiver<String>,
    ) -> String {
        tokio::time::timeout(Duration::from_secs(20), async {
            loop {
                bus.fire(Event::new(event_type, serde_json::json!({})))
                    .await
                    .expect("fire failed");
                if let Ok(Some(received)) =
                    tokio::time::timeout(Duration::from_millis(300), rx.recv()).await
                {
                    return received;
                }
            }
        })
        .await
        .expect("event not delivered")
    }

    #[tokio::test]
    async fn test_multiple_subscribers_per_event_type_all_receive() {
        let db = create_test_db().await;
        let bus = PostgresEventBus::new(db.pool().clone());
        let (tx, mut rx) = tokio::sync::mpsc::unbounded_channel();

        for name in ["first", "second"] {
            let tx = tx.clone();
            bus.subscribe("multi_sub_test".to_string(), move |_| {
                let _ = tx.send(name.to_string());
            })
            .await
            .expect("subscribe failed");
        }

        let first = fire_until_delivered(&bus, "multi_sub_test", &mut rx).await;
        // Both callbacks run for the same notification, so each name shows up at least once
        let mut seen = std::collections::HashSet::new();
        seen.insert(first);
        while seen.len() < 2 {
            let name = tokio::time::timeout(Duration::from_secs(5), rx.recv())
                .await
                .expect("second subscriber not called")
                .expect("channel closed");
            seen.insert(name);
        }
    }

    #[tokio::test]
    async fn test_listener_relistens_all_channels_after_reconnect() {
        let db = create_test_db().await;
        let bus = PostgresEventBus::new(db.pool().clone());
        let (tx, mut rx) = tokio::sync::mpsc::unbounded_channel();
        for event_type in ["reconnect_a", "reconnect_b"] {
            let tx = tx.clone();
            bus.subscribe(event_type.to_string(), move |event| {
                let _ = tx.send(event.event_type);
            })
            .await
            .expect("subscribe failed");
        }
        fire_until_delivered(&bus, "reconnect_a", &mut rx).await;
        fire_until_delivered(&bus, "reconnect_b", &mut rx).await;

        // Kill the LISTEN connection; the dispatcher must reconnect and listen again on both
        let killed: i64 = sqlx::query_scalar(
            "SELECT count(pg_terminate_backend(pid)) FROM pg_stat_activity
             WHERE datname = current_database() AND pid <> pg_backend_pid()
               AND query LIKE 'LISTEN%'",
        )
        .fetch_one(db.pool())
        .await
        .expect("terminate failed");
        assert_eq!(killed, 1, "expected exactly one LISTEN connection");

        // Drain events delivered while the old connection was still up
        while rx.try_recv().is_ok() {}
        fire_until_delivered(&bus, "reconnect_a", &mut rx).await;
        fire_until_delivered(&bus, "reconnect_b", &mut rx).await;
    }
}
