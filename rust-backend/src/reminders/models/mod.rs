//! Event payloads fired by the reminder engine. The push listener reads `appointment_id` only
//! (Java: `AppointmentParticipationStatusPendingReminderEvent(Long appointmentId)`).

use serde_json::{json, Value};

pub const PENDING_REMINDER_EVENT: &str = "AppointmentParticipationStatusPendingReminderEvent";

pub fn pending_reminder_payload(appointment_id: i64) -> Value {
    json!({ "appointment_id": appointment_id })
}
