use chrono::Utc;
use serde_json::json;
use std::sync::Arc;
use crate::appointments::models::Appointment;
use crate::event_bus::EventBus;
use crate::reminders::models::{AppointmentParticipationStatusPendingReminderEvent, AppointmentReminderEvent};
use crate::reminders::rules::ReminderRule;

pub struct ReminderRuleEngine {
    event_bus: Arc<dyn EventBus>,
    rules: Vec<Box<dyn ReminderRule>>,
}

impl ReminderRuleEngine {
    pub fn new(event_bus: Arc<dyn EventBus>, rules: Vec<Box<dyn ReminderRule>>) -> Self {
        Self { event_bus, rules }
    }

    pub async fn evaluate_appointment(&self, appointment: &Appointment) -> Vec<AppointmentReminderEvent> {
        let mut reminder_events = Vec::new();
        let now = Utc::now();

        for rule in &self.rules {
            let trigger_times = rule.evaluate(appointment).await;
            for trigger_time in trigger_times.trigger_times {
                if trigger_time <= now {
                    reminder_events.push(AppointmentReminderEvent::new(appointment.id, trigger_time));
                }
            }
        }

        reminder_events
    }

    pub async fn fire_reminder_event(&self, event: AppointmentReminderEvent) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let payload = json!({
            "appointment_id": event.appointment_id,
            "scheduled_time": event.scheduled_time,
        });
        let event = crate::event_bus::Event::new("AppointmentReminderEvent", payload);
        self.event_bus.fire(event).await
    }

    pub async fn fire_participation_reminder_event(&self, event: AppointmentParticipationStatusPendingReminderEvent) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let payload = json!({
            "appointment_id": event.appointment_id,
            "user_id": event.user_id,
            "scheduled_time": event.scheduled_time,
        });
        let event = crate::event_bus::Event::new("AppointmentParticipationStatusPendingReminderEvent", payload);
        self.event_bus.fire(event).await
    }
}
