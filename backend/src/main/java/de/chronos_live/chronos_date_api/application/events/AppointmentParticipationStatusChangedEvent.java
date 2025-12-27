package de.chronos_live.chronos_date_api.application.events;

import de.chronos_live.chronos_date_api.domain.ParticipationStatus;

public record AppointmentParticipationStatusChangedEvent(Long appointmentId, Long actingUserId,
                                                         ParticipationStatus newParticipationStatus,
                                                         ParticipationStatus oldParticipationStatus) {
}