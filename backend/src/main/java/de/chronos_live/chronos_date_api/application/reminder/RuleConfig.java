package de.chronos_live.chronos_date_api.application.reminder;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.util.List;

@ApplicationScoped
public class RuleConfig {

    @Produces
    public List<ReminderRule> rules(
            LongAppointmentRSVPRule longRule,
            ShortWeekdayRSVPRule weekdayRule,
            ShortWeekendRSVPRule weekendRule,
            AppointmentReminderRule appointmentReminderRule
    ) {
        return List.of(longRule, weekdayRule, weekendRule, appointmentReminderRule);
    }
}
