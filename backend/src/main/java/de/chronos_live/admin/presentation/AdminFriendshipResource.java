package de.chronos_live.admin.presentation;

import de.chronos_live.admin.application.AdminFriendshipService;
import de.chronos_live.admin.dto.FriendGroupDto;
import io.micrometer.core.annotation.Timed;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN_API")
@Path("/api/v2/admin/friendship")
@Timed("api.admin.friendships")
public class AdminFriendshipResource {
    @Inject
    AdminFriendshipService adminFriendshipService;

    @POST
    @Path("/befriend")
    public Response befriend(@RequestBody FriendGroupDto dto) {
        if (dto == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        this.adminFriendshipService.befriend(dto.getUserIds());
        return Response.ok().build();
    }
}
