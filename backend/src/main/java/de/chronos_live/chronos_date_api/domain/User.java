package de.chronos_live.chronos_date_api.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class User extends PanacheEntity {
    private String firstName;
    private String lastName;
    private String email;
    private String oidcId;

    public String getName() {
        return firstName + " " + lastName;
    }
}