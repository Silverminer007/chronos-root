package de.chronos_live.chronos_date_api.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@Entity
public class PushSubscription extends PanacheEntity {

    @Column(nullable = false)
    private String endpoint;

    @Column(nullable = false)
    private String auth;

    @Column(nullable = false)
    private String p256dh;

    @ManyToOne(optional = false)
    private User user; // deine User-Entity
}