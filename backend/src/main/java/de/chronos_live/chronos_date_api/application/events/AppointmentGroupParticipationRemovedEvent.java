package de.chronos_live.chronos_date_api.application.events;

public record AppointmentGroupParticipationRemovedEvent(Long appointmentId, Long groupId, String actingUserOidcId) {
}
