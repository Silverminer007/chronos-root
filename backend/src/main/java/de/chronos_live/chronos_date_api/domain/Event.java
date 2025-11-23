package de.chronos_live.chronos_date_api.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
public class Event extends PanacheEntity {
    private String name, description, venue;
    private LocalDateTime start, end;
    private EventStatus eventStatus;
    private Integer minimalAttendees;

    private LocalDateTime lastUpdate, createdAt;

    public Event(Event event) {
        this.name = event.name;
        this.description = event.description;
        this.venue = event.venue;
        this.start = event.start;
        this.end = event.end;
        this.eventStatus = event.eventStatus;
        this.minimalAttendees = event.minimalAttendees;
        this.lastUpdate = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
    }
}
