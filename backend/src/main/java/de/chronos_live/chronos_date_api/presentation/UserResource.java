package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.application.UserService;
import de.chronos_live.chronos_date_api.domain.User;
import de.chronos_live.chronos_date_api.dto.LinkedAccountDto;
import de.chronos_live.chronos_date_api.dto.PasskeyDto;
import de.chronos_live.chronos_date_api.dto.PrincipalDto;
import de.chronos_live.chronos_date_api.dto.UpdatedUserDto;
import de.chronos_live.chronos_date_api.mapper.PrincipalMapper;
import io.micrometer.core.annotation.Timed;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Path("/api/v2/user")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@PermitAll
@Timed("api.user")
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
    public Response patchUser(@RequestBody PrincipalDto userDto, @QueryParam("redirectUri") String redirectUri) {
        UpdatedUserDto newUser = this.userService.updateUser(
                userDto.first_name(),
                userDto.last_name(),
                userDto.email(),
                jwt.getSubject(),
                redirectUri
        );
        return Response.ok(newUser).build();
    }

    @DELETE
    public Response deleteUser() {
        return Response.status(Response.Status.METHOD_NOT_ALLOWED).build();
    }

    @GET
    @Path("/linked")
    public Response getLinkedAccounts() {
        List<LinkedAccountDto> accounts = userService.getLinkedAccounts(jwt.getSubject()).stream()
                .map(fi -> new LinkedAccountDto(fi.getIdentityProvider()))
                .toList();
        return Response.ok(accounts).build();
    }

    @DELETE
    @Path("/linked/{providerId}")
    public Response unlinkAccount(@PathParam("providerId") String providerId) {
        userService.unlinkAccount(jwt.getSubject(), providerId);
        return Response.noContent().build();
    }

    @GET
    @Path("/link/{provider}")
    public Response getLinkUrl(
            @PathParam("provider") String provider,
            @QueryParam("redirectUri") String redirectUri
    ) {
        try {
            String linkUrl = userService.getLinkUrl(provider, redirectUri);
            return Response.ok(Map.of("url", linkUrl)).build();
        } catch (NoSuchAlgorithmException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GET
    @Path("/link/passkey")
    public Response getPasskeyRegistrationUrl(
            @QueryParam("redirectUri") String redirectUri
    ) {
        String linkUrl = userService.getPasskeyRegistrationUrl(redirectUri);
        return Response.ok(Map.of("url", linkUrl)).build();
    }

    @GET
    @Path("/unlink/passkey")
    public Response getPasskeyDeletionUrl(
            @QueryParam("redirectUri") String redirectUri,
            @QueryParam("passkeyId") String passkeyId
    ) {
        String linkUrl = userService.getDeletePasskeyUrl(redirectUri, passkeyId);
        return Response.ok(Map.of("url", linkUrl)).build();
    }

    @GET
    @Path("/passkeys")
    public Response getPasskeys() {
        List<PasskeyDto> passkeys = userService.getPasskeys(jwt.getSubject()).stream()
                .map(c -> new PasskeyDto(
                        c.getId(),
                        c.getUserLabel(),
                        c.getCreatedDate() != null ? Instant.ofEpochMilli(c.getCreatedDate()).toString() : null,
                        null
                ))
                .toList();
        return Response.ok(passkeys).build();
    }

    @GET
    @Path("/change-password")
    public Response getChangePasswordUrl(@QueryParam("redirectUri") String redirectUri) {
        String linkUrl = userService.getChangePasswordUrl(redirectUri);
        return Response.ok(Map.of("url", linkUrl)).build();
    }
}
