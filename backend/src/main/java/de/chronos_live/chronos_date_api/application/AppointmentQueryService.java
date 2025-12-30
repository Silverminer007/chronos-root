package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.Appointment;
import de.chronos_live.chronos_date_api.domain.AppointmentStatus;
import de.chronos_live.chronos_date_api.exception.ResourceNotFoundException;
import de.chronos_live.chronos_date_api.infrastructure.AppointmentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class AppointmentQueryService {
    @Inject
    AppointmentRepository appointmentRepository;

    public Appointment getAppointment(Long appointmentId, boolean messages, boolean participants, boolean groupParticipants) {
        String sqlQuery = "SELECT a FROM Appointment a";
        if (messages) {
            sqlQuery += " LEFT JOIN FETCH a.messages";
        }
        if (participants) {
            sqlQuery += " LEFT JOIN FETCH a.participants p LEFT JOIN FETCH p.user";
        }

        if (groupParticipants) {
            sqlQuery += " LEFT JOIN FETCH a.groupParticipants gp LEFT JOIN FETCH gp.group g LEFT JOIN FETCH g.members";
        }
        sqlQuery += " WHERE a.id = ?1 AND a.status != ?2";
        return (Appointment) Appointment.find(sqlQuery, appointmentId, AppointmentStatus.DELETED).firstResultOptional()
                .orElseThrow(() -> new ResourceNotFoundException("appointment", appointmentId));
    }

    public List<Appointment> search(Long requestingUserId, String query,
                                    Instant after, Instant before,
                                    int page, int pageSize,
                                    boolean messages, boolean participants, boolean groupParticipants) {
        String sqlQuery = "SELECT a FROM AppointmentParticipation ap JOIN ap.appointment a";
        if (messages) {
            sqlQuery += " LEFT JOIN FETCH a.messages";
        }
        if (participants) {
            sqlQuery += " LEFT JOIN FETCH a.participants part LEFT JOIN FETCH part.user";
        }
        if (groupParticipants) {
            sqlQuery += " LEFT JOIN FETCH a.groupParticipants";
        }
        sqlQuery += " WHERE ap.user.id = ?1 AND a.endTime > ?2 AND a.startTime < ?3 AND a.status != ?4";
        if (query != null) {
            sqlQuery += " AND (lower(a.name) LIKE lower(?5) OR lower(a.description) LIKE lower(?5) OR lower(a.venue) LIKE lower(?5))";
        }
        sqlQuery += " ORDER BY a.startTime";

        if (query != null) {
            return Appointment.find(sqlQuery, requestingUserId, after, before, AppointmentStatus.DELETED, "%" + query + "%").page(page, pageSize).list();
        }
        return Appointment.find(sqlQuery, requestingUserId, after, before, AppointmentStatus.DELETED).page(page, pageSize).list();
    }

    public List<Appointment> getNonCancelledAppointmentsStartingAt(Instant at) {
        return this.getNonCancelledAppointmentsStartingBetween(at.minusSeconds(30), at.plusSeconds(30));
    }

    public List<Appointment> getNonCancelledAppointmentsStartingBetween(Instant after, Instant before) {
        return Appointment.find(
                        "startTime < ?1 AND startTime > ?2 AND status != ?3 AND status != ?4"
                        , before, after, AppointmentStatus.DELETED, AppointmentStatus.CANCELLED)
                .list();
    }

    public List<Appointment> getPlannedAppointmentsStartingBetween(Instant after, Instant before) {
        return Appointment.find(
                        "startTime < ?1 AND startTime > ?2 AND status = ?3"
                        , before, after, AppointmentStatus.PLANNED)
                .list();
    }

    public List<Appointment> findMatchingAppointments(
            int baseMinutes,
            int maxDays,
            int minAppointmentLength,
            int offsetWeeks
    ) {
        return this.appointmentRepository.findMatchingAppointments(baseMinutes, maxDays, minAppointmentLength, offsetWeeks);
    }

    public List<Appointment> findMatchingWeekdayAppointments(
            int baseMinutes,
            int maxDays,
            int maxAppointmentLength,
            int offsetWeeks
    ) {
        return this.appointmentRepository.findMatchingWeekdayAppointments(baseMinutes, maxDays, maxAppointmentLength, offsetWeeks);
    }

    public List<Appointment> findMatchingWeekendAppointments(
            int baseMinutes,
            int maxDays,
            int maxAppointmentLength,
            int offsetWeeks
    ) {
        return this.appointmentRepository.findMatchingWeekendAppointments(baseMinutes, maxDays, maxAppointmentLength, offsetWeeks);
    }
}