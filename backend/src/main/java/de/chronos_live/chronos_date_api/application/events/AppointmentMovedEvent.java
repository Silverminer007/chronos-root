package de.chronos_live.chronos_date_api.application.events;

import java.time.Instant;

public record AppointmentMovedEvent(Long appointmentId, Instant oldStartTime, Instant oldEndTime, String actingUserOidcId) {
}
