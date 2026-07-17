package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.Appointment;
import de.chronos_live.chronos_date_api.infrastructure.AppointmentRepository;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
@Timed("service.appointmentQuery")
public class AppointmentQueryService {
    @Inject
    AppointmentRepository appointmentRepository;

    public Appointment findById(Long id) {
        return appointmentRepository.findById(id);
    }

    public Appointment getAppointment(Long appointmentId, boolean messages, boolean participants, boolean groupParticipants) {
        return appointmentRepository.getAppointment(appointmentId, messages, participants, groupParticipants);
    }

    public record SearchResult(List<Appointment> items, long total) {}

    public SearchResult search(String requestingUserOidcId, String query,
                               Instant after, Instant before,
                               int page, int pageSize,
                               boolean messages, boolean participants, boolean groupParticipants) {
        AppointmentRepository.SearchResult r = appointmentRepository.search(
                requestingUserOidcId, query, after, before, page, pageSize, messages, participants, groupParticipants);
        return new SearchResult(r.items(), r.total());
    }

    public List<Appointment> getNonCancelledAppointmentsStartingAt(Instant at) {
        return appointmentRepository.findNonCancelledBetween(at.minusSeconds(30), at.plusSeconds(30));
    }

    public List<Appointment> getNonCancelledAppointmentsStartingBetween(Instant after, Instant before) {
        return appointmentRepository.findNonCancelledBetween(after, before);
    }

    public List<Appointment> getPlannedAppointmentsStartingBetween(Instant after, Instant before) {
        return appointmentRepository.findPlannedBetween(after, before);
    }

    public List<Appointment> findMatchingAppointments(int baseMinutes, int maxDays, int minAppointmentLength, int offsetWeeks) {
        return appointmentRepository.findMatchingAppointments(baseMinutes, maxDays, minAppointmentLength, offsetWeeks);
    }

    public List<Appointment> findMatchingWeekdayAppointments(int baseMinutes, int maxDays, int maxAppointmentLength, int offsetWeeks) {
        return appointmentRepository.findMatchingWeekdayAppointments(baseMinutes, maxDays, maxAppointmentLength, offsetWeeks);
    }

    public List<Appointment> findMatchingWeekendAppointments(int baseMinutes, int maxDays, int maxAppointmentLength, int offsetWeeks) {
        return appointmentRepository.findMatchingWeekendAppointments(baseMinutes, maxDays, maxAppointmentLength, offsetWeeks);
    }
}
