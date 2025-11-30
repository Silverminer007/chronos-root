package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.Objects;

@ApplicationScoped
@Transactional
public class UserService {
    public User createUser(String firstName, String lastName, String email, String oidcId) {
        Objects.requireNonNull(firstName);
        Objects.requireNonNull(lastName);
        Objects.requireNonNull(email);
        Objects.requireNonNull(oidcId);

        if(User.find("oidcId = ?1", oidcId).firstResultOptional().isPresent()) {
            throw new IllegalArgumentException("This user already exists");
        }

        User user = new User();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setOidcId(oidcId);
        user.persist();
        return user;
    }

    public User getUser(String oidcId) {
        return (User) User.find("oidcId = ?1", oidcId).firstResultOptional().orElseGet(() -> {
            User user = new User();
            user.setOidcId(oidcId);
            user.persist();
            return user;
        });
    }

    public User updateUser(User user) {
        User oldUser = (User) User.find("oidcId = ?1", user.getOidcId()).firstResultOptional()
                .orElseThrow(() -> new IllegalArgumentException("This user does not exist yet"));
        if (user.getFirstName() != null) {
            oldUser.setFirstName(user.getFirstName());
        }
        if (user.getLastName() != null) {
            oldUser.setLastName(user.getLastName());
        }
        if (user.getEmail() != null) {
            // TODO E-Mail auch in Keycloak updated
            oldUser.setEmail(user.getEmail());
        }
        return oldUser;
    }
}
