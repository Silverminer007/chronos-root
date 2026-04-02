package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.User;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@Timed("service.userQuery")
public class UserQueryService {

    public User findById(Long id) {
        return User.findById(id);
    }
}
