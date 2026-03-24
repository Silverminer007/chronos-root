package de.chronos_live.chronos_date_api.application.ports;

import de.chronos_live.chronos_date_api.application.events.AppointmentParticipationStatusPendingReminderEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

@ApplicationScoped
public class ReminderEventCDIAdapter implements ReminderEventPort {
    @Inject
    Event<AppointmentParticipationStatusPendingReminderEvent> appointmentParticipationStatusPendingReminderEvent;

    @Override
    public void sendReminder(long appointmentId) {

    }

    @Override
    public void sendRSVPReminder(long appointmentId) {
        appointmentParticipationStatusPendingReminderEvent.fire(
                new AppointmentParticipationStatusPendingReminderEvent(appointmentId)
        );
    }
}
