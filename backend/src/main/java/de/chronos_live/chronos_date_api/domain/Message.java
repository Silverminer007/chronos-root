package de.chronos_live.chronos_date_api.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
public class Message extends PanacheEntity {
    private String title;
    private String message;
    @ManyToOne
    private Event event;
    @ManyToOne
    private User sender;
    private LocalDateTime timeStamp;
}
