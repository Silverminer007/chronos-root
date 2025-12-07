package de.chronos_live.chronos_date_api.presentation;

import java.util.ArrayList;
import java.util.List;

public record EventDto(Long id, String name, String description, String start, String end, String venue,
                       String status, Integer minimalAttendees, String own_attendance_status, List<AttendanceDto> attendances) {
    public EventDto(Long id, String name, String description, String start, String end, String venue,
                    String status, Integer minimalAttendees) {
        this(id, name, description, start, end, venue, status, minimalAttendees, null, new ArrayList<>());
    }

    EventDto withOwnAttendanceStatus(String own_attendance_status) {
        return new EventDto(id(), name(), description(), start(), end(), venue(), status(), minimalAttendees(), own_attendance_status, attendances());
    }

    EventDto withAttendances(List<AttendanceDto> attendances) {
        return new EventDto(id(), name(), description(), start(), end(), venue(), status(), minimalAttendees(), own_attendance_status(), attendances);
    }
}
