package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.User;
import de.chronos_live.chronos_date_api.exception.BadRequestException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@ApplicationScoped
@Transactional
public class UserService {
    public User createUser(String firstName, String lastName, String email, String oidcId) {
        try {
            Objects.requireNonNull(firstName);
            Objects.requireNonNull(lastName);
            Objects.requireNonNull(email);
            Objects.requireNonNull(oidcId);
        } catch (NullPointerException e) {
            throw new BadRequestException(e.getMessage());
        }

        User user = new User();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);

        if (User.find("oidcId = ?1", oidcId).firstResultOptional().isPresent()) {
            // Update User if the user already exists
            return this.updateUser(user, oidcId);
        }

        user.setOidcId(oidcId);

        Optional<User> userByEmail = User.find("email = ?1", email).firstResultOptional();
        if (userByEmail.isPresent()) {
            return this.updateUser(user, userByEmail.get().getOidcId());
        }

        // Save the user to database otherwise
        user.setCreatedAt(Instant.now());
        user.setLastUpdate(Instant.now());
        user.persist();
        return user;
    }

    public User getUser(String oidcId) {
        return (User) User.find("oidcId = ?1", oidcId).firstResultOptional().orElseGet(() -> {
            User user = new User();
            user.setOidcId(oidcId);
            user.setCreatedAt(Instant.now());
            user.setLastUpdate(Instant.now());
            user.persist();
            return user;
        });
    }

    public Optional<User> getUser(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return User.find("id = ?1", id).firstResultOptional();
    }

    public User updateUser(User user, String oidcId) {
        User oldUser = (User) User.find("oidcId = ?1", oidcId).firstResultOptional()
                .orElseThrow(() -> new BadRequestException("This user does not exist yet"));
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
        oldUser.setLastUpdate(Instant.now());
        return oldUser;
    }
}
