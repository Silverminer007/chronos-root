package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.application.events.*;
import de.chronos_live.chronos_date_api.domain.Appointment;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

@ApplicationScoped
public class DebugLoggingService {
    public void onAppointmentParticipationStatusConfirmation(@Observes AppointmentParticipationStatusConfirmationEvent event) {
        Log.debugf("[Notifications] onAppointmentParticipationStatusConfirmation Appointment ID %s", event.appointmentId());
    }

    public void onAppointmentParticipationInvalid(@Observes AppointmentParticipationInvalidEvent event) {
        Log.debugf("[Notifications] onAppointmentParticipationInvalid Appointment ID %s", event.appointmentId());
    }

    public void onAppointmentReminder(@Observes AppointmentReminderEvent event) {
        Log.debugf("[Notifications] onAppointmentReminder Appointment ID %s", event.appointmentId());
    }

    public void onAppointmentParticipationStatusChanged(@Observes AppointmentParticipationStatusChangedEvent event) {
        Log.debugf("[Notifications] onAppointmentParticipationStatusChanged Appointment ID %s", event.appointmentId());
    }

    public void onAppointmentParticipationPendingReminder(@Observes AppointmentParticipationStatusPendingReminderEvent event) {
        Appointment appointment = Appointment.findById(event.appointmentId());

        Instant targetTime = appointment.getStartTime();
        if(appointment.getStartTime().until(appointment.getEndTime(), ChronoUnit.HOURS) >= 24) {
            targetTime = targetTime.minusSeconds(60 * 60 * 24 * 7 * 8);
        } else if (isWeekdayAtUTC(appointment.getStartTime())) {
            targetTime = targetTime.minusSeconds(60 * 60 * 24 * 7 * 2);
        } else {
            targetTime = targetTime.minusSeconds(60 * 60 * 24 * 7);
        }

        long minutesUntilAppointment = Instant.now().until(targetTime, ChronoUnit.MINUTES);
        double base = Math.log(minutesUntilAppointment) / Math.log(2d);
        boolean isBaseInt = notNaNOrInfinity(base) && (base % 1) == 0;

        Log.debugf("[Notifications] onAppointmentParticipationPendingReminder " +
                "Appointment ID [%s] " +
                "Target Feedback Time [%s] " +
                "Minutes Until Target Time [%d] " +
                "Power of two [%b]",
                event.appointmentId(),
                targetTime.toString(),
                minutesUntilAppointment,
                isBaseInt);

    }

    private boolean notNaNOrInfinity(double d) {
        return !(Double.isNaN(d) || Double.isInfinite(d));
    }

    private boolean isWeekdayAtUTC(Instant instant) {
        List<DayOfWeek> weekdays = List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY);
        return weekdays.contains(instant.atZone(ZoneOffset.UTC).getDayOfWeek());
    }
}