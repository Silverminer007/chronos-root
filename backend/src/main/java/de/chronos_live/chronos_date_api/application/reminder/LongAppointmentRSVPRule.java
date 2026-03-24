package de.chronos_live.chronos_date_api.application.reminder;

import de.chronos_live.chronos_date_api.application.ports.ReminderEventPort;
import de.chronos_live.chronos_date_api.application.utils.AppointmentTimeUtil;
import de.chronos_live.chronos_date_api.domain.Appointment;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class LongAppointmentRSVPRule implements ReminderRule {
    @Override
    public boolean appliesTo(Appointment a) {
        return AppointmentTimeUtil.durationHours(a) >= 24;
    }

    @Override
    public List<Instant> computeTriggerTimes(Appointment a) {
        Instant start = a.getStartTime();

        List<Instant> triggers = new ArrayList<>();

        // fixe Erinnerungen
        triggers.add(start.minus(16 * 7, ChronoUnit.DAYS));
        triggers.add(start.minus(8 * 7, ChronoUnit.DAYS));
        triggers.add(start.minus(4 * 7, ChronoUnit.DAYS));
        triggers.add(start.minus(2 * 7, ChronoUnit.DAYS));

        // danach täglich zur Startzeit
        Instant dailyStart = start.minus(2 * 7, ChronoUnit.DAYS);

        Instant cursor = dailyStart;
        while (cursor.isBefore(start)) {
            triggers.add(cursor);
            cursor = cursor.plus(1, ChronoUnit.DAYS);
        }

        return triggers;
    }

    @Override
    public void execute(Appointment appointment, ReminderEventPort reminderEventPort) {
        reminderEventPort.sendRSVPReminder(appointment.id);
    }
}
