package de.chronos_live.chronos_date_api.application.utils;

import de.chronos_live.chronos_date_api.domain.Appointment;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

public class AppointmentTimeUtil {

    public static long durationHours(Appointment a) {
        return a.getStartTime().until(a.getEndTime(), ChronoUnit.HOURS);
    }

    public static boolean isWeekday(Instant instant) {
        DayOfWeek d = instant.atZone(ZoneOffset.UTC).getDayOfWeek();
        return d.getValue() <= 4; // Mo-Do
    }

    public static boolean isWeekend(Instant instant) {
        DayOfWeek d = instant.atZone(ZoneOffset.UTC).getDayOfWeek();
        return d.getValue() >= 5; // Fr-So
    }
}
