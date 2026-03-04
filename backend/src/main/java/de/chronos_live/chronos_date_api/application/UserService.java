package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.User;
import de.chronos_live.chronos_date_api.dto.PrincipalDto;
import de.chronos_live.chronos_date_api.dto.UpdatedUserDto;
import de.chronos_live.chronos_date_api.exception.BadRequestException;
import de.chronos_live.chronos_date_api.security.PrincipalContext;
import io.micrometer.core.annotation.Timed;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.InternalServerErrorException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.FederatedIdentityRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;

@ApplicationScoped
@Transactional
@Timed("service.user")
public class UserService {
    @Inject
    Keycloak keycloak;

    @Inject
    PrincipalContext principalContext;

    @ConfigProperty(name = "quarkus.keycloak.admin-client.realm")
    String realm;

    @ConfigProperty(name = "quarkus.keycloak.admin-client.server-url")
    String oidcAuthServerUrl;

    @ConfigProperty(name = "quarkus.keycloak.admin-client.client-id")
    String clientId;

    @ConfigProperty(name = "quarkus.keycloak.admin-client.client-secret")
    String clientSecret;

    public User createUser(String firstName, String lastName, String email, String oidcId) {
        try {
            Objects.requireNonNull(firstName);
            Objects.requireNonNull(lastName);
            Objects.requireNonNull(email);
            Objects.requireNonNull(oidcId);
        } catch (NullPointerException e) {
            throw new BadRequestException(e.getMessage());
        }

        User user;

        Optional<User> existingUser = User.find("oidcId = ?1 OR email = ?2", oidcId, email).firstResultOptional();
        if (existingUser.isPresent()) {
            // Update User if the user already exists
            user = existingUser.get();
        } else {
            // Create a new user otherwise
            user = new User();
            user.persist();
            user.setCreatedAt(Instant.now());
        }
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);

        user.setOidcId(oidcId);

        user.setLastUpdate(Instant.now());
        return user;
    }

    public User getUser(String oidcId) {
        if (oidcId == null) {
            throw new BadRequestException("Authentication invalid");
        }
        return (User) User.find("oidcId = ?1", oidcId).firstResultOptional().orElseGet(() -> {
            User user = new User();
            user.setOidcId(oidcId);
            user.setCreatedAt(Instant.now());
            user.setLastUpdate(Instant.now());
            user.persist();
            return user;
        });
    }

    public void updateLastSeen(User user) {
        User managed = User.findById(user.id);
        managed.setLastSeen(Instant.now());
    }

    public Optional<User> getUser(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return User.find("id = ?1", id).firstResultOptional();
    }

    public UpdatedUserDto updateUser(String firstName, String lastName, String email, String oidcId, String redirectUri) {
        User user = (User) User.find("oidcId = ?1", oidcId).firstResultOptional()
                .orElseThrow(() -> new BadRequestException("This user does not exist yet"));
        if (firstName != null) {
            user.setFirstName(user.getFirstName());
        }
        if (lastName != null) {
            user.setLastName(user.getLastName());
        }
        boolean emailChanged = false;
        if (email != null &&
                (user.getEmail() == null || !user.getEmail().equals(email))) {
            emailChanged = true;
            user.setEmail(email);
        }
        user.setLastUpdate(Instant.now());

        if (emailChanged && redirectUri == null) {
            throw new BadRequestException("Redirect Url must be set to change email address");
        }

        this.updateUserInKeycloak(user, emailChanged);

        if (emailChanged) {
            keycloak.realm(realm).users().get(oidcId)
                    .sendVerifyEmail(clientId, redirectUri, 60 * 60 /*seconds token lifespan => 1h*/);
        }

        UpdatedUserDto updatedUserDto = new UpdatedUserDto();
        updatedUserDto.setUser(new PrincipalDto(user.id, firstName, lastName, email));
        updatedUserDto.setVerifyEmailUrl(getVerifyEmailUrl(redirectUri));
        return updatedUserDto;
    }

    private void updateUserInKeycloak(User user, boolean emailChanged) {
        UserRepresentation userRep = new UserRepresentation();
        userRep.setFirstName(user.getFirstName());
        userRep.setLastName(user.getLastName());
        userRep.setEmail(user.getEmail());
        if (emailChanged) {
            userRep.setEmailVerified(false);
        }
        keycloak.realm(realm).users().get(user.getOidcId()).update(userRep);
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

    public String getPasskeyRegistrationUrl(
            String redirectUri
    ) {
        return oidcAuthServerUrl + "/realms/" + realm
                + "/protocol/openid-connect/auth"
                + "?client_id=" + clientId
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, UTF_8)
                + "&response_type=code"
                + "&scope=openid"
                + "&kc_action=webauthn-register-passwordless";
    }

    public String getDeletePasskeyUrl(
            String redirectUri,
            String passkeyId
    ) {
        return oidcAuthServerUrl + "/realms/" + realm
                + "/protocol/openid-connect/auth"
                + "?client_id=" + clientId
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, UTF_8)
                + "&response_type=code"
                + "&scope=openid"
                + "&kc_action=delete_credential:" + passkeyId;
    }

    public String getChangePasswordUrl(
            String redirectUri
    ) {
        return oidcAuthServerUrl + "/realms/" + realm
                + "/protocol/openid-connect/auth"
                + "?client_id=" + clientId
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, UTF_8)
                + "&response_type=code"
                + "&scope=openid"
                + "&kc_action=UPDATE_PASSWORD";
    }

    public String getVerifyEmailUrl(
            String redirectUri
    ) {
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
