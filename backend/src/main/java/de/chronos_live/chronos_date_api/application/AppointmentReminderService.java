package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.application.events.AppointmentReminderEvent;
import de.chronos_live.chronos_date_api.domain.Appointment;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class AppointmentReminderService {
    @Inject
    AppointmentQueryService appointmentQueryService;

    @Inject
    Event<AppointmentReminderEvent> appointmentReminderEvent;

    @Scheduled(cron = "0 */1 * * * ?")
    void triggerAppointmentReminder() {
        long minutesUntilStart = 30L;
        Instant in30Minutes = Instant.now().plusSeconds(60L * minutesUntilStart);

        List<Appointment> appointments = this.appointmentQueryService.getNonCancelledAppointmentsStartingAt(in30Minutes);
        for (Appointment appointment : appointments) {
            this.appointmentReminderEvent.fireAsync(
                    new AppointmentReminderEvent(appointment.id)
            );
        }
    }
}
