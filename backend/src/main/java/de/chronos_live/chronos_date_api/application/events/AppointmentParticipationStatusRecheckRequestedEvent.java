package de.chronos_live.chronos_date_api.application.events;

public record AppointmentParticipationStatusRecheckRequestedEvent(Long appointmentId, Long actingUserId) {
}
