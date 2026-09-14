use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ReminderTriggerTimes {
    pub appointment_id: Uuid,
    pub trigger_times: Vec<DateTime<Utc>>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppointmentReminderEvent {
    pub appointment_id: Uuid,
    pub scheduled_time: DateTime<Utc>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppointmentParticipationStatusPendingReminderEvent {
    pub appointment_id: Uuid,
    pub user_id: Uuid,
    pub scheduled_time: DateTime<Utc>,
}

impl AppointmentReminderEvent {
    pub fn new(appointment_id: Uuid, scheduled_time: DateTime<Utc>) -> Self {
        Self { appointment_id, scheduled_time }
    }
}

impl AppointmentParticipationStatusPendingReminderEvent {
    pub fn new(appointment_id: Uuid, user_id: Uuid, scheduled_time: DateTime<Utc>) -> Self {
        Self { appointment_id, user_id, scheduled_time }
    }
}
