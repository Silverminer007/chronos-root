package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.application.UserService;
import de.chronos_live.chronos_date_api.domain.User;
import de.chronos_live.chronos_date_api.mapper.PrincipalMapper;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

import java.util.Objects;

@Path("/api/v2/user")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@PermitAll
public class UserResource {
    @Inject
    UserService userService;
    @Inject
    PrincipalMapper mapper;
    @Inject
    JsonWebToken jwt;

    @GET
    public Response getUser() {
        User user = userService.getUser(jwt.getSubject());
        return Response.ok(mapper.toDto(user)).build();
    }

    @POST
    public Response createUser() {
        try {
            User user =
                    this.userService.createUser(
                            jwt.getClaim("given_name"),
                            jwt.getClaim("family_name"),
                            jwt.getClaim("email"),
                            jwt.getSubject()
                    );
            return Response.ok(mapper.toDto(user)).build();
        } catch (NullPointerException e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
    }

    @PATCH
    public Response patchUser(@RequestBody PrincipalDto userDto) {
        User user = userService.getUser(jwt.getSubject());
        if (!Objects.equals(user.id, userDto.id())) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        User newUser = this.userService.updateUser(mapper.toEntity(userDto));
        return Response.ok(mapper.toDto(newUser)).build();
    }

    @DELETE
    public Response deleteUser() {
        return Response.status(Response.Status.METHOD_NOT_ALLOWED).build();
    }
}