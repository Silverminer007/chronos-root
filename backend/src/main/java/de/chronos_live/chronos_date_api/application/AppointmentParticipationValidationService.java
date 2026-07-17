package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.application.events.AppointmentEditedEvent;
import de.chronos_live.chronos_date_api.application.events.AppointmentParticipationInvalidEvent;
import de.chronos_live.chronos_date_api.application.events.AppointmentParticipationStatusChangedEvent;
import de.chronos_live.chronos_date_api.domain.Appointment;
import de.chronos_live.chronos_date_api.domain.AppointmentStatus;
import de.chronos_live.chronos_date_api.domain.ParticipationStatistik;
import de.chronos_live.chronos_date_api.domain.ParticipationStatus;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

@ApplicationScoped
@Timed("service.participationValidation")
public class AppointmentParticipationValidationService {
    @Inject
    AppointmentQueryService appointmentQueryService;

    @Inject
    AppointmentParticipationQueryService appointmentParticipationQueryService;

    @Inject
    Event<AppointmentParticipationInvalidEvent> appointmentParticipationInvalidEvent;

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    LeaderElectionService leaderElectionService;

    @Scheduled(cron = "0 */15 * * * ?")
    void sendNonMinimalAttendeesAlerts() {
        if (!leaderElectionService.isLeader()) return;
        // Sobald PENDING + APPROVED < REQUIRED
        // Wenn mehr als 24h -> 8 Wochen vorher
        // Wenn Mo - DO -> 1 Woche vorher
        // Wenn Fr - So -> 2 Wochen vorher
        Instant in8Weeks = Instant.now().plusSeconds(60 * 60 * 24 * 7 * 8);
        Instant in8WeeksAnd14Minutes = in8Weeks.plusSeconds(60 * 14);
        List<Appointment> longAppointments = this.appointmentQueryService.getPlannedAppointmentsStartingBetween(in8Weeks, in8WeeksAnd14Minutes);
        for (Appointment appointment : longAppointments) {
            if (appointment.getStartTime().until(appointment.getEndTime(), ChronoUnit.HOURS) < 24) {
                continue;
            }
            this.checkForEnoughAttendees(appointment);
        }

        Instant in1Weeks = Instant.now().plusSeconds(60 * 60 * 24 * 7);
        Instant in1WeeksAnd14Minutes = in1Weeks.plusSeconds(60 * 14);
        List<Appointment> weekdayAppointments = this.appointmentQueryService.getPlannedAppointmentsStartingBetween(in1Weeks, in1WeeksAnd14Minutes);
        for (Appointment appointment : weekdayAppointments) {
            if (!this.isWeekdayAtUTC(appointment.getStartTime())) {
                continue;
            }
            this.checkForEnoughAttendees(appointment);
        }

        Instant in2Weeks = Instant.now().plusSeconds(60 * 60 * 24 * 7 * 2);
        Instant in2WeeksAnd14Minutes = in2Weeks.plusSeconds(60 * 14);
        List<Appointment> weekendAppointments = this.appointmentQueryService.getPlannedAppointmentsStartingBetween(in2Weeks, in2WeeksAnd14Minutes);
        for (Appointment appointment : weekendAppointments) {
            if (this.isWeekdayAtUTC(appointment.getStartTime())) {
                continue;
            }
            this.checkForEnoughAttendees(appointment);
        }
    }

