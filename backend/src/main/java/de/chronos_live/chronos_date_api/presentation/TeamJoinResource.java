package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.application.TeamInviteService;
import de.chronos_live.chronos_date_api.dto.InvitePreviewDto;
import de.chronos_live.chronos_date_api.security.PrincipalContext;
import io.micrometer.core.annotation.Timed;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/v2/invite")
@PermitAll
@Produces(MediaType.APPLICATION_JSON)
@Timed("api.team_join")
public class TeamJoinResource {
    @Inject
    TeamInviteService inviteService;
    @Inject
    PrincipalContext principalContext;

    @GET
    @Path("/{token}")
    public InvitePreviewDto previewInvite(@PathParam("token") String token) {
        if (principalContext.getPrincipal() == null) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        }
        return inviteService.getInvitePreview(token);
    }

    @POST
    @Path("/{token}")
    public Response redeemInvite(@PathParam("token") String token) {
        String oidcId = principalContext.getPrincipal().oidcId();
        return inviteService.redeemInvite(oidcId, token);
    }
}
