package de.chronos_live.chronos_date_api.security;

import de.chronos_live.chronos_date_api.application.ports.IdentityPort;
import de.chronos_live.chronos_date_api.domain.UserIdentity;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

/**
 * Builds a UserIdentity from JWT claims on every authenticated request and
 * upserts it into the local identity cache so that names are always up-to-date.
 */
@Provider
@Priority(Priorities.AUTHORIZATION + 1)
public class PrincipalContextFilter implements ContainerRequestFilter {

    private static final Logger LOGGER = Logger.getLogger(PrincipalContextFilter.class);

    @Inject
    PrincipalContext principalContext;
    @Inject
    JsonWebToken jwt;
    @Inject
    IdentityPort identityPort;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (jwt.getSubject() == null) {
            return;
        }
        UserIdentity identity = new UserIdentity(
                jwt.getSubject(),
                jwt.getClaim("given_name"),
                jwt.getClaim("family_name"),
                jwt.getClaim("email"),
                jwt.getClaim("picture")
        );
        principalContext.setPrincipal(identity);

        // Keep the local cache fresh — runs in its own transaction (REQUIRES_NEW)
        identityPort.upsert(identity);

        String path = requestContext.getUriInfo().getPath();
        if (path.startsWith("api/v2/admin/") || path.startsWith("/api/v2/admin/")) {
            principalContext.setAdminRequest(true);
        }
    }
}
