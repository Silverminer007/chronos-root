package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.dto.UpdatedUserDto;
import de.chronos_live.chronos_date_api.exception.BadRequestException;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.FederatedIdentityRepresentation;

import java.security.NoSuchAlgorithmException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link KeycloakProfileService}.
 *
 * <p>Strategy: {@code @QuarkusTest} + {@code @InjectMock} replaces the
 * {@code Keycloak} admin client with a Mockito mock. No database access.
 */
@QuarkusTest
class KeycloakProfileServiceTest {

    private static final String OIDC_ID      = "oidc-abc-123";
    private static final String FIRST_NAME   = "Alice";
    private static final String LAST_NAME    = "Tester";
    private static final String EMAIL        = "alice@example.com";
    private static final String REDIRECT_URI = "https://example.com/redirect";
    private static final String PROVIDER     = "google";
    private static final String PASSKEY_ID   = "pk-id-1";

    @Inject
    KeycloakProfileService service;

    @InjectMock
    Keycloak keycloak;

    private RealmResource realmResource;
    private UsersResource usersResource;
    private UserResource userResource;

    @BeforeEach
    void setupKeycloakMocks() {
        realmResource = mock(RealmResource.class);
        usersResource = mock(UsersResource.class);
        userResource  = mock(UserResource.class);
        when(keycloak.realm(anyString())).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.get(anyString())).thenReturn(userResource);
    }

    @Nested
    class UpdateUser {

        @Test
        void should_throwBadRequestException_when_emailChangedButNoRedirectUri() {
            assertThatThrownBy(() -> service.updateUser(FIRST_NAME, LAST_NAME, EMAIL, OIDC_ID, null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Redirect");
        }

        @Test
        void should_updateKeycloakAndReturnDto_when_noEmailChange() {
            UpdatedUserDto result = service.updateUser(FIRST_NAME, LAST_NAME, null, OIDC_ID, null);

            verify(userResource).update(any());
            verify(userResource, never()).sendVerifyEmail(any(), any(), anyInt());
            assertThat(result).isNotNull();
        }

        @Test
        void should_updateKeycloakAndSendVerifyEmail_when_emailChanged() {
            UpdatedUserDto result = service.updateUser(FIRST_NAME, LAST_NAME, EMAIL, OIDC_ID, REDIRECT_URI);

            verify(userResource).update(any());
            verify(userResource).sendVerifyEmail(anyString(), eq(REDIRECT_URI), anyInt());
            assertThat(result.getVerifyEmailUrl()).isNotNull();
        }
    }

    @Nested
    class GetLinkedAccounts {

        @Test
        void should_delegateToKeycloak() {
            List<FederatedIdentityRepresentation> expected = List.of(new FederatedIdentityRepresentation());
            when(userResource.getFederatedIdentity()).thenReturn(expected);

            assertThat(service.getLinkedAccounts(OIDC_ID)).isSameAs(expected);
        }
    }

    @Nested
    class UnlinkAccount {

        @Test
        void should_callRemoveFederatedIdentity() {
            service.unlinkAccount(OIDC_ID, PROVIDER);

            verify(userResource).removeFederatedIdentity(PROVIDER);
        }
    }

    @Nested
    class GetLinkUrl {

        @Test
        void should_returnUrlContainingProvider() throws NoSuchAlgorithmException {
            String url = service.getLinkUrl(PROVIDER, REDIRECT_URI);

            assertThat(url).contains("idp_link:" + PROVIDER);
        }
    }

    @Nested
    class GetPasskeyRegistrationUrl {

        @Test
        void should_returnUrlContainingWebauthnAction() {
            String url = service.getPasskeyRegistrationUrl(REDIRECT_URI);

            assertThat(url).contains("webauthn-register-passwordless");
        }
    }

    @Nested
    class GetDeletePasskeyUrl {

        @Test
        void should_returnUrlContainingPasskeyId() {
            String url = service.getDeletePasskeyUrl(REDIRECT_URI, PASSKEY_ID);

            assertThat(url).contains("delete_credential:" + PASSKEY_ID);
        }
    }

    @Nested
    class GetChangePasswordUrl {

        @Test
        void should_returnUrlContainingUpdatePasswordAction() {
            String url = service.getChangePasswordUrl(REDIRECT_URI);

            assertThat(url).contains("UPDATE_PASSWORD");
        }
    }

    @Nested
    class GetPasskeys {

        @Test
        void should_returnOnlyWebauthnPasswordlessCredentials() {
            CredentialRepresentation webauthn = new CredentialRepresentation();
            webauthn.setType("webauthn-passwordless");
            webauthn.setId("pk-1");

            CredentialRepresentation password = new CredentialRepresentation();
            password.setType("password");

            when(userResource.credentials()).thenReturn(List.of(webauthn, password));

            List<CredentialRepresentation> result = service.getPasskeys(OIDC_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getType()).isEqualTo("webauthn-passwordless");
        }
    }
}
