use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

/// Event fired when an appointment is created
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppointmentCreatedEvent {
    pub appointment_id: Uuid,
    pub creator_id: String,
    pub timestamp: i64,
}

impl AppointmentCreatedEvent {
    pub fn new(appointment_id: Uuid, creator_id: String) -> Self {
        Self {
            appointment_id,
            creator_id,
            timestamp: Utc::now().timestamp(),
        }
    }
}

/// Event fired when an appointment is edited
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppointmentEditedEvent {
    pub appointment_id: Uuid,
    pub timestamp: i64,
}

impl AppointmentEditedEvent {
    pub fn new(appointment_id: Uuid) -> Self {
        Self {
            appointment_id,
            timestamp: Utc::now().timestamp(),
        }
    }
}

/// Event fired when an appointment is moved (time changed)
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppointmentMovedEvent {
    pub appointment_id: Uuid,
    pub old_start: i64,
    pub old_end: i64,
    pub timestamp: i64,
}

impl AppointmentMovedEvent {
    pub fn new(appointment_id: Uuid, old_start: DateTime<Utc>, old_end: DateTime<Utc>) -> Self {
        Self {
            appointment_id,
            old_start: old_start.timestamp(),
            old_end: old_end.timestamp(),
            timestamp: Utc::now().timestamp(),
        }
    }
}

/// Event fired when an appointment is deleted
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppointmentDeletedEvent {
    pub appointment_id: Uuid,
    pub timestamp: i64,
}

impl AppointmentDeletedEvent {
    pub fn new(appointment_id: Uuid) -> Self {
        Self {
            appointment_id,
            timestamp: Utc::now().timestamp(),
        }
    }
}

/// Event fired when an appointment is cancelled
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppointmentCancelledEvent {
    pub appointment_id: Uuid,
    pub timestamp: i64,
}

impl AppointmentCancelledEvent {
    pub fn new(appointment_id: Uuid) -> Self {
        Self {
            appointment_id,
            timestamp: Utc::now().timestamp(),
        }
    }
}

/// Event fired when a participant is added to an appointment
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppointmentParticipationAddedEvent {
    pub appointment_id: Uuid,
    pub target_user_id: String,
    pub acting_user_id: String,
    pub timestamp: i64,
}

impl AppointmentParticipationAddedEvent {
    pub fn new(appointment_id: Uuid, target_user_id: String, acting_user_id: String) -> Self {
        Self {
            appointment_id,
            target_user_id,
            acting_user_id,
            timestamp: Utc::now().timestamp(),
        }
    }
}
