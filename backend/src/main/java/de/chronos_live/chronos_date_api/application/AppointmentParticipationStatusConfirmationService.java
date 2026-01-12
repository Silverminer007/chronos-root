package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.application.events.AppointmentParticipationStatusConfirmationEvent;
import de.chronos_live.chronos_date_api.domain.Appointment;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

@ApplicationScoped
public class AppointmentParticipationStatusConfirmationService {
    @Inject
    AppointmentQueryService appointmentQueryService;

    @Inject
    Event<AppointmentParticipationStatusConfirmationEvent> appointmentParticipationStatusConfirmationEvent;

    @Scheduled(cron = "0 */15 * * * ?")
    void triggerAppointmentParticipationStatusConfirmation() {
        // Wenn mehr als 24h -> 1 Monat vorher
        // Wenn Mo - Do -> gar nicht
        // Wenn Fr - So -> 1 Woche vorher
        Instant in30Days = Instant.now().plusSeconds(60 * 60 * 24 * 30);
        Instant in30DaysAnd14Minutes = in30Days.plusSeconds(60 * 14);

        List<Appointment> longAppointments =
                this.appointmentQueryService.getNonCancelledAppointmentsStartingBetween(in30Days, in30DaysAnd14Minutes);

        for (Appointment appointment : longAppointments) {
            if (appointment.getStartTime().until(appointment.getEndTime(), ChronoUnit.HOURS) < 24) {
                continue;
            }
            this.appointmentParticipationStatusConfirmationEvent.fire(
                    new AppointmentParticipationStatusConfirmationEvent(appointment.id)
            );
        }

        Instant in7Days = Instant.now().plusSeconds(60 * 60 * 24 * 7);
        Instant in7DaysAnd14Minutes = in7Days.plusSeconds(60 * 14);
        List<Appointment> weekendAppointments =
                this.appointmentQueryService.getNonCancelledAppointmentsStartingBetween(in7Days, in7DaysAnd14Minutes);
        for (Appointment appointment : weekendAppointments) {
            List<DayOfWeek> weekdays = List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY);
            if (weekdays.contains(appointment.getStartTime().atZone(ZoneOffset.UTC).getDayOfWeek())) {
                continue;
            }
            this.appointmentParticipationStatusConfirmationEvent.fire(
                    new AppointmentParticipationStatusConfirmationEvent(appointment.id)
            );
        }
    }
}
