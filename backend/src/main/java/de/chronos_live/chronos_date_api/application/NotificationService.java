package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.scheduler.Scheduled;
import jakarta.transaction.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

@ApplicationScoped
@Transactional
public class NotificationService {
    @Inject
    SettingsService settingsService;

    @Inject
    AttendanceStatusService attendanceStatusService;

    @Inject
    EventAccessService eventAccessService;

    @Inject
    WebPushService webPushService;

    public void notify(User user, String title, String message, NotificationCategory notificationCategory) {
        Settings settings = this.settingsService.getOrCreateSettings(user);
        if (notificationCategory == NotificationCategory.EVENT_REMINDER && !settings.isEventRemindersNotifications()) {
            return;
        }
        if (notificationCategory == NotificationCategory.GROUP_MEMBERSHIP && !settings.isGroupMembershipNotifications()) {
            return;
        }
        if (notificationCategory == NotificationCategory.ATTENDANCE_STATUS_CHANGED && !settings.isAttendanceStatusChangedNotifications()) {
            return;
        }
        if (notificationCategory == NotificationCategory.CONTACTS && !settings.isContactsNotifications()) {
            return;
        }
        if (notificationCategory == NotificationCategory.EVENT_CHANGED && !settings.isEventChangedNotifications()) {
            return;
        }
        if (notificationCategory == NotificationCategory.MESSAGE && !settings.isMessagesNotifications()) {
            return;
        }

        this.webPushService.sendToUser(user.id, title, message);
    }

    @Scheduled(cron = "0 */15 * * * ?")
    void runWithCron() {
        // Event Reminder (30 Minuten)
        this.sendEventReminders();
        // Is Attendance Status correct?
        this.sendEventAttendanceStatusChecks();
        // Non-minimal Attendees
        this.sendNonMinimalAttendeesAlerts();
        // Attendance Status Pending
        this.sendEventAttendanceStatusPendingReminder();
    }

    private void sendEventReminders() {
        List<Event> events = Event.find("start AFTER now() AND BEFORE (now() + interval '30 minutes')").list();
        for (Event event : events) {
            String reminderTemplate =
                    """
                            In %s Minuten startet %s
                            %s zusagen, %s absagen und %s fehlende Rückmeldungen
                            """;
            String reminderTitleTemplate = "In %s Minuten startet %s";
            Long minutesUntilStart = LocalDateTime.now().until(event.getStartTime(), ChronoUnit.MINUTES);
            List<Attendance> attendances = this.attendanceStatusService.getAttendanceStatus(event.id);
            long approvedAttendances = attendances.stream().filter(a -> a.getStatus() == AttendanceStatus.APPROVED).count();
            long rejectedAttendances = attendances.stream().filter(a -> a.getStatus() == AttendanceStatus.REJECTED).count();
            long pendingAttendances = attendances.stream().filter(a -> a.getStatus() == AttendanceStatus.PENDING).count();
            String reminderMessage = String.format(reminderTemplate,
                    minutesUntilStart,
                    event.getName(),
                    approvedAttendances,
                    rejectedAttendances,
                    pendingAttendances
            );
            String reminderTitle = String.format(reminderTitleTemplate,
                    minutesUntilStart,
                    event.getName());
            for (User user : eventAccessService.getAttendees(event)) {
                this.notify(user, reminderTitle, reminderMessage, NotificationCategory.EVENT_REMINDER);
            }
        }
    }

