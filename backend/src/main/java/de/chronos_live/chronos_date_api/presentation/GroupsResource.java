package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.application.GroupService;
import de.chronos_live.chronos_date_api.application.UserService;
import de.chronos_live.chronos_date_api.domain.Group;
import de.chronos_live.chronos_date_api.domain.User;
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
import java.util.Objects;
import java.util.Set;

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
    JsonWebToken jwt;

    @Inject
    GroupMapper groupMapper;
    @Inject
    UserMapper userMapper;

    @GET
    @Path("/")
    public Response getGroups(@QueryParam("members") Boolean members, @QueryParam("search") String search) {
        User user = this.userService.getUser(jwt.getSubject());
        List<Group> groups;
        if(search == null) {
            groups = this.groupService.getGroups(user);
        } else {
            groups = this.groupService.searchGroups(user, search);
        }

        if (members == null || !members) {
            return Response.ok(groupMapper.toDtoListWithOwner(groups, user.id)).build();
        }
        return Response.ok(groupMapper.toDtoWithMembersListWithOwner(groups, user.id)).build();
    }

    @POST
    @Path("/")
    public Response postGroup(@RequestBody GroupDto groupDto) {
        User user = this.userService.getUser(jwt.getSubject());

        try {
            Group group = new Group();
            group.setGroupName(groupDto.name());
            this.groupService.createGroup(user, group);
            return Response.ok(groupMapper.toDtoWithOwner(group, user.id)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @DELETE
    @Path("/{group}")
    public Response deleteGroup(@PathParam("group") Long groupId) {
        User user = this.userService.getUser(jwt.getSubject());
        try {
            this.groupService.deleteGroup(user, groupId);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
        return Response.noContent().build();
    }

    @PATCH
    @Path("/{group}")
    public Response patchGroup(@PathParam("group") Long groupId, @RequestBody GroupDto groupDto) {
        if (groupDto.id() != null && !Objects.equals(groupId, groupDto.id())) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        User user = this.userService.getUser(jwt.getSubject());
        try {
            this.groupService.editGroupName(user, groupId, groupDto.name());
            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("/{group}/users")
    public Response postUsers(@PathParam("group") Long groupId, @RequestBody UserDto userDto) {
        User user = this.userService.getUser(jwt.getSubject());

        User groupUser = userMapper.toEntity(userDto);
        try {
            this.groupService.addGroupMember(user, groupId, groupUser);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
        return Response.noContent().build();
    }

    @GET
    @Path("/{group}/users")
    public Response getUsers(@PathParam("group") Long groupId) {
        User user = this.userService.getUser(jwt.getSubject());

        try {
            Set<User> users = this.groupService.getGroupUsers(user, groupId);
            return Response.ok(userMapper.toDtoList(users.stream().toList())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @DELETE
    @Path("/{group}/users")
    public Response deleteUsers(@PathParam("group") Long groupId, @RequestBody UserDto userDto) {
        User user = this.userService.getUser(jwt.getSubject());

        User groupUser = userMapper.toEntity(userDto);
        try {
            this.groupService.removeGroupMember(user, groupId, groupUser);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
        return Response.noContent().build();
    }
}