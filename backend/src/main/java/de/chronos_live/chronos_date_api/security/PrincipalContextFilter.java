package de.chronos_live.chronos_date_api.security;

import de.chronos_live.chronos_date_api.application.UserService;
import de.chronos_live.chronos_date_api.domain.User;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Provider
@Priority(Priorities.AUTHORIZATION + 1)
public class PrincipalContextFilter implements ContainerRequestFilter {

    @Inject
    PrincipalContext principalContext;
    @Inject
    UserService userService;
    @Inject
    JsonWebToken jwt;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        User user = this.userService.getUser(jwt.getSubject());
        this.userService.updateLastSeen(user);
        principalContext.setPrincipal(user);
        String path = requestContext.getUriInfo().getPath();
        if (path.startsWith("api/v2/admin/") || path.startsWith("/api/v2/admin/")) {
            principalContext.setAdminRequest(true);
        }
    }
}
