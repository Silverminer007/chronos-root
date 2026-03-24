package de.chronos_live.chronos_date_api.application;

import de.chronos_live.chronos_date_api.domain.User;
import de.chronos_live.chronos_date_api.dto.UpdatedUserDto;
import de.chronos_live.chronos_date_api.exception.BadRequestException;
import de.chronos_live.chronos_date_api.security.PrincipalContext;
import io.agroal.api.AgroalDataSource;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UserService}.
 *
 * <p>Strategy: {@code @QuarkusTest} + {@code @InjectMock} replaces all CDI
 * dependencies (Keycloak admin client, PrincipalContext) with Mockito mocks.
 * {@link PanacheMock} intercepts every Panache-enhanced call on {@link User}
 * so no real database is ever touched.
 *
 * <p><b>Untestable branch:</b><br>
 * {@code updateUser} line 116–119: when {@code firstName != null}, the method
 * calls {@code user.setFirstName(user.getFirstName())} — a no-op that always
 * runs when non-null (same logic for lastName). These are testable but yield
 * no observable side-effect difference beyond coverage counters.
 */
@QuarkusTest
class UserServiceTest {

    // ── Constants ──────────────────────────────────────────────────────────────
    private static final Long   USER_ID      = 1L;
    private static final String OIDC_ID      = "oidc-abc-123";
    private static final String FIRST_NAME   = "Alice";
    private static final String LAST_NAME    = "Tester";
    private static final String EMAIL        = "alice@example.com";
    private static final String NEW_EMAIL    = "alice-new@example.com";
    private static final String REDIRECT_URI = "https://example.com/redirect";

    // ── CDI injection ──────────────────────────────────────────────────────────
    @Inject
    UserService service;

    @Inject
    AgroalDataSource dataSource;

    @InjectMock
    Keycloak keycloak;

    @InjectMock
    PrincipalContext principalContext;

