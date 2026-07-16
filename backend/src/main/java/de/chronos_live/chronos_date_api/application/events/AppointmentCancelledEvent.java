package de.chronos_live.chronos_date_api.application.events;

public record AppointmentCancelledEvent(Long cancelledAppointmentId, String actingUserOidcId) {
}
