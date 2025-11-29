package de.chronos_live.chronos_date_api.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
public class EventGroupAttendees extends PanacheEntity {
    @ManyToOne
    private Group group;
    @ManyToOne
    private Event event;
    private EventAttendeeRole role;
}
