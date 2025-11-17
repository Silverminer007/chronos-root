package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.Attendance;
import de.chronos_live.chronos_date_api.domain.AttendanceStatus;
import de.chronos_live.chronos_date_api.domain.Event;
import de.chronos_live.chronos_date_api.domain.User;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@ApplicationScoped
public class AttendanceStatusService {
    private final MessageService messageService;

    public AttendanceStatusService(MessageService messageService) {
        this.messageService = messageService;
    }

    public Attendance getAttendanceStatus(User user, Event event) {
        return (Attendance) Attendance.find("user AND event", user, event).firstResultOptional().orElseGet(() -> {
            Attendance attendance = new Attendance();
            attendance.setUser(user);
            attendance.setEvent(event);
            attendance.setStatus(AttendanceStatus.PENDING);
            attendance.persist();
            return attendance;
        });
    }

    public List<Attendance> getAttendanceStatus(Event event) {
        return Attendance.find("event", event).list();
    }

    public void setAttendanceStatus(User user, Event event, AttendanceStatus status) {
        Attendance attendance = this.getAttendanceStatus(user, event);

        if (!event.getStart().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Attendance status cannot be updated after start date");
        }

        if (attendance.getStatus() == status) {
            return;
        }
        attendance.setStatus(status);
        this.messageService.sendMessage(event,
                String.format("%s: %s zu %s", status.toString(), user.getName(), event.getName()),
                String.format("%s: %s zu %s am %s", status, user.getName(), event.getName(),
                        event.getStart().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))),
                user);
    }
}
