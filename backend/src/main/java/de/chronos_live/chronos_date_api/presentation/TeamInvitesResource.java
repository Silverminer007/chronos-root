package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.application.TeamInviteService;
import de.chronos_live.chronos_date_api.dto.CreateInviteDto;
import de.chronos_live.chronos_date_api.dto.TeamInviteDto;
import de.chronos_live.chronos_date_api.security.PrincipalContext;
import io.micrometer.core.annotation.Timed;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

import java.util.List;

@Path("/api/v2/teams/{teamId}/invites")
@PermitAll
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Timed("api.team_invites")
public class TeamInvitesResource {
    @Inject
    TeamInviteService inviteService;
    @Inject
    PrincipalContext principalContext;

    @GET
    public Response getInvites(@PathParam("teamId") Long teamId) {
        String oidcId = principalContext.getPrincipal().oidcId();
        List<TeamInviteDto> invites = inviteService.listInvites(oidcId, teamId);
        return Response.ok(invites).build();
    }

    @POST
    public Response createInvite(@PathParam("teamId") Long teamId, @RequestBody CreateInviteDto dto) {
        String oidcId = principalContext.getPrincipal().oidcId();
        TeamInviteDto created = inviteService.createInvite(oidcId, teamId, dto);
        return Response.ok(created).build();
    }

    @DELETE
    @Path("/{inviteId}")
    public Response revokeInvite(@PathParam("teamId") Long teamId, @PathParam("inviteId") Long inviteId) {
        String oidcId = principalContext.getPrincipal().oidcId();
        inviteService.revokeInvite(oidcId, teamId, inviteId);
        return Response.noContent().build();
    }
}
