package de.chronos_live.chronos_date_api.application.reminder;

import de.chronos_live.chronos_date_api.application.ports.ReminderEventPort;
import de.chronos_live.chronos_date_api.domain.Appointment;

import java.time.Instant;
import java.util.List;

public interface ReminderRule {
    boolean appliesTo(Appointment appointment);
    List<Instant> computeTriggerTimes(Appointment appointment);
    void execute(Appointment appointment, ReminderEventPort reminderEventPort);
}
