package de.chronos_live.chronos_date_api.application.events;

public record AppointmentCreatedEvent(Long appointmentId, Long creatorId) {
}
