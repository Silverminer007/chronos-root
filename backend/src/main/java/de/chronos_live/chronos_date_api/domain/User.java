package de.chronos_live.chronos_date_api.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "users")
public class User extends PanacheEntity {
    private String firstName;
    private String lastName;
    private String email;
    private String oidcId;
    private String profilePictureUrl;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "last_update")
    private Instant lastUpdate;
    @Column(name = "last_seen")
    private Instant lastSeen;

    public String getName() {
        return firstName + " " + lastName;
    }
}