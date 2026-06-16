package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.AppointmentParticipation;
import de.chronos_live.chronos_date_api.domain.ParticipationStatistik;
import de.chronos_live.chronos_date_api.domain.ParticipationStatus;
import de.chronos_live.chronos_date_api.domain.UserRole;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
@Timed("service.participationQuery")
public class AppointmentParticipationQueryService {
    private static final Logger LOGGER = Logger.getLogger(AppointmentParticipationQueryService.class);
    public UserRole getUserRole(Long appointmentId, Long userId) {
        LOGGER.debugf("[Principal %s][Appointment %s] Reading user role", userId, appointmentId);

        return AppointmentParticipation
                .find("appointment.id = ?1 AND user.id = ?2", appointmentId, userId)
                .firstResultOptional()
                .map(ap ->
                        ((AppointmentParticipation) ap).getRole())
                .orElse(UserRole.NONE);
    }

    public ParticipationStatus getUserStatus(Long appointmentId, Long userId) {
        LOGGER.debugf("[Principal %s][Appointment %s] Reading user status", userId, appointmentId);

        return AppointmentParticipation
                .find("appointment.id = ?1 AND user.id = ?2", appointmentId, userId)
                .firstResultOptional()
                .map(ap ->
                        ((AppointmentParticipation) ap).getStatus())
                .orElse(ParticipationStatus.PENDING);
    }

    public List<AppointmentParticipation> getParticipants(Long appointmentId) {
        return AppointmentParticipation.list("appointment.id = ?1", appointmentId);
    }

    public ParticipationStatistik getParticipationStatistik(Long appointmentId) {
        List<AppointmentParticipation> appointmentParticipationList =
                AppointmentParticipation.list("appointment.id = ?1", appointmentId);

        long participantCount = appointmentParticipationList.size();
        long approvedCount = appointmentParticipationList.stream()
                .filter(ap ->
                        ParticipationStatus.APPROVED.equals(ap.getStatus()))
                .count();
        long rejectedCount = appointmentParticipationList.stream()
                .filter(ap ->
                        ParticipationStatus.REJECTED.equals(ap.getStatus()))
                .count();
        return new ParticipationStatistik(participantCount, approvedCount, rejectedCount);
    }
}