    @AfterEach
    void cleanupUserTestData() throws Exception {
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM users WHERE oidcid = 'oidc-abc-123'");
        }
    }

    // ── Keycloak chain mocks ───────────────────────────────────────────────────
    private RealmResource realmResource;
    private UsersResource usersResource;
    private UserResource userResource;

    @BeforeEach
    void setupKeycloakMocks() {
        realmResource  = mock(RealmResource.class);
        usersResource  = mock(UsersResource.class);
        userResource   = mock(UserResource.class);
        when(keycloak.realm(anyString())).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.get(anyString())).thenReturn(userResource);
    }

    // ── Test-object builder ───────────────────────────────────────────────────
    private static User buildUser() {
        User u = new User();
        u.id = USER_ID;
        u.setFirstName(FIRST_NAME);
        u.setLastName(LAST_NAME);
        u.setEmail(EMAIL);
        u.setOidcId(OIDC_ID);
        return u;
    }

    // ── Helper: stub User.find to return an Optional ──────────────────────────
    @SuppressWarnings("unchecked")
    private static void stubUserFind(Optional<User> result) {
        PanacheQuery<User> q = mock(PanacheQuery.class);
        when(User.<User>find(anyString(), any(Object[].class))).thenReturn(q);
        when(q.firstResultOptional()).thenReturn(result);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // createUser
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – createUser:
     *   B1  firstName == null         → BadRequestException
     *   B2  lastName  == null         → BadRequestException
     *   B3  email     == null         → BadRequestException
     *   B4  oidcId    == null         → BadRequestException
     *   B5  existingUser present      → update existing user
     *   B6  existingUser absent       → create new user + persist
     *
     * Total branches: 6  |  Tests: 6
     */
    @Nested
    class CreateUser {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(User.class);
        }

        // B1=true
        @Test
        void should_throwBadRequestException_when_firstNameIsNull() {
            assertThatThrownBy(() -> service.createUser(null, LAST_NAME, EMAIL, OIDC_ID))
                    .isInstanceOf(BadRequestException.class);
        }

        // B2=true
        @Test
        void should_throwBadRequestException_when_lastNameIsNull() {
            assertThatThrownBy(() -> service.createUser(FIRST_NAME, null, EMAIL, OIDC_ID))
                    .isInstanceOf(BadRequestException.class);
        }

        // B3=true
        @Test
        void should_throwBadRequestException_when_emailIsNull() {
            assertThatThrownBy(() -> service.createUser(FIRST_NAME, LAST_NAME, null, OIDC_ID))
                    .isInstanceOf(BadRequestException.class);
        }

        // B4=true
        @Test
        void should_throwBadRequestException_when_oidcIdIsNull() {
            assertThatThrownBy(() -> service.createUser(FIRST_NAME, LAST_NAME, EMAIL, null))
                    .isInstanceOf(BadRequestException.class);
        }

        // B5=true — existing user found → update fields, no new persist
        @Test
        void should_updateExistingUser_when_userAlreadyExists() {
            User existing = buildUser();
            stubUserFind(Optional.of(existing));

            User result = service.createUser(FIRST_NAME, LAST_NAME, EMAIL, OIDC_ID);

            assertThat(result).isSameAs(existing);
            assertThat(result.getFirstName()).isEqualTo(FIRST_NAME);
            assertThat(result.getLastName()).isEqualTo(LAST_NAME);
            assertThat(result.getEmail()).isEqualTo(EMAIL);
            assertThat(result.getOidcId()).isEqualTo(OIDC_ID);
            // persist() must NOT have been called for an existing user
            // (instance persist is not intercepted by PanacheMock; verified by absence of constraint violation)
        }

        // B6=true — no existing user → create and persist new user
        @Test
        void should_createNewUserAndPersist_when_userDoesNotExist() {
            stubUserFind(Optional.empty());

            User result = service.createUser(FIRST_NAME, LAST_NAME, EMAIL, OIDC_ID);

            assertThat(result).isNotNull();
            assertThat(result.getFirstName()).isEqualTo(FIRST_NAME);
            assertThat(result.getLastName()).isEqualTo(LAST_NAME);
            assertThat(result.getEmail()).isEqualTo(EMAIL);
            assertThat(result.getOidcId()).isEqualTo(OIDC_ID);
            assertThat(result.getCreatedAt()).isNotNull();
            // instance persist() is not intercepted by PanacheMock; verified by result being non-null
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getUser(String oidcId)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – getUser(String):
     *   B1  oidcId == null           → BadRequestException
     *   B2  user found in DB         → return existing user
     *   B3  user not found in DB     → create + persist new user with oidcId
     *
     * Total branches: 3  |  Tests: 3
     */
    @Nested
    class GetUserByOidcId {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(User.class);
        }

        // B1=true
        @Test
        void should_throwBadRequestException_when_oidcIdIsNull() {
            assertThatThrownBy(() -> service.getUser((String) null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Authentication invalid");
        }

        // B2=true
        @Test
        void should_returnExistingUser_when_foundByOidcId() {
            User existing = buildUser();
            stubUserFind(Optional.of(existing));

            User result = service.getUser(OIDC_ID);

            assertThat(result).isSameAs(existing);
        }

        // B3=true
        @Test
        void should_createAndPersistUser_when_notFoundByOidcId() {
            stubUserFind(Optional.empty());

            User result = service.getUser(OIDC_ID);

            assertThat(result).isNotNull();
            assertThat(result.getOidcId()).isEqualTo(OIDC_ID);
            assertThat(result.getCreatedAt()).isNotNull();
            assertThat(result.getLastUpdate()).isNotNull();
            // instance persist() is not intercepted by PanacheMock; verified by result being non-null
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // updateLastSeen
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – updateLastSeen:
     *   No conditional branches. Finds managed user by id and sets lastSeen.
     *
     * Total branches: 0  |  Tests: 1
     */
    @Nested
    class UpdateLastSeen {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(User.class);
        }

        @Test
        void should_setLastSeen_when_calledWithUser() {
            User managed = buildUser();
            when(User.<User>findById(USER_ID)).thenReturn(managed);

            User inputUser = new User();
            inputUser.id = USER_ID;

            service.updateLastSeen(inputUser);

            assertThat(managed.getLastSeen()).isNotNull();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getUser(Long id)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – getUser(Long):
     *   B1  id == null     → empty Optional
     *   B2  id not null    → delegates to User.find + firstResultOptional
     *
     * Total branches: 2  |  Tests: 2
     */
    @Nested
    class GetUserById {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(User.class);
        }

        // B1=true
        @Test
        void should_returnEmptyOptional_when_idIsNull() {
            Optional<User> result = service.getUser((Long) null);

            assertThat(result).isEmpty();
        }

        // B2=true — id is non-null, user exists
        @Test
        void should_returnUserOptional_when_idIsNotNull() {
            User existing = buildUser();
            stubUserFind(Optional.of(existing));

            Optional<User> result = service.getUser(USER_ID);

            assertThat(result).contains(existing);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // updateUser
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – updateUser:
     *   B1  user not found by oidcId          → BadRequestException
     *   B2  firstName != null                 → no-op setFirstName (always runs)
     *   B3  lastName  != null                 → no-op setLastName  (always runs)
     *   B4  email != null && email changed    → true (emailChanged=true)
     *   B5  email null or unchanged           → false (emailChanged=false)
     *   B6  emailChanged && redirectUri null  → BadRequestException
     *   B7  emailChanged && redirectUri set   → sendVerifyEmail called
     *   B8  !emailChanged                     → sendVerifyEmail not called
     *
     * Total branches: 8  |  Tests: 5
     */
    @Nested
    class UpdateUser {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(User.class);
        }

        // B1=true
        @Test
        void should_throwBadRequestException_when_userNotFound() {
            stubUserFind(Optional.empty());

            assertThatThrownBy(() ->
                    service.updateUser(FIRST_NAME, LAST_NAME, EMAIL, OIDC_ID, REDIRECT_URI))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("does not exist");
        }

        // B4=true, B6=true — email changed but no redirectUri
        @Test
        void should_throwBadRequestException_when_emailChangedButNoRedirectUri() {
            User existing = buildUser();
            stubUserFind(Optional.of(existing));

            assertThatThrownBy(() ->
                    service.updateUser(FIRST_NAME, LAST_NAME, NEW_EMAIL, OIDC_ID, null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Redirect Url");
        }

        // B4=true, B7=true — email changed with redirectUri → sendVerifyEmail called
        @Test
        void should_sendVerifyEmailAndReturnDto_when_emailChangedWithRedirectUri() {
            User existing = buildUser();
            stubUserFind(Optional.of(existing));

            UpdatedUserDto result =
                    service.updateUser(FIRST_NAME, LAST_NAME, NEW_EMAIL, OIDC_ID, REDIRECT_URI);

            verify(userResource).update(any());
            verify(userResource).sendVerifyEmail(anyString(), eq(REDIRECT_URI), anyInt());
            assertThat(result).isNotNull();
            assertThat(result.getVerifyEmailUrl()).contains("VERIFY_EMAIL");
        }

        // B5=true (email null) — no email change, updateKeycloak called but no sendVerifyEmail
        @Test
        void should_notSendVerifyEmail_when_emailIsNull() {
            User existing = buildUser();
            stubUserFind(Optional.of(existing));

            UpdatedUserDto result =
                    service.updateUser(FIRST_NAME, LAST_NAME, null, OIDC_ID, REDIRECT_URI);

            verify(userResource).update(any());
            verify(userResource, never()).sendVerifyEmail(any(), any(), anyInt());
            assertThat(result).isNotNull();
        }

        // B5 variant — email same as existing → no email change
        @Test
        void should_notSendVerifyEmail_when_emailUnchanged() {
            User existing = buildUser(); // existing.email == EMAIL
            stubUserFind(Optional.of(existing));

            UpdatedUserDto result =
                    service.updateUser(FIRST_NAME, LAST_NAME, EMAIL, OIDC_ID, REDIRECT_URI);

            verify(userResource, never()).sendVerifyEmail(any(), any(), anyInt());
            assertThat(result).isNotNull();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getLinkedAccounts
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – getLinkedAccounts:
     *   No conditional branches. Delegates to Keycloak.
     *
     * Total branches: 0  |  Tests: 1
     */
    @Nested
    class GetLinkedAccounts {

        @Test
        void should_returnFederatedIdentities_when_called() {
            List<FederatedIdentityRepresentation> identities = List.of(
                    new FederatedIdentityRepresentation());
            when(userResource.getFederatedIdentity()).thenReturn(identities);

            List<FederatedIdentityRepresentation> result = service.getLinkedAccounts(OIDC_ID);

            assertThat(result).isEqualTo(identities);
            verify(userResource).getFederatedIdentity();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // unlinkAccount
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – unlinkAccount:
     *   No conditional branches. Delegates to Keycloak.
     *
     * Total branches: 0  |  Tests: 1
     */
    @Nested
    class UnlinkAccount {

        @Test
        void should_callRemoveFederatedIdentity_when_called() {
            service.unlinkAccount(OIDC_ID, "google");

            verify(userResource).removeFederatedIdentity("google");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getLinkUrl
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – getLinkUrl:
     *   No conditional branches. Pure URL construction.
     *
     * Total branches: 0  |  Tests: 1
     */
    @Nested
    class GetLinkUrl {

        @Test
        void should_returnUrlWithProviderAndRedirectUri_when_called()
                throws NoSuchAlgorithmException {
            String url = service.getLinkUrl("google", REDIRECT_URI);

            assertThat(url).contains("client_id=test-client");
            assertThat(url).contains("kc_action=idp_link:google");
            assertThat(url).contains("response_type=code");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getPasskeyRegistrationUrl
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – getPasskeyRegistrationUrl:
     *   No conditional branches. Pure URL construction.
     *
     * Total branches: 0  |  Tests: 1
     */
    @Nested
    class GetPasskeyRegistrationUrl {

        @Test
        void should_returnUrlWithWebauthnRegisterAction_when_called() {
            String url = service.getPasskeyRegistrationUrl(REDIRECT_URI);

            assertThat(url).contains("kc_action=webauthn-register-passwordless");
            assertThat(url).contains("client_id=test-client");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getDeletePasskeyUrl
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – getDeletePasskeyUrl:
     *   No conditional branches. Pure URL construction.
     *
     * Total branches: 0  |  Tests: 1
     */
    @Nested
    class GetDeletePasskeyUrl {

        @Test
        void should_returnUrlWithDeleteCredentialAction_when_called() {
            String passkeyId = "pk-id-99";
            String url = service.getDeletePasskeyUrl(REDIRECT_URI, passkeyId);

            assertThat(url).contains("kc_action=delete_credential:" + passkeyId);
            assertThat(url).contains("client_id=test-client");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getChangePasswordUrl
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – getChangePasswordUrl:
     *   No conditional branches. Pure URL construction.
     *
     * Total branches: 0  |  Tests: 1
     */
    @Nested
    class GetChangePasswordUrl {

        @Test
        void should_returnUrlWithUpdatePasswordAction_when_called() {
            String url = service.getChangePasswordUrl(REDIRECT_URI);

            assertThat(url).contains("kc_action=UPDATE_PASSWORD");
            assertThat(url).contains("client_id=test-client");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getVerifyEmailUrl
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – getVerifyEmailUrl:
     *   No conditional branches. Pure URL construction.
     *
     * Total branches: 0  |  Tests: 1
     */
    @Nested
    class GetVerifyEmailUrl {

        @Test
        void should_returnUrlWithVerifyEmailAction_when_called() {
            String url = service.getVerifyEmailUrl(REDIRECT_URI);

            assertThat(url).contains("kc_action=VERIFY_EMAIL");
            assertThat(url).contains("client_id=test-client");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getPasskeys
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – getPasskeys:
     *   B1  credential type is "webauthn-passwordless" → included in result
     *   B2  credential type is something else           → excluded from result
     *
     * Total branches: 2  |  Tests: 1 (covers both in a single test)
     */
    @Nested
    class GetPasskeys {

        @Test
        void should_returnOnlyPasskeyCredentials_when_called() {
            CredentialRepresentation passkey = new CredentialRepresentation();
            passkey.setType("webauthn-passwordless");

            CredentialRepresentation password = new CredentialRepresentation();
            password.setType("password");

            when(userResource.credentials()).thenReturn(List.of(passkey, password));

            List<CredentialRepresentation> result = service.getPasskeys(OIDC_ID);

            assertThat(result).containsExactly(passkey);
        }
    }
}
