package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.UserIdentity;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@Timed("service.userQuery")
public class UserQueryService {

    @Inject
    UserService userService;

    public UserIdentity findByOidcId(String oidcId) {
        return userService.getUserByOidcId(oidcId);
    }
}
