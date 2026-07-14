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

    public UserRole getUserRole(Long appointmentId, String userOidcId) {
        LOGGER.debugf("[Principal %s][Appointment %s] Reading user role", userOidcId, appointmentId);

        return AppointmentParticipation
                .find("appointment.id = ?1 AND userOidcId = ?2", appointmentId, userOidcId)
                .firstResultOptional()
                .map(ap -> ((AppointmentParticipation) ap).getRole())
                .orElse(UserRole.NONE);
    }

    public ParticipationStatus getUserStatus(Long appointmentId, String userOidcId) {
        LOGGER.debugf("[Principal %s][Appointment %s] Reading user status", userOidcId, appointmentId);

        return AppointmentParticipation
                .find("appointment.id = ?1 AND userOidcId = ?2", appointmentId, userOidcId)
                .firstResultOptional()
                .map(ap -> ((AppointmentParticipation) ap).getStatus())
                .orElse(ParticipationStatus.PENDING);
    }

    public List<AppointmentParticipation> getParticipants(Long appointmentId) {
        return AppointmentParticipation.list("appointment.id = ?1", appointmentId);
    }

    public ParticipationStatistik getParticipationStatistik(Long appointmentId) {
        List<AppointmentParticipation> list = AppointmentParticipation.list("appointment.id = ?1", appointmentId);
        long total = list.size();
        long approved = list.stream().filter(ap -> ParticipationStatus.APPROVED.equals(ap.getStatus())).count();
        long rejected = list.stream().filter(ap -> ParticipationStatus.REJECTED.equals(ap.getStatus())).count();
        return new ParticipationStatistik(total, approved, rejected);
    }
}
