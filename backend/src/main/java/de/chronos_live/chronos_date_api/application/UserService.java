package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.UserIdentity;
import de.chronos_live.chronos_date_api.dto.PrincipalDto;
import de.chronos_live.chronos_date_api.dto.UpdatedUserDto;
import de.chronos_live.chronos_date_api.exception.BadRequestException;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.FederatedIdentityRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import io.quarkus.cache.CacheResult;

import java.net.URLEncoder;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

@ApplicationScoped
@Transactional
@Timed("service.user")
public class UserService {
    private static final Logger LOGGER = Logger.getLogger(UserService.class);

    @Inject
    Keycloak keycloak;

    @ConfigProperty(name = "quarkus.keycloak.admin-client.realm")
    String realm;

    @ConfigProperty(name = "quarkus.keycloak.admin-client.server-url")
    String oidcAuthServerUrl;

    @ConfigProperty(name = "quarkus.keycloak.admin-client.client-id")
    String clientId;

    /**
     * Builds a UserIdentity from the current JWT — no database call.
     */
    public UserIdentity fromToken(JsonWebToken jwt) {
        if (jwt.getSubject() == null) {
            throw new BadRequestException("Authentication invalid");
        }
        return new UserIdentity(
                jwt.getSubject(),
                jwt.getClaim("given_name"),
                jwt.getClaim("family_name"),
                jwt.getClaim("email"),
                jwt.getClaim("picture")
        );
    }

    /**
     * Fetches a UserIdentity from Keycloak Admin API by oidcId.
     * Results are cached (see quarkus.cache.caffeine.user-identity in application.properties).
     */
    @CacheResult(cacheName = "user-identity")
    public UserIdentity getUserByOidcId(String oidcId) {
        UserRepresentation rep = keycloak.realm(realm).users().get(oidcId).toRepresentation();
        return new UserIdentity(
                rep.getId(),
                rep.getFirstName(),
                rep.getLastName(),
                rep.getEmail(),
                rep.getAttributes() != null
                        ? rep.getAttributes().getOrDefault("picture", List.of()).stream().findFirst().orElse(null)
                        : null
        );
    }

    /**
     * Fetches multiple users from Keycloak in parallel, using the per-user cache.
     * Avoids N+1 by fanning out all requests concurrently and returning a lookup map.
     */
    public Map<String, UserIdentity> batchGetUsers(Collection<String> oidcIds) {
        return oidcIds.stream()
                .distinct()
                .parallel()
                .collect(Collectors.toMap(
                        id -> id,
                        this::getUserByOidcId
                ));
    }

    /**
     * Searches Keycloak for users whose name or email matches the query.
     * Replaces the former DB-based findNonFriends / findRecentNonFriends queries.
     */
    public List<UserIdentity> searchUsers(String query, int limit) {
        return keycloak.realm(realm).users().search(query, 0, limit).stream()
                .map(rep -> new UserIdentity(
                        rep.getId(),
                        rep.getFirstName(),
                        rep.getLastName(),
                        rep.getEmail(),
                        rep.getAttributes() != null
                                ? rep.getAttributes().getOrDefault("picture", List.of()).stream().findFirst().orElse(null)
                                : null
                ))
                .toList();
    }

    public UpdatedUserDto updateUser(String firstName, String lastName, String email, String oidcId, String redirectUri) {
        LOGGER.debugf("[Principal %s] Updating User in Keycloak", oidcId);

        boolean emailChanged = email != null;
        if (emailChanged && redirectUri == null) {
            throw new BadRequestException("Redirect Url must be set to change email address");
        }

        UserRepresentation userRep = new UserRepresentation();
        if (firstName != null) userRep.setFirstName(firstName);
        if (lastName != null) userRep.setLastName(lastName);
        if (emailChanged) {
            userRep.setEmail(email);
            userRep.setEmailVerified(false);
        }
        keycloak.realm(realm).users().get(oidcId).update(userRep);

        if (emailChanged) {
            keycloak.realm(realm).users().get(oidcId)
                    .sendVerifyEmail(clientId, redirectUri, 60 * 60);
        }

        UpdatedUserDto updatedUserDto = new UpdatedUserDto();
        updatedUserDto.setUser(new PrincipalDto(oidcId, firstName, lastName, email));
        updatedUserDto.setVerifyEmailUrl(getVerifyEmailUrl(redirectUri));
        return updatedUserDto;
    }

    public List<FederatedIdentityRepresentation> getLinkedAccounts(String oidcId) {
        return keycloak.realm(realm).users().get(oidcId).getFederatedIdentity();
    }

    public void unlinkAccount(String oidcId, String providerId) {
        keycloak.realm(realm).users().get(oidcId).removeFederatedIdentity(providerId);
    }

    public String getLinkUrl(String provider, String redirectUri) throws NoSuchAlgorithmException {
        return oidcAuthServerUrl + "/realms/" + realm
                + "/protocol/openid-connect/auth"
                + "?client_id=" + clientId
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, UTF_8)
                + "&response_type=code"
                + "&scope=openid"
                + "&kc_action=idp_link:" + provider;
    }

    public String getPasskeyRegistrationUrl(String redirectUri) {
        return oidcAuthServerUrl + "/realms/" + realm
                + "/protocol/openid-connect/auth"
                + "?client_id=" + clientId
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, UTF_8)
                + "&response_type=code"
                + "&scope=openid"
                + "&kc_action=webauthn-register-passwordless";
    }

    public String getDeletePasskeyUrl(String redirectUri, String passkeyId) {
        return oidcAuthServerUrl + "/realms/" + realm
                + "/protocol/openid-connect/auth"
                + "?client_id=" + clientId
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, UTF_8)
                + "&response_type=code"
                + "&scope=openid"
                + "&kc_action=delete_credential:" + passkeyId;
    }

    public String getChangePasswordUrl(String redirectUri) {
        return oidcAuthServerUrl + "/realms/" + realm
                + "/protocol/openid-connect/auth"
                + "?client_id=" + clientId
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, UTF_8)
                + "&response_type=code"
                + "&scope=openid"
                + "&kc_action=UPDATE_PASSWORD";
    }

    public String getVerifyEmailUrl(String redirectUri) {
        return oidcAuthServerUrl + "/realms/" + realm
                + "/protocol/openid-connect/auth"
                + "?client_id=" + clientId
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, UTF_8)
                + "&response_type=code"
                + "&scope=openid"
                + "&kc_action=VERIFY_EMAIL";
    }

    public List<CredentialRepresentation> getPasskeys(String oidcId) {
        return keycloak.realm(realm).users().get(oidcId).credentials().stream()
                .filter(c -> "webauthn-passwordless".equals(c.getType()))
                .toList();
    }
}
