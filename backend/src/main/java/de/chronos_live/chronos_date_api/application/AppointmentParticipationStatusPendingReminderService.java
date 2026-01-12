package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.application.events.AppointmentParticipationStatusPendingReminderEvent;
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
public class AppointmentParticipationStatusPendingReminderService {
    @Inject
    AppointmentQueryService appointmentQueryService;

    @Inject
    Event<AppointmentParticipationStatusPendingReminderEvent> appointmentParticipationStatusPendingReminderEvent;

    @Scheduled(cron = "0 */15 * * * ?")
    void sendAppointmentParticipationStatusPendingReminder() {
        // Wenn mehr als 24h -> 2 Monate vorher
        // Wenn Mo - Do -> 1 Woche vorher
        // Wenn Fr - So -> 2 Wochen vorher

        List<Appointment> longAppointments =
                this.appointmentQueryService.findMatchingAppointments(
                        /*Checking every*/15/*Minutes*/,
                        /*Starting*/60 * 24 * 7 * 16 /*Minutes before the event starts*/,
                        /*The Appointment has a length of at least*/24/*hours*/,
                        /*Everyone shall have answered until*/8/*weeks before appointment starts*/);
        for (Appointment appointment : longAppointments) {
            this.appointmentParticipationStatusPendingReminderEvent.fire(
                    new AppointmentParticipationStatusPendingReminderEvent(appointment.id)
            );
        }

        List<Appointment> shortWeekdayAppointments =
                this.appointmentQueryService.findMatchingWeekdayAppointments(
                        /*Checking every*/15/*Minutes*/,
                        /*Starting*/60 * 24 * 7 * 2 /*Minutes before the event starts*/,
                        /*The Appointment has a length of at max*/24/*hours*/,
                        /*Everyone shall have answered until*/1/*week before appointment starts*/);
        for (Appointment appointment : shortWeekdayAppointments) {
            this.appointmentParticipationStatusPendingReminderEvent.fire(
                    new AppointmentParticipationStatusPendingReminderEvent(appointment.id)
            );
        }

        List<Appointment> shortWeekendAppointments =
                this.appointmentQueryService.findMatchingWeekendAppointments(
                        /*Checking every*/15/*Minutes*/,
                        /*Starting*/60 * 24 * 7 * 4 /*Minutes before the event starts*/,
                        /*The Appointment has a length of at max*/24/*hours*/,
                        /*Everyone shall have answered until*/2/*weeks before appointment starts*/);
        for (Appointment appointment : shortWeekendAppointments) {
            this.appointmentParticipationStatusPendingReminderEvent.fire(
                    new AppointmentParticipationStatusPendingReminderEvent(appointment.id)
            );
        }
    }

    @Scheduled(cron = "0 0 17 * * ?")
    void sendEventAttendanceStatusPendingReminderAfterTargetTime() {
        List<Appointment> appointmentList = this.appointmentQueryService
                .getNonCancelledAppointmentsStartingBetween(
                        Instant.now(),
                        Instant.now().plusSeconds(60 * 60 * 24 * 7 * 8)
                );

        for(Appointment appointment : appointmentList) {
            if(appointment.getStartTime().until(appointment.getEndTime(), ChronoUnit.HOURS) < 24) {
                if(isWeekdayAtUTC(appointment.getStartTime())) {
                    if(Instant.now().until(appointment.getStartTime(), ChronoUnit.DAYS) * 7 > 1) {
                        continue;
                    }
                } else {
                    if(Instant.now().until(appointment.getStartTime(), ChronoUnit.DAYS) * 7 > 2) {
                        continue;
                    }
                }
            }
            this.appointmentParticipationStatusPendingReminderEvent.fire(
                    new AppointmentParticipationStatusPendingReminderEvent(appointment.id)
            );
        }
    }

    private boolean isWeekdayAtUTC(Instant instant) {
        List<DayOfWeek> weekdays = List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY);
        return weekdays.contains(instant.atZone(ZoneOffset.UTC).getDayOfWeek());
    }
}
