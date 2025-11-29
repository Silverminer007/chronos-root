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
        User user = new User();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setOidcId(oidcId);
        user.persist();
        return user;
    }

    public User getUser(String oidcId) {
        return User.find("oidcId = ?1", oidcId).firstResult();
    }

    public User updateUser(User user) {
        User oldUser = User.findById(user.id);
        if(user.getFirstName() != null) {
            oldUser.setFirstName(user.getFirstName());
        }
        if(user.getLastName() != null) {
            oldUser.setLastName(user.getLastName());
        }
        if(user.getEmail() != null) {
            // TODO E-Mail auch in Keycloak updated
            oldUser.setEmail(user.getEmail());
        }
        // Wahrscheinlich eine schlechte Idee das zu ändern, deshalb erstmal auskommentieren
        /*if(user.getOidcId() != null) {
            oldUser.setOidcId(user.getOidcId());
        }*/
        return oldUser;
    }
}
