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
        String sqlQuery = "SELECT a FROM appointment WHERE id = ?1 AND status != ?2";
        if (messages) {
            sqlQuery += " JOIN FETCH a.messages";
        }
        if (participants) {
            sqlQuery += " JOIN FETCH a.participants";
        }
        if (groupParticipants) {
            sqlQuery += " JOIN FETCH a.groupParticipants";
        }
        return (Appointment) Appointment.find(sqlQuery, appointmentId, AppointmentStatus.DELETED).firstResultOptional()
                .orElseThrow(() -> new ResourceNotFoundException("appointment", appointmentId));
    }

    public List<Appointment> search(Long requestingUserId, String query,
                                    Instant after, Instant before,
                                    int page, int pageSize,
                                    boolean messages, boolean participants, boolean groupParticipants) {
        String sqlQuery = "SELECT ap FROM appointment_participation WHERE user_id = ?5 JOIN ap.appointment a";
        sqlQuery += " WHERE endTime > ?1 AND startTime < ?2 AND status != ?3";
        if (query != null) {
            sqlQuery += " AND (lower(name) LIKE lower(?4) OR lower(description) LIKE lower(?4) OR lower(venue) LIKE lower(?4))";
        }
        if (messages) {
            sqlQuery += " JOIN FETCH a.messages";
        }
        if (participants) {
            sqlQuery += " JOIN FETCH a.participants";
        }
        if (groupParticipants) {
            sqlQuery += " JOIN FETCH a.groupParticipants";
        }
        sqlQuery += " ORDER BY startTime";

        return Appointment.find(sqlQuery, after, before, AppointmentStatus.DELETED, query).list();
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
        return this.appointmentRepository.findMatchingWeekendAppointments(baseMinutes, maxDays, maxAppointmentLength, offsetWeeks);
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