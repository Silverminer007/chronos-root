package de.chronos_live.chronos_date_api.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class EventUserAttendees extends PanacheEntity {
    private User user;
    private Event event;
    private EventAttendeeRole role;
}
