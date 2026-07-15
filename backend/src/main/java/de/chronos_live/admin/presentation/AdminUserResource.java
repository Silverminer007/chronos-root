package de.chronos_live.admin.presentation;

import de.chronos_live.admin.application.AdminUserService;
import de.chronos_live.admin.dto.AdminUserDto;
import de.chronos_live.admin.dto.AdminUserListResponse;
import io.micrometer.core.annotation.Timed;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN_API")
@Path("/api/v2/admin/user")
@Timed("api.admin.users")
public class AdminUserResource {

    @Inject
    AdminUserService adminUserService;

    @GET
    @Path("/")
    public Response listUsers(@QueryParam("page") @DefaultValue("0") int page,
                              @QueryParam("size") @DefaultValue("20") int size,
                              @QueryParam("lastSeenAfter") String lastSeenAfter,
                              @QueryParam("lastSeenBefore") String lastSeenBefore) {
        AdminUserListResponse response = adminUserService.listUsers(page, size, null, null);
        return Response.ok(response).build();
    }

    @GET
    @Path("{id}")
    public Response getUserById(@PathParam("id") String oidcId) {
        try {
            AdminUserDto user = adminUserService.getUserByOidcId(oidcId);
            return Response.ok(user).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }
}
