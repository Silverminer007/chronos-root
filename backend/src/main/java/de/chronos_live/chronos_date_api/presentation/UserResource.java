package de.chronos_live.chronos_date_api.presentation;

import de.chronos_live.chronos_date_api.application.KeycloakProfileService;
import de.chronos_live.chronos_date_api.domain.UserIdentity;
import de.chronos_live.chronos_date_api.dto.LinkedAccountDto;
import de.chronos_live.chronos_date_api.dto.PasskeyDto;
import de.chronos_live.chronos_date_api.dto.PrincipalDto;
import de.chronos_live.chronos_date_api.dto.UpdatedUserDto;
import de.chronos_live.chronos_date_api.mapper.PrincipalMapper;
import de.chronos_live.chronos_date_api.security.PrincipalContext;
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
    KeycloakProfileService keycloakProfileService;
    @Inject
    PrincipalMapper mapper;
    @Inject
    PrincipalContext principalContext;
    @Inject
    JsonWebToken jwt;

    @GET
    public Response getUser() {
        UserIdentity user = principalContext.getPrincipal();
        return Response.ok(mapper.toDto(user)).build();
    }

    @POST
    public Response createUser() {
        // With Keycloak as source of truth, user "creation" is just reading the JWT.
        UserIdentity user = principalContext.getPrincipal();
        return Response.ok(mapper.toDto(user)).build();
    }

    @PATCH
    public Response patchUser(@RequestBody PrincipalDto userDto, @QueryParam("redirectUri") String redirectUri) {
        String oidcId = principalContext.getPrincipal().oidcId();
        UpdatedUserDto newUser = keycloakProfileService.updateUser(
                userDto.first_name(), userDto.last_name(), userDto.email(), oidcId, redirectUri);
        return Response.ok(newUser).build();
    }

    @DELETE
    public Response deleteUser() {
        return Response.status(Response.Status.METHOD_NOT_ALLOWED).build();
    }

    @GET
    @Path("/linked")
    public Response getLinkedAccounts() {
        String oidcId = principalContext.getPrincipal().oidcId();
        List<LinkedAccountDto> accounts = keycloakProfileService.getLinkedAccounts(oidcId).stream()
                .map(fi -> new LinkedAccountDto(fi.getIdentityProvider()))
                .toList();
        return Response.ok(accounts).build();
    }

    @DELETE
    @Path("/linked/{providerId}")
    public Response unlinkAccount(@PathParam("providerId") String providerId) {
        String oidcId = principalContext.getPrincipal().oidcId();
        keycloakProfileService.unlinkAccount(oidcId, providerId);
        return Response.noContent().build();
    }

    @GET
    @Path("/link/{provider}")
    public Response getLinkUrl(@PathParam("provider") String provider,
                               @QueryParam("redirectUri") String redirectUri) {
        try {
            String linkUrl = keycloakProfileService.getLinkUrl(provider, redirectUri);
            return Response.ok(Map.of("url", linkUrl)).build();
        } catch (NoSuchAlgorithmException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GET
    @Path("/link/passkey")
    public Response getPasskeyRegistrationUrl(@QueryParam("redirectUri") String redirectUri) {
        String linkUrl = keycloakProfileService.getPasskeyRegistrationUrl(redirectUri);
        return Response.ok(Map.of("url", linkUrl)).build();
    }

    @GET
    @Path("/unlink/passkey")
    public Response getPasskeyDeletionUrl(@QueryParam("redirectUri") String redirectUri,
                                          @QueryParam("passkeyId") String passkeyId) {
        String linkUrl = keycloakProfileService.getDeletePasskeyUrl(redirectUri, passkeyId);
        return Response.ok(Map.of("url", linkUrl)).build();
    }

    @GET
    @Path("/passkeys")
    public Response getPasskeys() {
        String oidcId = principalContext.getPrincipal().oidcId();
        List<PasskeyDto> passkeys = keycloakProfileService.getPasskeys(oidcId).stream()
                .map(c -> new PasskeyDto(c.getId(), c.getUserLabel(),
                        c.getCreatedDate() != null ? Instant.ofEpochMilli(c.getCreatedDate()).toString() : null, null))
                .toList();
        return Response.ok(passkeys).build();
    }

    @GET
    @Path("/change-password")
    public Response getChangePasswordUrl(@QueryParam("redirectUri") String redirectUri) {
        String linkUrl = keycloakProfileService.getChangePasswordUrl(redirectUri);
        return Response.ok(Map.of("url", linkUrl)).build();
    }
}
