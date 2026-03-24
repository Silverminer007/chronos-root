package de.chronos_live.chronos_date_api.application.ports;

public interface ReminderEventPort {
    void sendReminder(long appointmentId);
    void sendRSVPReminder(long appointmentId);
}
