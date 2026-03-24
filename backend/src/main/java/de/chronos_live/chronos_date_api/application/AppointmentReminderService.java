package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.application.reminder.ReminderRuleEngine;
import de.chronos_live.chronos_date_api.domain.Appointment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@ApplicationScoped
public class AppointmentReminderService {
    @Inject
    AppointmentQueryService queryService;
    @Inject
    ReminderRuleEngine engine;
    @Inject
    Clock clock;

    public void sendPendingReminders() {
        Instant now = clock.instant();

        List<Appointment> appointments =
                queryService.getNonCancelledAppointmentsStartingBetween(now, now.plus(20 * 7, ChronoUnit.DAYS));

        engine.evaluate(appointments, now);
    }
}
