package de.chronos_live.chronos_date_api.application.events;

public record AppointmentParticipationRemovedEvent(Long appointmentId, Long targetUserId, Long actingUserId) {
}