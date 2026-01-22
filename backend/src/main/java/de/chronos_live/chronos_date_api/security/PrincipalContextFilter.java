package de.chronos_live.chronos_date_api.security;

import de.chronos_live.chronos_date_api.application.UserService;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
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
        principalContext.setPrincipal(
                this.userService.getUser(
                        jwt.getSubject()
                )
        );
        Log.info("Requested for user " + principalContext.getPrincipal().getName());
        String path = requestContext.getUriInfo().getPath();
        if (path.startsWith("api/v2/admin/") || path.startsWith("/api/v2/admin/")) {
            principalContext.setAdminRequest(true);
            Log.info("Admin Requested for user " + principalContext.getPrincipal().getName());
        }
    }
}
