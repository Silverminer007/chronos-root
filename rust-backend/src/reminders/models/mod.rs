use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ReminderTriggerTimes {
    pub appointment_id: i64,
    pub trigger_times: Vec<DateTime<Utc>>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppointmentReminderEvent {
    pub appointment_id: i64,
    pub scheduled_time: DateTime<Utc>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppointmentParticipationStatusPendingReminderEvent {
    pub appointment_id: i64,
    pub user_id: String,
    pub scheduled_time: DateTime<Utc>,
}

impl AppointmentReminderEvent {
    pub fn new(appointment_id: i64, scheduled_time: DateTime<Utc>) -> Self {
        Self {
            appointment_id,
            scheduled_time,
        }
    }
}

impl AppointmentParticipationStatusPendingReminderEvent {
    pub fn new(appointment_id: i64, user_id: String, scheduled_time: DateTime<Utc>) -> Self {
        Self {
            appointment_id,
            user_id,
            scheduled_time,
        }
    }
}
