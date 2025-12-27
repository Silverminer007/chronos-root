package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.AppointmentParticipation;
import de.chronos_live.chronos_date_api.domain.ParticipationStatistik;
import de.chronos_live.chronos_date_api.domain.ParticipationStatus;
import de.chronos_live.chronos_date_api.domain.UserRole;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class AppointmentParticipationQueryService {
    public UserRole getUserRole(Long appointmentId, Long userId) {
        return AppointmentParticipation
                .find("appointment.id = ?1 AND user.id = ?2", appointmentId, userId)
                .firstResultOptional()
                .map(ap ->
                        ((AppointmentParticipation) ap).getRole())
                .orElse(UserRole.NONE);
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