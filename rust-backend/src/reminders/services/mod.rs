//! Rule engine and event port (Java `ReminderRuleEngine`, `ReminderEventPort`, `ReminderEventCDIAdapter`).

use crate::appointments::models::Appointment;
use crate::event_bus::{Event, EventPublisher};
use crate::reminders::models::{pending_reminder_payload, PENDING_REMINDER_EVENT};
use crate::reminders::rules::{ReminderKind, ReminderRule};
use async_trait::async_trait;
use chrono::{DateTime, Duration, Utc};
use std::sync::Arc;
use tracing::error;

type BoxError = Box<dyn std::error::Error + Send + Sync>;

/// Maximum age of a trigger time that still counts as due (Java: `diff.toMinutes() <= 15`)
const DUE_WINDOW_MINUTES: i64 = 15;

#[async_trait]
pub trait ReminderEventPort: Send + Sync {
    async fn send_reminder(&self, appointment_id: i64) -> Result<(), BoxError>;
    async fn send_rsvp_reminder(&self, appointment_id: i64) -> Result<(), BoxError>;
}

/// Fires the reminders on the event bus, where the push listener picks them up
pub struct BusReminderEventPort {
    event_bus: Arc<dyn EventPublisher>,
}

impl BusReminderEventPort {
    pub fn new(event_bus: Arc<dyn EventPublisher>) -> Self {
        Self { event_bus }
    }
}

#[async_trait]
impl ReminderEventPort for BusReminderEventPort {
    /// Java's CDI adapter implements `sendReminder` as an empty method; copied as is.
    async fn send_reminder(&self, _appointment_id: i64) -> Result<(), BoxError> {
        Ok(())
    }

    async fn send_rsvp_reminder(&self, appointment_id: i64) -> Result<(), BoxError> {
        self.event_bus
            .fire(Event::new(
                PENDING_REMINDER_EVENT,
                pending_reminder_payload(appointment_id),
            ))
            .await
    }
}

pub struct ReminderRuleEngine {
    rules: Vec<Box<dyn ReminderRule>>,
    port: Arc<dyn ReminderEventPort>,
}

impl ReminderRuleEngine {
    pub fn new(rules: Vec<Box<dyn ReminderRule>>, port: Arc<dyn ReminderEventPort>) -> Self {
        Self { rules, port }
    }

    /// Evaluates the first applicable rule of every appointment and fires at most one reminder
    /// per appointment. Returns the number of fired reminders; a failing delivery is logged and
    /// does not stop the remaining appointments.
    pub async fn evaluate(&self, appointments: &[Appointment], now: DateTime<Utc>) -> usize {
        let mut fired = 0;
        for appointment in appointments {
            let Some(rule) = self.rules.iter().find(|r| r.applies_to(appointment)) else {
                continue;
            };
            let due = rule
                .compute_trigger_times(appointment)
                .into_iter()
                .any(|trigger| is_due(now, trigger));
            if !due {
                continue;
            }
            let result = match rule.kind() {
                ReminderKind::Reminder => self.port.send_reminder(appointment.id).await,
                ReminderKind::RsvpReminder => self.port.send_rsvp_reminder(appointment.id).await,
            };
            match result {
                Ok(()) => fired += 1,
                Err(e) => error!(
                    "Reminder ({}) for appointment {} failed: {}",
                    rule.name(),
                    appointment.id,
                    e
                ),
            }
        }
        fired
    }
}

