package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.application.GroupService;
import de.chronos_live.chronos_date_api.domain.Group;
import de.chronos_live.chronos_date_api.domain.UserIdentity;
import de.chronos_live.chronos_date_api.dto.GroupDto;
import de.chronos_live.chronos_date_api.mapper.GroupMapper;
import de.chronos_live.chronos_date_api.mapper.UserMapper;
import de.chronos_live.chronos_date_api.security.PrincipalContext;
import io.micrometer.core.annotation.Timed;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

import java.util.List;

@Path("/api/v2/groups")
@PermitAll
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Timed("api.groups")
public class GroupsResource {
    @Inject
    GroupService groupService;
    @Inject
    GroupQueryService groupQueryService;
    @Inject
    PrincipalContext principalContext;
    @Inject
    GroupMapper groupMapper;
    @Inject
    UserMapper userMapper;

    @GET
    @Path("/")
    public Response getGroups(@QueryParam("search") String search) {
        UserIdentity user = principalContext.getPrincipal();
        List<Group> groups = groupQueryService.searchGroups(user, search);
        return Response.ok(groupMapper.toDtoList(groups)).build();
    }

    @POST
    @Path("/")
    public Response postGroup(@RequestBody GroupDto groupDto) {
        String oidcId = principalContext.getPrincipal().oidcId();
        Group created = groupService.createGroup(oidcId, groupDto);
        return Response.ok(groupMapper.toDto(created)).build();
    }

    @DELETE
    @Path("/{group}")
    public Response deleteGroup(@PathParam("group") Long groupId) {
        String oidcId = principalContext.getPrincipal().oidcId();
        groupService.deleteGroup(oidcId, groupId);
        return Response.noContent().build();
    }

    @PATCH
    @Path("/{group}")
    public Response patchGroup(@PathParam("group") Long groupId, @RequestBody GroupDto groupDto) {
        String oidcId = principalContext.getPrincipal().oidcId();
        Group updated = groupService.editGroup(oidcId, groupId, groupDto);
        updated.setMembers(null);
        updated.setOwnerOidcId(null);
        return Response.ok(groupMapper.toDto(updated)).build();
    }

    @GET
    @Path("/{group}/users")
    public Response getUsers(@PathParam("group") Long groupId) {
        String oidcId = principalContext.getPrincipal().oidcId();
        List<UserIdentity> users = groupService.getGroupUsers(oidcId, groupId);
        return Response.ok(userMapper.toDtoList(users)).build();
    }

    @POST
    @Path("/{groupId}/user/{userId}")
    public Response postUsers(@PathParam("groupId") Long groupId, @PathParam("userId") String targetOidcId) {
        String oidcId = principalContext.getPrincipal().oidcId();
        groupService.addGroupMember(oidcId, groupId, targetOidcId);
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{groupId}/user/{userId}")
    public Response deleteUsers(@PathParam("groupId") Long groupId, @PathParam("userId") String targetOidcId) {
        String oidcId = principalContext.getPrincipal().oidcId();
        groupService.removeGroupMember(oidcId, groupId, targetOidcId);
        return Response.noContent().build();
    }
}
