package de.chronos_live.chronos_date_api.security;

import de.chronos_live.chronos_date_api.domain.UserIdentity;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Builds a UserIdentity from JWT claims on every request — no database call needed.
 * Keycloak is the authoritative source for firstName, lastName, email, and profilePictureUrl.
 */
@Provider
@Priority(Priorities.AUTHORIZATION + 1)
public class PrincipalContextFilter implements ContainerRequestFilter {

    @Inject
    PrincipalContext principalContext;
    @Inject
    JsonWebToken jwt;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        UserIdentity identity = new UserIdentity(
                jwt.getSubject(),
                jwt.getClaim("given_name"),
                jwt.getClaim("family_name"),
                jwt.getClaim("email"),
                jwt.getClaim("picture")
        );
        principalContext.setPrincipal(identity);

        String path = requestContext.getUriInfo().getPath();
        if (path.startsWith("api/v2/admin/") || path.startsWith("/api/v2/admin/")) {
            principalContext.setAdminRequest(true);
        }
    }
}
