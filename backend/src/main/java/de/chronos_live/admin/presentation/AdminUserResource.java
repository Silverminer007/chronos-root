package de.chronos_live.admin.presentation;

import de.chronos_live.admin.application.AdminUserService;
import de.chronos_live.admin.dto.AdminCreateUserDto;
import de.chronos_live.admin.dto.AdminUserListResponse;
import de.chronos_live.chronos_date_api.application.UserService;
import de.chronos_live.chronos_date_api.domain.User;
import de.chronos_live.chronos_date_api.mapper.PrincipalMapper;
import io.micrometer.core.annotation.Timed;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN_API")
@Path("/api/v2/admin/user")
@Timed("api.admin.users")
public class AdminUserResource {
    @Inject
    UserService userService;

    @Inject
    AdminUserService adminUserService;

    @Inject
    PrincipalMapper userMapper;

    @GET
    @Path("/")
    public Response listUsers(@QueryParam("page") @DefaultValue("0") int page,
                              @QueryParam("size") @DefaultValue("20") int size,
                              @QueryParam("lastSeenAfter") String lastSeenAfter,
                              @QueryParam("lastSeenBefore") String lastSeenBefore) {
        Instant after = lastSeenAfter != null ? Instant.parse(lastSeenAfter) : null;
        Instant before = lastSeenBefore != null ? Instant.parse(lastSeenBefore) : null;
        AdminUserListResponse response = adminUserService.listUsers(page, size, after, before);
        return Response.ok(response).build();
    }

    @GET
    @Path("{id}")
    public Response getUserById(@PathParam("id") Long userId) {
        Optional<User> userOptional = this.userService.getUser(userId);
        if (userOptional.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(this.userMapper.toDto(userOptional.get())).build();
    }

    @POST
    @Path("/")
    public Response createUser(@RequestBody AdminCreateUserDto dto) {
        if (dto == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        User createdUser = this.userService.createUser(dto.getFirstName(), dto.getLastName(), dto.getEmail(), dto.getOidcId());
        return Response.ok(this.userMapper.toDto(createdUser)).build();
    }
}