    private void sendEventAttendanceStatusChecks() {
        // Wenn mehr als 24h -> 1 Monat vorher
        // Wenn Mo - Do -> gar nicht
        // Wenn Fr - So -> 1 Woche vorher
        List<Event> longEvents = Event.find("start BETWEEN (now() + interval '30 days') AND (now() + interval '30 days 14 minutes')").list();
        for (Event event : longEvents) {
            if (event.getStartTime().until(event.getEndTime(), ChronoUnit.HOURS) < 24) {
                continue;
            }
            this.sendEventAttendanceStatusChecks(event);
        }

        List<Event> weekendEvents = Event.find("start BETWEEN (now() + interval '7 days') AND (now() + interval '7 days 14 minutes')").list();
        for (Event event : weekendEvents) {
            List<DayOfWeek> weekdays = List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY);
            if (weekdays.contains(event.getStartTime().getDayOfWeek())) {
                continue;
            }
            this.sendEventAttendanceStatusChecks(event);
        }
    }

    private void sendEventAttendanceStatusChecks(Event event) {
        List<Attendance> attendances = this.attendanceStatusService.getAttendanceStatus(event.id);
        for (Attendance attendance : attendances) {
            if (attendance.getStatus() == AttendanceStatus.PENDING) {
                continue;
            }
            this.notify(attendance.getUser(),
                    String.format("Du hast %s %s", event.getName(), attendance.getStatus() == AttendanceStatus.APPROVED ? "zugesagt" : "abgesagt"),
                    String.format("Du hast %s %s. Ist das noch aktuell?", event.getName(),
                            attendance.getStatus() == AttendanceStatus.APPROVED ? "zugesagt" : "abgesagt"),
                    NotificationCategory.ATTENDANCE_CHECK);
        }
    }

    private void sendEventAttendanceStatusPendingReminder() {
        // Wenn mehr als 24h -> 2 Monate vorher
        // Wenn Mo - Do -> 1 Woche vorher
        // Wenn Fr - So -> 2 Wochen vorher
        List<Event> longEvents = Event.find("start BETWEEN now() AND (now() + interval '60 days')").list();
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
            this.sendAttendanceStatusPendingReminder(event);
        }

        List<Event> weekdayEvents = Event.find("start BETWEEN (now() + interval '7 days') AND (now() + interval '7 days 14 minutes')").list();
        for (Event event : weekdayEvents) {
            List<DayOfWeek> weekdays = List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY);
            if (!weekdays.contains(event.getStartTime().getDayOfWeek())) {
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
            this.sendAttendanceStatusPendingReminder(event);
        }

        List<Event> weekendEvents = Event.find("start BETWEEN (now() + interval '14 days') AND (now() + interval '14 days 14 minutes')").list();
        for (Event event : weekendEvents) {
            List<DayOfWeek> weekdays = List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY);
            if (weekdays.contains(event.getStartTime().getDayOfWeek())) {
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
            this.sendAttendanceStatusPendingReminder(event);
        }
    }

    private void sendAttendanceStatusPendingReminder(Event event) {
        long acceptances = this.attendanceStatusService.getAttendanceStatus(event.id).stream()
                .filter(attendance -> attendance.getStatus() == AttendanceStatus.APPROVED)
                .count();
        long missingAcceptances = event.getMinimalAttendees() - acceptances;
        for (User user : this.eventAccessService.getAttendees(event)) {
            Attendance attendance = this.attendanceStatusService.getAttendanceStatus(user, event.id);
            if (attendance.getStatus() != AttendanceStatus.PENDING) {
                continue;
            }

            String messageTitle = String.format("Deine Rückmeldung zu %s fehlt", event.getName());
            String messageBody = String.format(
                    """
                            Du hast bisher keine Rückmeldung zu %s gegeben.
                            Bitte gib bald möglichst eine Antwort.
                            %s
                            """,
                    event.getName(),
                    missingAcceptances > 0 ?
                            String.format("Es fehlen noch %s zusagen", missingAcceptances)
                            : ""
            );

            this.notify(user, messageTitle, messageBody, NotificationCategory.ATTENDANCE_CHECK);
        }
    }

    private void sendNonMinimalAttendeesAlerts() {
        // Sobald PENDING + APPROVED < REQUIRED
        // Wenn mehr als 24h -> 3 Wochen vorher
        // Wenn Mo - DO -> 3 Tage vorher
        // Wenn Fr - So -> 5 Tage vorher
        List<Event> longEvents = Event.find("start BETWEEN (now() + interval '21 days') AND (now() + interval '21 days 14 minutes')").list();
        for (Event event : longEvents) {
            if (event.getStartTime().until(event.getEndTime(), ChronoUnit.HOURS) < 24) {
                continue;
            }
            this.checkForEnoughAttendees(event);
        }

        List<Event> weekdayEvents = Event.find("start BETWEEN (now() + interval '3 days') AND (now() + interval '3 days 14 minutes')").list();
        for (Event event : weekdayEvents) {
            List<DayOfWeek> weekdays = List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY);
            if (!weekdays.contains(event.getStartTime().getDayOfWeek())) {
                continue;
            }
            this.checkForEnoughAttendees(event);
        }

        List<Event> weekendEvents = Event.find("start BETWEEN (now() + interval '5 days') AND (now() + interval '5 days 14 minutes')").list();
        for (Event event : weekendEvents) {
            List<DayOfWeek> weekdays = List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY);
            if (weekdays.contains(event.getStartTime().getDayOfWeek())) {
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
        Set<User> attendees = this.eventAccessService.getAttendees(event);
        if (attendees.size() - rejectedAttendees >= event.getMinimalAttendees()) {
            return;
        }
        String messageTitle = String.format("Es sind zu wenig Leitende bei %s", event.getName());
        String messageBody = String.format("""
                Es werden für %s mindestens %s Leitende benötigt.
                Es haben sich aber bereits %s Leitende abgemeldet,
                sodass nicht mehr ausreichend Leitende zur Verfügung stehen.
                """, event.getName(), event.getMinimalAttendees(), rejectedAttendees);
        event.setEventStatus(EventStatus.NOT_ENOUGH_ATTENDEES);

        for (User user : this.eventAccessService.getAttendees(event)) {
            this.notify(user,
                    messageTitle,
                    messageBody,
                    NotificationCategory.ATTENDANCE_ALERT
            );
        }
    }
}
