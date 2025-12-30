package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.application.events.AppointmentParticipationStatusPendingReminderEvent;
import de.chronos_live.chronos_date_api.domain.Appointment;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class AppointmentParticipationStatusPendingReminderService {
    @Inject
    AppointmentQueryService appointmentQueryService;

    @Inject
    Event<AppointmentParticipationStatusPendingReminderEvent> appointmentParticipationStatusPendingReminderEvent;

    @Scheduled(cron = "0 */15 * * * ?")
    void sendEventAttendanceStatusPendingReminder() {
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
}
