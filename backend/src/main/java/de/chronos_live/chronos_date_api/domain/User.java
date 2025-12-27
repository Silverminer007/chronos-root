package de.chronos_live.chronos_date_api.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

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

    public String getName() {
        return firstName + " " + lastName;
    }
}