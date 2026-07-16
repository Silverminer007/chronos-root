package de.chronos_live.admin.presentation;

import de.chronos_live.admin.dto.AdminCreateGroupDto;
import de.chronos_live.chronos_date_api.application.GroupService;
import de.chronos_live.chronos_date_api.domain.Group;
import de.chronos_live.chronos_date_api.dto.GroupDto;
import de.chronos_live.chronos_date_api.exception.ValidationException;
import io.micrometer.core.annotation.Timed;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN_API")
@Path("/api/v2/admin/groups")
@Timed("api.admin.groups")
public class AdminGroupResource {
    @Inject
    GroupService groupService;

    @POST
    @Path("/")
    public Response createGroup(@RequestBody AdminCreateGroupDto dto) {
        if (dto == null || dto.getOwnerId() == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        GroupDto groupDto = new GroupDto();
        groupDto.setName(dto.getGroupName());
        Group createdGroup = groupService.createGroup(dto.getOwnerId(), groupDto);
        return Response.status(Response.Status.CREATED).entity(createdGroup.id).build();
    }

    @POST
    @Path("/{groupId}/users/{userId}")
    public Response addGroupMember(@PathParam("groupId") Long groupId,
                                   @PathParam("userId") String targetUserOidcId) {
        if (groupId == null || targetUserOidcId == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        try {
            groupService.addGroupMember(null, groupId, targetUserOidcId);
        } catch (ValidationException ignored) {}
        return Response.ok().build();
    }
}
