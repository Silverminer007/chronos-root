package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.*;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@ApplicationScoped
public class AttendanceStatusService {
    private final MessageService messageService;
    private final NotificationService notificationService;

    public AttendanceStatusService(MessageService messageService, NotificationService notificationService) {
        this.messageService = messageService;
        this.notificationService = notificationService;
    }

    public Attendance getAttendanceStatus(User user, Long eventId) {
        return (Attendance) Attendance.find("user AND event.id", user, eventId).firstResultOptional().orElseGet(() -> {
            Attendance attendance = new Attendance();
            attendance.setUser(user);
            Event event = Event.find("event", eventId).firstResult();
            attendance.setEvent(event);
            attendance.setStatus(AttendanceStatus.PENDING);
            attendance.persist();
            return attendance;
        });
    }

    public List<Attendance> getAttendanceStatus(Long eventId) {
        return Attendance.find("event.id", eventId).list();
    }

    public AttendanceStatus getAttendanceStatus(String stringValue) {
        return AttendanceStatus.valueOf(stringValue);
    }

    public void setAttendanceStatus(User user, Long eventId, AttendanceStatus status) {
        Attendance attendance = this.getAttendanceStatus(user, eventId);

        Event event = Event.find("event", eventId).firstResult();
        if (!event.getStart().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Attendance status cannot be updated after start date");
        }

        if (attendance.getStatus() == status) {
            return;
        }
        attendance.setStatus(status);
        this.messageService.sendMessage(event.id,
                String.format("%s: %s zu %s", status.toString(), user.getName(), event.getName()),
                String.format("%s: %s zu %s am %s", status, user.getName(), event.getName(),
                        event.getStart().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))),
                user,
                NotificationCategory.ATTENDANCE_STATUS_CHANGED);

        this.notificationService.checkForEnoughAttendees(event);
    }
}
