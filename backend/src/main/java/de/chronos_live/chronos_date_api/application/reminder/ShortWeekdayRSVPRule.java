package de.chronos_live.chronos_date_api.application.reminder;

import de.chronos_live.chronos_date_api.application.ports.ReminderEventPort;
import de.chronos_live.chronos_date_api.application.utils.AppointmentTimeUtil;
import de.chronos_live.chronos_date_api.domain.Appointment;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@ApplicationScoped
public class ShortWeekdayRSVPRule implements ReminderRule {

    @Override
    public boolean appliesTo(Appointment a) {
        return AppointmentTimeUtil.durationHours(a) < 24
                && AppointmentTimeUtil.isWeekday(a.getStartTime());
    }

    @Override
    public List<Instant> computeTriggerTimes(Appointment a) {
        Instant start = a.getStartTime();

        return List.of(
                start.minus(7, ChronoUnit.DAYS),
                start.minus(4, ChronoUnit.DAYS),
                start.minus(2, ChronoUnit.DAYS),
                start.minus(1, ChronoUnit.DAYS)
        );
    }

    @Override
    public void execute(Appointment appointment, ReminderEventPort reminderEventPort) {
        reminderEventPort.sendRSVPReminder(appointment.id);
    }
}
