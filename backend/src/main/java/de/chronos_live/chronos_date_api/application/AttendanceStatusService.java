package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@ApplicationScoped
@Transactional
public class AttendanceStatusService {
    private final MessageService messageService;
    private final NotificationService notificationService;
    private final WebPushService webPushService;
    private final EventAccessService eventAccessService;

    public AttendanceStatusService(MessageService messageService, NotificationService notificationService, WebPushService webPushService, EventAccessService eventAccessService) {
        this.messageService = messageService;
        this.notificationService = notificationService;
        this.webPushService = webPushService;
        this.eventAccessService = eventAccessService;
    }

    public Attendance getAttendanceStatus(User user, Long eventId) {
        return (Attendance) Attendance.find("user.id = ?1 AND event.id = ?2", user.id, eventId).firstResultOptional().orElseGet(() -> {
            Attendance attendance = new Attendance();
            attendance.setUser(user);
            Event event = Event.find("id = ?1", eventId).firstResult();
            attendance.setEvent(event);
            attendance.setStatus(AttendanceStatus.PENDING);
            attendance.persist();
            return attendance;
        });
    }

    public List<Attendance> getAttendanceStatus(Long eventId) {
        Set<User> attendees = this.eventAccessService.getAttendees(eventId);
        List<Attendance> attendances = Attendance.find("event.id", eventId).list();
        Event event = Event.findById(eventId);
        if (event == null) {
            return new ArrayList<>();
        }
        return attendees.stream().map(attendee -> {
            for (Attendance a : attendances) {
                if (Objects.equals(a.getUser().id, attendee.id)) {
                    attendances.remove(a);
                    return a;
                }
            }
            Attendance newAttendance = new Attendance();
            newAttendance.setEvent(event);
            newAttendance.setUser(attendee);
            newAttendance.setStatus(AttendanceStatus.PENDING);
            newAttendance.persist();
            return newAttendance;
        }).toList();
    }

    public AttendanceStatus getAttendanceStatus(String stringValue) {
        return AttendanceStatus.valueOf(stringValue);
    }

    public void setAttendanceStatus(User user, Long eventId, AttendanceStatus status) {
        Attendance attendance = this.getAttendanceStatus(user, eventId);

        Event event = Event.find("id", eventId).firstResult();
        if (!event.getStartTime().isAfter(Instant.now())) {
            throw new IllegalArgumentException("Attendance status cannot be updated after start date");
        }

        if (attendance.getStatus() == status) {
            return;
        }
        attendance.setStatus(status);
        this.messageService.sendMessage(event.id,
                "",
                String.format("%s hat zu %s %s", user.getName(), event.getName(), status.equals(AttendanceStatus.APPROVED) ? "zugesagt" : "abgesagt"),
                user);
        this.webPushService.sendAttendanceStatusChangedNotification(event, attendance, this.eventAccessService.getAttendees(eventId));

        this.notificationService.checkForEnoughAttendees(event);
    }
}
