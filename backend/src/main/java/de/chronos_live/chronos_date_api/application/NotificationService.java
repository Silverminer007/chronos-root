package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.scheduler.Scheduled;
import jakarta.transaction.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
@Transactional
public class NotificationService {
    @Inject
    AttendanceStatusService attendanceStatusService;

    @Inject
    EventAccessService eventAccessService;

    @Inject
    WebPushService webPushService;

    @Inject
    EventService eventService;

    @Scheduled(cron = "0 */1 * * * ?")
    void sendEventReminders() {
        long minutesUntilStart = 30L;
        Instant in30Minutes = Instant.now().plusSeconds(60L * minutesUntilStart);

        List<Event> events = this.eventService.findEventsStartingAt(in30Minutes);
        for (Event event : events) {
            Set<User> attendees = eventAccessService.getAttendees(event.id);
            this.webPushService.sendEventReminderNotification(event, attendees);
        }
    }

    @Scheduled(cron = "0 */15 * * * ?")
    void sendEventAttendanceStatusChecks() {
        // Wenn mehr als 24h -> 1 Monat vorher
        // Wenn Mo - Do -> gar nicht
        // Wenn Fr - So -> 1 Woche vorher
        Instant in30Days = Instant.now().plusSeconds(60 * 60 * 24 * 30);
        Instant in30DaysAnd14Minutes = in30Days.plusSeconds(60 * 14);
        List<Event> longEvents = this.eventService.findEventsStartingBetween(in30Days, in30DaysAnd14Minutes);
        for (Event event : longEvents) {
            if (event.getStartTime().until(event.getEndTime(), ChronoUnit.HOURS) < 24) {
                continue;
            }
            this.webPushService.sendAttendanceStatusRecheckNotification(event, this.attendanceStatusService.getAttendanceStatus(event.id));
        }

        Instant in7Days = Instant.now().plusSeconds(60 * 60 * 24 * 7);
        Instant in7DaysAnd14Minutes = in7Days.plusSeconds(60 * 14);
        List<Event> weekendEvents = this.eventService.findEventsStartingBetween(in7Days, in7DaysAnd14Minutes);
        for (Event event : weekendEvents) {
            List<DayOfWeek> weekdays = List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY);
            if (weekdays.contains(event.getStartTime().atZone(ZoneOffset.UTC).getDayOfWeek())) {
                continue;
            }
            this.webPushService.sendAttendanceStatusRecheckNotification(event, this.attendanceStatusService.getAttendanceStatus(event.id));
        }
    }

    @Scheduled(cron = "0 */15 * * * ?")
    void sendEventAttendanceStatusPendingReminder() {
        // Wenn mehr als 24h -> 2 Monate vorher
        // Wenn Mo - Do -> 1 Woche vorher
        // Wenn Fr - So -> 2 Wochen vorher
        Instant now = Instant.now();
        Instant in60Days = now.plusSeconds(60 * 60 * 24 * 60);
        List<Event> longEvents = this.eventService.findEventsStartingBetween(now, in60Days);
        for (Event event : longEvents) {
            if (event.getStartTime().until(event.getEndTime(), ChronoUnit.HOURS) < 24) {
                continue;
            }
            long quarterHours = ChronoUnit.MINUTES.between(event.getStartTime(), event.getEndTime()) / 15;
            // 60 Tage -> 5760
            // 30 Tage -> 2880
            // 15 Tage -> 1440
            // 7 Tage 12 h -> 720
            // 3 Tage 18 h -> 360
            // 1 Tag 21 h -> 180
            // 22 h 30 min -> 90
            // 11 h 15 min -> 45
            // 5 h 30 min -> 22
            // 2 h 45 min -> 11
            // 1 h 15 min -> 5
            // 30 min -> 2
            // 15 min -> 1
            List<Long> pendingReminderTimes = List.of(5760L, 2880L, 1440L, 720L, 360L, 180L, 90L, 45L, 22L, 11L, 5L, 2L, 1L);
            if (!pendingReminderTimes.contains(quarterHours)) {
                continue;
            }
            this.webPushService.sendAttendanceStatusPendingReminderNotification(event,
                    this.attendanceStatusService.getAttendanceStatus(event.id)
                            .stream()
                            .filter(attendance ->
                                    attendance.getStatus().equals(AttendanceStatus.PENDING))
                            .map(Attendance::getUser)
                            .collect(Collectors.toSet())
            );
        }

        Instant in7Days = Instant.now().plusSeconds(60 * 60 * 24 * 7);
        Instant in7DaysAnd14Minutes = in7Days.plusSeconds(60 * 14);
        List<Event> weekdayEvents = this.eventService.findEventsStartingBetween(in7Days, in7DaysAnd14Minutes);
        for (Event event : weekdayEvents) {
            List<DayOfWeek> weekdays = List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY);
            if (!weekdays.contains(event.getStartTime().atZone(ZoneOffset.UTC).getDayOfWeek())) {
                continue;
            }
            long quarterHours = ChronoUnit.MINUTES.between(event.getStartTime(), event.getEndTime()) / 15;
            // 7 Tage 12 h -> 720
            // 3 Tage 18 h -> 360
            // 1 Tag 21 h -> 180
            // 22 h 30 min -> 90
            // 11 h 15 min -> 45
            // 5 h 30 min -> 22
            // 2 h 45 min -> 11
            // 1 h 15 min -> 5
            // 30 min -> 2
            // 15 min -> 1
            List<Long> pendingReminderTimes = List.of(720L, 360L, 180L, 90L, 45L, 22L, 11L, 5L, 2L, 1L);
            if (!pendingReminderTimes.contains(quarterHours)) {
                continue;
            }
            this.webPushService.sendAttendanceStatusPendingReminderNotification(event,
                    this.attendanceStatusService.getAttendanceStatus(event.id)
                            .stream()
                            .filter(attendance ->
                                    attendance.getStatus().equals(AttendanceStatus.PENDING))
                            .map(Attendance::getUser)
                            .collect(Collectors.toSet())
            );
        }

        Instant in14Days = Instant.now().plusSeconds(60 * 60 * 24 * 14);
        Instant in14DaysAnd14Minutes = in14Days.plusSeconds(60 * 14);
        List<Event> weekendEvents = this.eventService.findEventsStartingBetween(in14Days, in14DaysAnd14Minutes);
        for (Event event : weekendEvents) {
            List<DayOfWeek> weekdays = List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY);
            if (weekdays.contains(event.getStartTime().atZone(ZoneOffset.UTC).getDayOfWeek())) {
                continue;
            }
            long quarterHours = ChronoUnit.MINUTES.between(event.getStartTime(), event.getEndTime()) / 15;
            // 14 Tage -> 1440
            // 7 Tage 12 h -> 720
            // 3 Tage 18 h -> 360
            // 1 Tag 21 h -> 180
            // 22 h 30 min -> 90
            // 11 h 15 min -> 45
            // 5 h 30 min -> 22
            // 2 h 45 min -> 11
            // 1 h 15 min -> 5
            // 30 min -> 2
            // 15 min -> 1
            List<Long> pendingReminderTimes = List.of(1440L, 720L, 360L, 180L, 90L, 45L, 22L, 11L, 5L, 2L, 1L);
            if (!pendingReminderTimes.contains(quarterHours)) {
                continue;
            }
            this.webPushService.sendAttendanceStatusPendingReminderNotification(event,
                    this.attendanceStatusService.getAttendanceStatus(event.id)
                            .stream()
                            .filter(attendance ->
                                    attendance.getStatus().equals(AttendanceStatus.PENDING))
                            .map(Attendance::getUser)
                            .collect(Collectors.toSet())
            );
        }
    }

    @Scheduled(cron = "0 */15 * * * ?")
    void sendNonMinimalAttendeesAlerts() {
        // Sobald PENDING + APPROVED < REQUIRED
        // Wenn mehr als 24h -> 3 Wochen vorher
        // Wenn Mo - DO -> 3 Tage vorher
        // Wenn Fr - So -> 5 Tage vorher
        Instant in21Days = Instant.now().plusSeconds(60 * 60 * 24 * 21);
        Instant in21DaysAnd14Minutes = in21Days.plusSeconds(60 * 14);
        List<Event> longEvents = this.eventService.findEventsStartingBetween(in21Days, in21DaysAnd14Minutes);
        for (Event event : longEvents) {
            if (event.getStartTime().until(event.getEndTime(), ChronoUnit.HOURS) < 24) {
                continue;
            }
            this.checkForEnoughAttendees(event);
        }

        Instant in3Days = Instant.now().plusSeconds(60 * 60 * 24 * 14);
        Instant in3DaysAnd14Minutes = in3Days.plusSeconds(60 * 14);
        List<Event> weekdayEvents = this.eventService.findEventsStartingBetween(in3Days, in3DaysAnd14Minutes);
        for (Event event : weekdayEvents) {
            List<DayOfWeek> weekdays = List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY);
            if (!weekdays.contains(event.getStartTime().atZone(ZoneOffset.UTC).getDayOfWeek())) {
                continue;
            }
            this.checkForEnoughAttendees(event);
        }

        Instant in5Days = Instant.now().plusSeconds(60 * 60 * 24 * 5);
        Instant in5DaysAnd14Minutes = in5Days.plusSeconds(60 * 14);
        List<Event> weekendEvents = this.eventService.findEventsStartingBetween(in5Days, in5DaysAnd14Minutes);
        for (Event event : weekendEvents) {
            List<DayOfWeek> weekdays = List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY);
            if (weekdays.contains(event.getStartTime().atZone(ZoneOffset.UTC).getDayOfWeek())) {
                continue;
            }
            this.checkForEnoughAttendees(event);
        }
    }

    public void checkForEnoughAttendees(Event event) {
        if (event.getEventStatus() != EventStatus.PLANNED) {
            return;
        }
        List<Attendance> attendances = this.attendanceStatusService.getAttendanceStatus(event.id);
        long rejectedAttendees = attendances.stream().filter(a -> a.getStatus() == AttendanceStatus.REJECTED).count();
        if (attendances.size() - rejectedAttendees >= event.getMinimalAttendees()) {
            event.setEventStatus(EventStatus.PLANNED);
            return;
        }

        this.webPushService.sendNotEnoughAttendeesNotification(event, attendances, attendances.stream().map(Attendance::getUser).collect(Collectors.toSet()));
    }
}
