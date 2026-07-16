package de.chronos_live.chronos_date_api.infrastructure;

import de.chronos_live.chronos_date_api.application.ports.IdentityPort;
import de.chronos_live.chronos_date_api.domain.UserIdentity;
import io.agroal.api.AgroalDataSource;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for the Keycloak fallback paths in {@link LocalDbIdentityAdapter}.
 *
 * <p>All tests use an oidcId absent from {@code user_profiles}, forcing the adapter
 * to reach Keycloak. The Keycloak admin client chain is mocked via {@code @InjectMock}.
 *
 * <p>Behaviour under test (finding 3 from PR #4 review):
 * <ul>
 *   <li>Keycloak 404 → deleted sentinel returned, nothing written to {@code user_profiles}</li>
 *   <li>Keycloak unavailable → {@link ServiceUnavailableException} propagated</li>
 * </ul>
 */
@QuarkusTest
class LocalDbIdentityAdapterTest {

    private static final String ABSENT_OIDC_ID = "test-oidc-not-in-keycloak";

    @Inject
    IdentityPort identityPort;

    @InjectMock
    Keycloak keycloak;

    @Inject
    AgroalDataSource dataSource;

    private UserResource userResource;

    @BeforeEach
    void setUp() throws Exception {
        RealmResource realmResource = mock(RealmResource.class);
        UsersResource usersResource = mock(UsersResource.class);
        userResource = mock(UserResource.class);

        when(keycloak.realm(anyString())).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.get(ABSENT_OIDC_ID)).thenReturn(userResource);

        // Remove any leftover profile row so the DB-cache miss path is always exercised.
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("DELETE FROM user_profiles WHERE oidc_id = ?")) {
            stmt.setString(1, ABSENT_OIDC_ID);
            stmt.executeUpdate();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // findById — Keycloak fallback
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – findById Keycloak fallback:
     *   B1  Keycloak 404 → deleted sentinel returned
     *   B2  Keycloak unavailable → ServiceUnavailableException
     *   B3  Keycloak 404 → sentinel NOT persisted to user_profiles
     *
     * Total branches: 3  |  Tests: 3
     */
    @Nested
    class FindById {

        // B1
        @Test
        void should_returnDeletedSentinel_when_keycloakReturns404() {
            when(userResource.toRepresentation()).thenThrow(new NotFoundException());

            UserIdentity result = identityPort.findById(ABSENT_OIDC_ID);

            assertThat(result.isDeleted()).isTrue();
            assertThat(result.oidcId()).isEqualTo(ABSENT_OIDC_ID);
            assertThat(result.getName()).isEqualTo("Gelöschter Benutzer");
        }

        // B2
        @Test
        void should_throwServiceUnavailableException_when_keycloakIsUnreachable() {
            when(userResource.toRepresentation()).thenThrow(new RuntimeException("Connection refused"));

            assertThatThrownBy(() -> identityPort.findById(ABSENT_OIDC_ID))
                    .isInstanceOf(ServiceUnavailableException.class);
        }

        // B3
        @Test
        void should_notWriteToUserProfiles_when_keycloakReturns404() throws Exception {
            when(userResource.toRepresentation()).thenThrow(new NotFoundException());

            identityPort.findById(ABSENT_OIDC_ID);

            try (var conn = dataSource.getConnection();
                 var stmt = conn.prepareStatement(
                         "SELECT COUNT(*) FROM user_profiles WHERE oidc_id = ?")) {
                stmt.setString(1, ABSENT_OIDC_ID);
                var rs = stmt.executeQuery();
                rs.next();
                assertThat(rs.getLong(1)).isZero();
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // findByIds — Keycloak fallback for cache misses
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – findByIds Keycloak fallback:
     *   B1  Keycloak 404 → deleted sentinel present in returned map
     *   B2  Keycloak unavailable → ServiceUnavailableException
     *   B3  Keycloak 404 → sentinel NOT persisted to user_profiles
     *
     * Total branches: 3  |  Tests: 3
     */
    @Nested
    class FindByIds {

        // B1
        @Test
        void should_includeDeletedSentinelInResult_when_keycloakReturns404() {
            when(userResource.toRepresentation()).thenThrow(new NotFoundException());

            Map<String, UserIdentity> result = identityPort.findByIds(List.of(ABSENT_OIDC_ID));

            assertThat(result).containsKey(ABSENT_OIDC_ID);
            assertThat(result.get(ABSENT_OIDC_ID).isDeleted()).isTrue();
        }

        // B2
        @Test
        void should_throwServiceUnavailableException_when_keycloakIsUnreachable() {
            when(userResource.toRepresentation()).thenThrow(new RuntimeException("Connection refused"));

            assertThatThrownBy(() -> identityPort.findByIds(List.of(ABSENT_OIDC_ID)))
                    .isInstanceOf(ServiceUnavailableException.class);
        }

        // B3
        @Test
        void should_notWriteToUserProfiles_when_keycloakReturns404() throws Exception {
            when(userResource.toRepresentation()).thenThrow(new NotFoundException());

            identityPort.findByIds(List.of(ABSENT_OIDC_ID));

            try (var conn = dataSource.getConnection();
                 var stmt = conn.prepareStatement(
                         "SELECT COUNT(*) FROM user_profiles WHERE oidc_id = ?")) {
                stmt.setString(1, ABSENT_OIDC_ID);
                var rs = stmt.executeQuery();
                rs.next();
                assertThat(rs.getLong(1)).isZero();
            }
        }
    }
}