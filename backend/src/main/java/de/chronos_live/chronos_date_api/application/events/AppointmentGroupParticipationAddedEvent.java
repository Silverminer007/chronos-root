package de.chronos_live.chronos_date_api.application.events;

public record AppointmentGroupParticipationAddedEvent(Long appointmentId, Long groupId, Long actingUserId) {
}