package de.chronos_live.chronos_date_api.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UpdateAppointmentDto {
    private String name;

    private String description;

    private String start;

    private String end;

    private String venue;

    private Integer minimal_attendees;

    /**
     * Konstruktor für Pflichtfelder
     */
    public UpdateAppointmentDto(
            String name,
            String description,
            String start,
            String end,
            String venue,
            Integer minimalAttendees
    ) {
        this.name = name;
        this.description = description;
        this.start = start;
        this.end = end;
        this.venue = venue;
        this.minimal_attendees = minimalAttendees;
    }
}
