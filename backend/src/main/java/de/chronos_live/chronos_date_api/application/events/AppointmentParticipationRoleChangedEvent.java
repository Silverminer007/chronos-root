package de.chronos_live.chronos_date_api.application.events;

import de.chronos_live.chronos_date_api.domain.UserRole;

public record AppointmentParticipationRoleChangedEvent(Long appointmentId, String targetUserOidcId, String actingUserOidcId, UserRole oldRole) {
}
