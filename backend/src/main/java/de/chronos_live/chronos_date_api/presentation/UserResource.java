package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.application.UserService;
import de.chronos_live.chronos_date_api.domain.User;
import de.chronos_live.chronos_date_api.dto.PrincipalDto;
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
        User user =
                this.userService.createUser(
                        jwt.getClaim("given_name"),
                        jwt.getClaim("family_name"),
                        jwt.getClaim("email"),
                        jwt.getSubject()
                );
        return Response.ok(mapper.toDto(user)).build();
    }

    @PATCH
    public Response patchUser(@RequestBody PrincipalDto userDto) {
        if (userDto != null) {
            User newUser = this.userService.updateUser(mapper.toEntity(userDto), jwt.getSubject());
            return Response.ok(mapper.toDto(newUser)).build();
        }
        try {
            User user = new User();
            user.setFirstName(jwt.getClaim("given_name"));
            user.setLastName(jwt.getClaim("family_name"));
            user.setEmail(jwt.getClaim("email"));
            user.setOidcId(jwt.getSubject());
            User newUser = this.userService.updateUser(user, jwt.getSubject());
            return Response.ok(mapper.toDto(newUser)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @DELETE
    public Response deleteUser() {
        return Response.status(Response.Status.METHOD_NOT_ALLOWED).build();
    }
}