    public void checkForEnoughAttendees(Appointment appointment) {
        if (appointment.getStatus().equals(AppointmentStatus.CANCELLED)) {
            return;
        }

        ParticipationStatistik participationStatistik =
                this.appointmentParticipationQueryService.getParticipationStatistik(appointment.id);

        if (participationStatistik.approvedCount() >= appointment.getMinimalAttendees()) {
            return;
        }

        if(appointment.getStatus().equals(AppointmentStatus.NOT_ENOUGH_ATTENDEES)) {
            return;
        }

        appointment.setStatus(AppointmentStatus.NOT_ENOUGH_ATTENDEES);

        this.appointmentParticipationInvalidEvent.fire(
                new AppointmentParticipationInvalidEvent(appointment.id)
        );
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onAppointmentParticipationStatusChanged(@ObservesAsync AppointmentParticipationStatusChangedEvent event) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            Appointment appointment = appointmentQueryService.findById(event.appointmentId());

            if (appointment.getStartTime().isBefore(Instant.now())) {
                return;
            }

            if (AppointmentStatus.CANCELLED.equals(appointment.getStatus())
                    || AppointmentStatus.DELETED.equals(appointment.getStatus())) {
                return;
            }

            if (ParticipationStatus.REJECTED.equals(event.newParticipationStatus())
                    && AppointmentStatus.NOT_ENOUGH_ATTENDEES.equals(appointment.getStatus())) {
                return;
            }

            ParticipationStatistik participationStatistik =
                    this.appointmentParticipationQueryService.getParticipationStatistik(appointment.id);
            if (AppointmentStatus.PLANNED.equals(appointment.getStatus())) {
                if (participationStatistik.participantCount() - participationStatistik.rejectedCount() >= appointment.getMinimalAttendees()) {
                    return;
                }

                appointment.setStatus(AppointmentStatus.NOT_ENOUGH_ATTENDEES);

                this.appointmentParticipationInvalidEvent.fire(
                        new AppointmentParticipationInvalidEvent(appointment.id)
                );
            } else {
                if (participationStatistik.participantCount() - participationStatistik.rejectedCount() < appointment.getMinimalAttendees()) {
                    return;
                }

                appointment.setStatus(AppointmentStatus.PLANNED);
            }
        } finally {
            sample.stop(Timer.builder("observer.participationValidation.onParticipationStatusChanged")
                    .description("Time for participation validation on status change")
                    .register(meterRegistry));
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onAppointmentEdited(@Observes AppointmentEditedEvent event) {
        // Falls sich die mindest Teilnehmenden Zahl geändert hat
        Appointment appointment = appointmentQueryService.findById(event.appointmentId());

        if(appointment.getMinimalAttendees() == null) {
            return;
        }
        if (appointment.getStartTime().isBefore(Instant.now())) {
            return;
        }
        if (AppointmentStatus.CANCELLED.equals(appointment.getStatus())
                || AppointmentStatus.DELETED.equals(appointment.getStatus())) {
            return;
        }

        long feedbackDeadlineInWeeks;
        if (appointment.getStartTime().until(appointment.getEndTime(), ChronoUnit.HOURS) >= 24) {
            feedbackDeadlineInWeeks = 8;
        } else if (this.isWeekdayAtUTC(appointment.getStartTime())) {
            feedbackDeadlineInWeeks = 1;
        } else {
            feedbackDeadlineInWeeks = 2;
        }

        long weeksUntilAppointment = Instant.now().until(appointment.getStartTime(), ChronoUnit.DAYS) / 7;

        ParticipationStatistik participationStatistik =
                this.appointmentParticipationQueryService.getParticipationStatistik(appointment.id);

        // Wenn wir schon nach dem Feedback Zeitraum haben, dann wird schneller entschieden, dass zu wenig teilnehmende dabei sind
        if (feedbackDeadlineInWeeks > weeksUntilAppointment) {
            if (participationStatistik.approvedCount() >= appointment.getMinimalAttendees()) {
                return;
            }
        } else {
            if (participationStatistik.participantCount() - participationStatistik.rejectedCount() >= appointment.getMinimalAttendees()) {
                return;
            }
        }
        appointment.setStatus(AppointmentStatus.NOT_ENOUGH_ATTENDEES);
        this.appointmentParticipationInvalidEvent.fire(
                new AppointmentParticipationInvalidEvent(appointment.id)
        );
    }

    private boolean isWeekdayAtUTC(Instant instant) {
        List<DayOfWeek> weekdays = List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY);
        return weekdays.contains(instant.atZone(ZoneOffset.UTC).getDayOfWeek());
    }
}
