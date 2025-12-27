package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.application.GroupQueryService;
import de.chronos_live.chronos_date_api.application.GroupService;
import de.chronos_live.chronos_date_api.application.UserService;
import de.chronos_live.chronos_date_api.domain.Group;
import de.chronos_live.chronos_date_api.domain.User;
import de.chronos_live.chronos_date_api.dto.GroupDto;
import de.chronos_live.chronos_date_api.mapper.GroupMapper;
import de.chronos_live.chronos_date_api.mapper.UserMapper;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

import java.util.List;

@Path("/api/v2/groups")
@PermitAll
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GroupsResource {
    @Inject
    UserService userService;
    @Inject
    GroupService groupService;
    @Inject
    GroupQueryService groupQueryService;
    @Inject
    JsonWebToken jwt;

    @Inject
    GroupMapper groupMapper;
    @Inject
    UserMapper userMapper;

    @GET
    @Path("/")
    public Response getGroups(@QueryParam("members") Boolean members, @QueryParam("search") String search) {
        User user = this.userService.getUser(jwt.getSubject());
        List<Group> groups = this.groupQueryService.searchGroups(user, search, members != null && members);
        return Response.ok(groupMapper.toDtoList(groups)).build();
    }

    @POST
    @Path("/")
    public Response postGroup(@RequestBody GroupDto groupDto) {
        User user = this.userService.getUser(jwt.getSubject());
        Group createdGroup = this.groupService.createGroup(user.id, groupDto);
        return Response.ok(groupMapper.toDto(createdGroup)).build();
    }

    @DELETE
    @Path("/{group}")
    public Response deleteGroup(@PathParam("group") Long groupId) {
        User user = this.userService.getUser(jwt.getSubject());
        this.groupService.deleteGroup(user.id, groupId);
        return Response.noContent().build();
    }

    @PATCH
    @Path("/{group}")
    public Response patchGroup(@PathParam("group") Long groupId, @RequestBody GroupDto groupDto) {
        User user = this.userService.getUser(jwt.getSubject());
        Group updatedGroup = this.groupService.editGroup(user.id, groupId, groupDto);
        return Response.ok(this.groupMapper.toDto(updatedGroup)).build();
    }

    @GET
    @Path("/{group}/users")
    public Response getUsers(@PathParam("group") Long groupId) {
        User user = this.userService.getUser(jwt.getSubject());
        List<User> users = this.groupService.getGroupUsers(user.id, groupId);
        return Response.ok(userMapper.toDtoList(users.stream().toList())).build();
    }

    @POST
    @Path("/{groupId}/user/{userId}")
    public Response postUsers(@PathParam("groupId") Long groupId, @PathParam("userId") Long userId) {
        User user = this.userService.getUser(jwt.getSubject());
        this.groupService.addGroupMember(user.id, groupId, userId);
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{groupId}/user/{userId}")
    public Response deleteUsers(@PathParam("groupId") Long groupId, @PathParam("userId") Long userId) {
        User user = this.userService.getUser(jwt.getSubject());
        this.groupService.removeGroupMember(user.id, groupId, userId);
        return Response.noContent().build();
    }
}