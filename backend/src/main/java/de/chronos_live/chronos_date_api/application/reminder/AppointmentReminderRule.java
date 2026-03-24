package de.chronos_live.chronos_date_api.application.reminder;

import de.chronos_live.chronos_date_api.application.ports.ReminderEventPort;
import de.chronos_live.chronos_date_api.domain.Appointment;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@ApplicationScoped
public class AppointmentReminderRule implements ReminderRule {
    @Override
    public boolean appliesTo(Appointment appointment) {
        return true;
    }

    @Override
    public List<Instant> computeTriggerTimes(Appointment appointment) {
        Instant thirtyMinutesUpfront = appointment.getStartTime().minus(30, ChronoUnit.MINUTES);
        return List.of(thirtyMinutesUpfront);
    }

    @Override
    public void execute(Appointment appointment, ReminderEventPort reminderEventPort) {
        reminderEventPort.sendReminder(appointment.id);
    }
}
