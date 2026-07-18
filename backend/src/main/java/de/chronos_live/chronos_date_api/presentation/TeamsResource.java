package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.application.TeamService;
import de.chronos_live.chronos_date_api.dto.CreateTeamDto;
import de.chronos_live.chronos_date_api.dto.TeamDto;
import de.chronos_live.chronos_date_api.dto.UpdateMemberRoleDto;
import de.chronos_live.chronos_date_api.security.PrincipalContext;
import io.micrometer.core.annotation.Timed;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

import java.util.List;

@Path("/api/v2/teams")
@PermitAll
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Timed("api.teams")
public class TeamsResource {
    @Inject
    TeamService teamService;
    @Inject
    PrincipalContext principalContext;

    @GET
    @Path("/")
    public Response getTeams() {
        String oidcId = principalContext.getPrincipal().oidcId();
        List<TeamDto> teams = teamService.listTeamsForUser(oidcId);
        return Response.ok(teams).build();
    }

    @POST
    @Path("/")
    public Response postTeam(@RequestBody CreateTeamDto dto) {
        String oidcId = principalContext.getPrincipal().oidcId();
        TeamDto created = teamService.createTeam(oidcId, dto.getName());
        return Response.ok(created).build();
    }

    @GET
    @Path("/{teamId}")
    public Response getTeam(@PathParam("teamId") Long teamId) {
        String oidcId = principalContext.getPrincipal().oidcId();
        return Response.ok(teamService.getTeam(oidcId, teamId)).build();
    }

    @PATCH
    @Path("/{teamId}")
    public Response renameTeam(
            @PathParam("teamId") Long teamId,
            @RequestBody CreateTeamDto dto) {
        String oidcId = principalContext.getPrincipal().oidcId();
        teamService.renameTeam(oidcId, teamId, dto.getName());
        return Response.noContent().build();
    }

    @PUT
    @Path("/{teamId}/members/{targetOidcId}/role")
    public Response updateMemberRole(
            @PathParam("teamId") Long teamId,
            @PathParam("targetOidcId") String targetOidcId,
            @RequestBody UpdateMemberRoleDto dto) {
        String oidcId = principalContext.getPrincipal().oidcId();
        teamService.updateMemberRole(oidcId, teamId, targetOidcId, dto.getRole());
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{teamId}/members/{targetOidcId}")
    public Response removeMember(
            @PathParam("teamId") Long teamId,
            @PathParam("targetOidcId") String targetOidcId) {
        String oidcId = principalContext.getPrincipal().oidcId();
        teamService.removeMember(oidcId, teamId, targetOidcId);
        return Response.noContent().build();
    }

    @POST
    @Path("/{teamId}/members/{targetOidcId}/transfer-ownership")
    public Response transferOwnership(
            @PathParam("teamId") Long teamId,
            @PathParam("targetOidcId") String targetOidcId) {
        String oidcId = principalContext.getPrincipal().oidcId();
        teamService.transferOwnership(oidcId, teamId, targetOidcId);
        return Response.noContent().build();
    }
}
