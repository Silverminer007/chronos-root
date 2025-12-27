package de.chronos_live.chronos_date_api.application.events;

import de.chronos_live.chronos_date_api.domain.Appointment;

public record AppointmentCancelledEvent(Long cancelledAppointmentId, Long actingUserId) {
}
