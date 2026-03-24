package de.chronos_live.chronos_date_api.application.reminder;

import de.chronos_live.chronos_date_api.application.ports.ReminderEventPort;
import de.chronos_live.chronos_date_api.domain.Appointment;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class ReminderRuleEngine {

    private final List<ReminderRule> rules;
    private final ReminderEventPort eventPort;

    public ReminderRuleEngine(List<ReminderRule> rules,
                              ReminderEventPort eventPort) {
        this.rules = rules;
        this.eventPort = eventPort;
    }

    public void evaluate(List<Appointment> appointments, Instant now) {

        for (Appointment appointment : appointments) {

            ReminderRule rule = rules.stream()
                    .filter(r -> r.appliesTo(appointment))
                    .findFirst()
                    .orElse(null);

            if (rule == null) continue;

            for (Instant trigger : rule.computeTriggerTimes(appointment)) {

                if (isDue(now, trigger)) {
                    rule.execute(appointment, this.eventPort);
                    break; // nur 1 Reminder pro Lauf
                }
            }
        }
    }

    private boolean isDue(Instant now, Instant trigger) {
        long diff = Math.abs(Duration.between(now, trigger).toMinutes());
        return diff <= 15; // dein Scheduler-Fenster
    }
}
