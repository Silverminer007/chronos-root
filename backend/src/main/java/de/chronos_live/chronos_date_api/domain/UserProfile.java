package de.chronos_live.chronos_date_api.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/**
 * Local cache of Keycloak user identity data.
 * Written through on each authenticated request; used for all profile reads.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "user_profiles")
public class UserProfile extends PanacheEntityBase {

    @Id
    @Column(name = "oidc_id", nullable = false, length = 255)
    public String oidcId;

    @Column(name = "first_name", length = 255)
    public String firstName;

    @Column(name = "last_name", length = 255)
    public String lastName;

    @Column(length = 255)
    public String email;

    @Column(name = "profile_picture_url", length = 512)
    public String profilePictureUrl;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