/// Not in the future and at most 15 whole minutes old
fn is_due(now: DateTime<Utc>, trigger: DateTime<Utc>) -> bool {
    let diff = now - trigger;
    diff >= Duration::zero() && diff.num_minutes() <= DUE_WINDOW_MINUTES
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::appointments::models::AppointmentStatus;
    use crate::reminders::rules::default_rules;
    use std::sync::Mutex;

    #[derive(Default)]
    struct RecordingPort {
        calls: Mutex<Vec<(&'static str, i64)>>,
    }

    #[async_trait]
    impl ReminderEventPort for RecordingPort {
        async fn send_reminder(&self, id: i64) -> Result<(), BoxError> {
            self.calls.lock().unwrap().push(("reminder", id));
            Ok(())
        }
        async fn send_rsvp_reminder(&self, id: i64) -> Result<(), BoxError> {
            self.calls.lock().unwrap().push(("rsvp", id));
            Ok(())
        }
    }

    fn at(date: &str) -> DateTime<Utc> {
        DateTime::parse_from_rfc3339(date)
            .unwrap()
            .with_timezone(&Utc)
    }

    fn appointment(id: i64, start: &str, hours: i64) -> Appointment {
        let start = at(start);
        Appointment {
            id,
            name: "Test".to_string(),
            description: None,
            venue: None,
            start_time: start,
            end_time: start + Duration::hours(hours),
            minimal_attendees: None,
            created_at: None,
            last_update: None,
            status: AppointmentStatus::Planned,
        }
    }

    fn engine() -> (ReminderRuleEngine, Arc<RecordingPort>) {
        let port = Arc::new(RecordingPort::default());
        (ReminderRuleEngine::new(default_rules(), port.clone()), port)
    }

    // Monday 2025-12-01 10:00, 4 hours: weekday rule, triggers 7/4/2/1 days before
    const MONDAY: &str = "2025-12-01T10:00:00Z";

    #[tokio::test]
    async fn fires_when_a_trigger_is_due() {
        let (engine, port) = engine();
        let fired = engine
            .evaluate(&[appointment(7, MONDAY, 4)], at("2025-11-24T10:00:00Z"))
            .await;
        assert_eq!(fired, 1);
        assert_eq!(*port.calls.lock().unwrap(), vec![("rsvp", 7)]);
    }

    #[tokio::test]
    async fn due_window_is_15_whole_minutes() {
        let trigger = at("2025-11-24T10:00:00Z");
        for (offset, expected) in [
            (Duration::seconds(-1), 0), // not yet
            (Duration::zero(), 1),
            (Duration::minutes(15), 1),
            (Duration::minutes(15) + Duration::seconds(59), 1), // toMinutes() truncates
            (Duration::minutes(16), 0),
        ] {
            let (engine, _) = engine();
            let fired = engine
                .evaluate(&[appointment(1, MONDAY, 4)], trigger + offset)
                .await;
            assert_eq!(fired, expected, "offset {offset}");
        }
    }

    #[tokio::test]
    async fn only_one_reminder_per_appointment_and_run() {
        // weekend appointment: the fixed trigger -1 week and the first daily trigger coincide
        let (engine, port) = engine();
        let fired = engine
            .evaluate(
                &[appointment(1, "2025-12-07T10:00:00Z", 4)],
                at("2025-11-30T10:00:00Z"),
            )
            .await;
        assert_eq!(fired, 1);
        assert_eq!(port.calls.lock().unwrap().len(), 1);
    }

    #[tokio::test]
    async fn nothing_fires_between_triggers() {
        let (engine, port) = engine();
        let fired = engine
            .evaluate(&[appointment(1, MONDAY, 4)], at("2025-11-26T12:00:00Z"))
            .await;
        assert_eq!(fired, 0);
        assert!(port.calls.lock().unwrap().is_empty());
    }

    #[tokio::test]
    async fn only_the_first_applicable_rule_counts() {
        // 30 minutes before a weekday appointment: only AppointmentReminderRule would match, but
        // the weekday rule shadows it (as in Java), so nothing is sent
        let (engine, port) = engine();
        let fired = engine
            .evaluate(&[appointment(1, MONDAY, 4)], at("2025-12-01T09:30:00Z"))
            .await;
        assert_eq!(fired, 0);
        assert!(port.calls.lock().unwrap().is_empty());
    }

    #[tokio::test]
    async fn reminder_rule_sends_a_plain_reminder_when_it_is_the_only_rule() {
        let port = Arc::new(RecordingPort::default());
        let engine = ReminderRuleEngine::new(
            vec![Box::new(crate::reminders::rules::AppointmentReminderRule)],
            port.clone(),
        );
        let fired = engine
            .evaluate(&[appointment(3, MONDAY, 4)], at("2025-12-01T09:30:00Z"))
            .await;
        assert_eq!(fired, 1);
        assert_eq!(*port.calls.lock().unwrap(), vec![("reminder", 3)]);
    }

    #[tokio::test]
    async fn bus_port_fires_the_pending_reminder_event() {
        struct Bus(Mutex<Vec<Event>>);
        #[async_trait]
        impl EventPublisher for Bus {
            async fn fire(&self, event: Event) -> Result<(), BoxError> {
                self.0.lock().unwrap().push(event);
                Ok(())
            }
        }
        let bus = Arc::new(Bus(Mutex::new(Vec::new())));
        let port = BusReminderEventPort::new(bus.clone());

        port.send_reminder(5).await.unwrap(); // no-op like Java
        assert!(bus.0.lock().unwrap().is_empty());
        port.send_rsvp_reminder(5).await.unwrap();

        let events = bus.0.lock().unwrap();
        assert_eq!(events.len(), 1);
        assert_eq!(
            events[0].event_type,
            "AppointmentParticipationStatusPendingReminderEvent"
        );
        assert_eq!(events[0].payload, serde_json::json!({"appointment_id": 5}));
    }
}
