package de.chronos_live.chronos_date_api.application.events;

public record AppointmentParticipationAddedEvent(Long appointmentId, Long targetUserId, Long actingUserId) {
}