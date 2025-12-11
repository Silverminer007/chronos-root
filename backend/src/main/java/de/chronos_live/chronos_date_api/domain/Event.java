package de.chronos_live.chronos_date_api.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

@EqualsAndHashCode(callSuper = true)
@Data
@Entity
public class Event extends PanacheEntity {
    private String name, description, venue;
    private Instant startTime, endTime;
    private EventStatus eventStatus;
    private Integer minimalAttendees;

    private Instant lastUpdate, createdAt;

    public Event(Event event) {
        this.name = event.name;
        this.description = event.description;
        this.venue = event.venue;
        this.startTime = event.startTime;
        this.endTime = event.endTime;
        this.eventStatus = event.eventStatus;
        this.minimalAttendees = event.minimalAttendees;
        this.lastUpdate = Instant.now();
        this.createdAt = Instant.now();
    }

    public Event(){}
}
