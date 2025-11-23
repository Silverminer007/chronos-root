package de.chronos_live.chronos_date_api.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class Message extends PanacheEntity {
    private String title;
    private String message;
    private Event event;
    private User sender;
    private LocalDateTime timeStamp;
}
