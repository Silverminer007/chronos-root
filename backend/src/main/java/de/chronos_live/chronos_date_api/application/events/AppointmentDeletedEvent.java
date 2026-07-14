package de.chronos_live.chronos_date_api.application.events;

public record AppointmentDeletedEvent(Long deletedAppointmentId, String actingUserOidcId) {
}
