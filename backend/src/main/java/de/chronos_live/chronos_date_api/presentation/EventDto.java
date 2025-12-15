package de.chronos_live.chronos_date_api.presentation;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
public class EventDto {

    // Pflichtfelder
    private Long id;

    private String name;

    private String description;

    private String start;

    private String end;

    private String venue;

    private String status;

    private Integer minimal_attendees;

    // Optionale Felder
    private String own_attendance_status;
    private List<AttendanceDto> attendances;
    private List<MessageDto> messages;
    private List<EventUserAttendeesDto> userAttendees;
    private List<EventGroupAttendeesDto> groupAttendees;

    /**
     * Konstruktor für Pflichtfelder
     */
    public EventDto(
            Long id,
            String name,
            String description,
            String start,
            String end,
            String venue,
            String status,
            Integer minimal_attendees
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.start = start;
        this.end = end;
        this.venue = venue;
        this.status = status;
        this.minimal_attendees = minimal_attendees;
    }

    /**
     * Vollständiger Konstruktor (optional nutzbar)
     */
    public EventDto(
            Long id,
            String name,
            String description,
            String start,
            String end,
            String venue,
            String status,
            Integer minimal_attendees,
            String own_attendance_status,
            List<AttendanceDto> attendances,
            List<MessageDto> messages,
            List<EventUserAttendeesDto> userAttendees,
            List<EventGroupAttendeesDto> groupAttendees
    ) {
        this(id, name, description, start, end, venue, status, minimal_attendees);
        this.own_attendance_status = own_attendance_status;
        this.attendances = attendances;
        this.messages = messages;
        this.userAttendees = userAttendees;
        this.groupAttendees = groupAttendees;
    }
}