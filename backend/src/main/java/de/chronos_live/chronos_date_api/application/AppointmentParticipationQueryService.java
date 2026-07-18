package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.AppointmentParticipation;
import de.chronos_live.chronos_date_api.domain.ParticipationStatistik;
import de.chronos_live.chronos_date_api.domain.ParticipationStatus;
import de.chronos_live.chronos_date_api.domain.UserRole;
import de.chronos_live.chronos_date_api.infrastructure.AppointmentParticipationRepository;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
@Timed("service.participationQuery")
public class AppointmentParticipationQueryService {
    private static final Logger LOGGER = Logger.getLogger(AppointmentParticipationQueryService.class);

    @Inject
    AppointmentParticipationRepository participationRepository;

    public UserRole getUserRole(Long appointmentId, String userOidcId) {
        LOGGER.debugf("[Principal %s][Appointment %s] Reading user role", userOidcId, appointmentId);
        return participationRepository.findByAppointmentAndUser(appointmentId, userOidcId)
                .map(AppointmentParticipation::getRole)
                .orElse(UserRole.NONE);
    }

    public ParticipationStatus getUserStatus(Long appointmentId, String userOidcId) {
        LOGGER.debugf("[Principal %s][Appointment %s] Reading user status", userOidcId, appointmentId);
        return participationRepository.findByAppointmentAndUser(appointmentId, userOidcId)
                .map(AppointmentParticipation::getStatus)
                .orElse(ParticipationStatus.PENDING);
    }

    public List<AppointmentParticipation> getParticipants(Long appointmentId) {
        return participationRepository.listByAppointment(appointmentId);
    }

    public ParticipationStatistik getParticipationStatistik(Long appointmentId) {
        List<AppointmentParticipation> list = participationRepository.listByAppointment(appointmentId);
        long total = list.size();
        long approved = list.stream().filter(ap -> ParticipationStatus.APPROVED.equals(ap.getStatus())).count();
        long rejected = list.stream().filter(ap -> ParticipationStatus.REJECTED.equals(ap.getStatus())).count();
        return new ParticipationStatistik(total, approved, rejected);
    }
}
