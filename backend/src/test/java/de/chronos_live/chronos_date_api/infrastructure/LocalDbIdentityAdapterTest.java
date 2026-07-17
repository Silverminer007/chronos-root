package de.chronos_live.chronos_date_api.infrastructure;

import de.chronos_live.chronos_date_api.domain.UserIdentity;
import de.chronos_live.chronos_date_api.domain.UserProfile;
import io.quarkus.panache.mock.PanacheMock;
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
import org.keycloak.representations.idm.UserRepresentation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link LocalDbIdentityAdapter}.
 *
 * <p>Strategy: PanacheMock intercepts all static {@link UserProfile} calls;
 * the injected {@link Keycloak} admin client is replaced with a Mockito mock.
 */
@QuarkusTest
class LocalDbIdentityAdapterTest {

    private static final String OIDC_ID     = "oidc-abc-123";
    private static final String FIRST_NAME  = "Max";
    private static final String LAST_NAME   = "Mustermann";
    private static final String EMAIL       = "max@example.com";
    private static final String PICTURE_URL = "https://example.com/pic.jpg";

    @Inject
    LocalDbIdentityAdapter adapter;

    @InjectMock
    Keycloak keycloak;

    private RealmResource realmResource;
    private UsersResource usersResource;
    private UserResource  userResource;

    @BeforeEach
    void setupKeycloakMocks() {
        realmResource = mock(RealmResource.class);
        usersResource = mock(UsersResource.class);
        userResource  = mock(UserResource.class);
        when(keycloak.realm(anyString())).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.get(OIDC_ID)).thenReturn(userResource);
    }

    private static UserProfile buildProfile() {
        UserProfile p = new UserProfile();
        p.oidcId = OIDC_ID;
        p.firstName = FIRST_NAME;
        p.lastName = LAST_NAME;
        p.email = EMAIL;
        p.profilePictureUrl = PICTURE_URL;
        return p;
    }

    private static UserRepresentation buildRep(String id, String first, String last, String email) {
        UserRepresentation rep = new UserRepresentation();
        rep.setId(id);
        rep.setFirstName(first);
        rep.setLastName(last);
        rep.setEmail(email);
        return rep;
    }

    // ── findById ────────────────────────────────────────────────────────────

    @Nested
    class FindById {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(UserProfile.class);
        }

        @Test
        void should_returnCachedIdentity_when_profileExistsInDb() {
            UserProfile cached = buildProfile();
            @SuppressWarnings("unchecked")
            io.quarkus.hibernate.orm.panache.PanacheQuery<UserProfile> q =
                    mock(io.quarkus.hibernate.orm.panache.PanacheQuery.class);
            when(UserProfile.<UserProfile>find(anyString(), any(Object[].class))).thenReturn(q);
            when(q.firstResultOptional()).thenReturn(Optional.of(cached));

            UserIdentity result = adapter.findById(OIDC_ID);

            assertThat(result.oidcId()).isEqualTo(OIDC_ID);
            assertThat(result.firstName()).isEqualTo(FIRST_NAME);
            assertThat(result.email()).isEqualTo(EMAIL);
            verifyNoInteractions(keycloak);
        }

        @Test
        void should_fallbackToKeycloak_when_profileNotInDb() {
            @SuppressWarnings("unchecked")
            io.quarkus.hibernate.orm.panache.PanacheQuery<UserProfile> q =
                    mock(io.quarkus.hibernate.orm.panache.PanacheQuery.class);
            when(UserProfile.<UserProfile>find(anyString(), any(Object[].class))).thenReturn(q);
            when(q.firstResultOptional()).thenReturn(Optional.empty());
            when(UserProfile.update(anyString(), any(Object[].class))).thenReturn(0);

            UserRepresentation rep = buildRep(OIDC_ID, FIRST_NAME, LAST_NAME, EMAIL);
            when(userResource.toRepresentation()).thenReturn(rep);

            UserIdentity result = adapter.findById(OIDC_ID);

            assertThat(result.oidcId()).isEqualTo(OIDC_ID);
            assertThat(result.firstName()).isEqualTo(FIRST_NAME);
        }

        @Test
        void should_returnFallbackIdentity_when_keycloakThrows() {
            @SuppressWarnings("unchecked")
            io.quarkus.hibernate.orm.panache.PanacheQuery<UserProfile> q =
                    mock(io.quarkus.hibernate.orm.panache.PanacheQuery.class);
            when(UserProfile.<UserProfile>find(anyString(), any(Object[].class))).thenReturn(q);
            when(q.firstResultOptional()).thenReturn(Optional.empty());
            when(userResource.toRepresentation()).thenThrow(new RuntimeException("Keycloak down"));

            UserIdentity result = adapter.findById(OIDC_ID);

            assertThat(result.oidcId()).isEqualTo(OIDC_ID);
            assertThat(result.firstName()).isNull();
        }
    }

    // ── findByIds ───────────────────────────────────────────────────────────

    @Nested
    class FindByIds {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(UserProfile.class);
        }

        @Test
        void should_returnEmptyMap_when_idsIsEmpty() {
            Map<String, UserIdentity> result = adapter.findByIds(List.of());

            assertThat(result).isEmpty();
            PanacheMock.verifyNoInteractions(UserProfile.class);
        }

        @Test
        void should_returnAllFromDb_when_allIdsAreCached() {
            String oidc2 = "oidc-def-456";
            UserProfile p1 = buildProfile();
            UserProfile p2 = new UserProfile();
            p2.oidcId = oidc2;
            p2.firstName = "Anna";
            p2.lastName = "Schmidt";
            p2.email = "anna@example.com";

            when(UserProfile.<UserProfile>list(anyString(), any(Object[].class)))
                    .thenReturn(List.of(p1, p2));

            Map<String, UserIdentity> result = adapter.findByIds(List.of(OIDC_ID, oidc2));

            assertThat(result).hasSize(2);
            assertThat(result.get(OIDC_ID).firstName()).isEqualTo(FIRST_NAME);
            assertThat(result.get(oidc2).firstName()).isEqualTo("Anna");
            verifyNoInteractions(keycloak);
        }

        @Test
        void should_fetchMissingFromKeycloak_when_notAllAreCached() {
            String missingId = "oidc-missing";
            UserProfile cached = buildProfile();
            when(UserProfile.<UserProfile>list(anyString(), any(Object[].class)))
                    .thenReturn(List.of(cached));
            when(UserProfile.update(anyString(), any(Object[].class))).thenReturn(0);

            UserResource missingResource = mock(UserResource.class);
            when(usersResource.get(missingId)).thenReturn(missingResource);
            UserRepresentation rep = buildRep(missingId, "New", "User", "new@example.com");
            when(missingResource.toRepresentation()).thenReturn(rep);

            Map<String, UserIdentity> result = adapter.findByIds(List.of(OIDC_ID, missingId));

            assertThat(result).hasSize(2);
            assertThat(result.get(missingId).firstName()).isEqualTo("New");
        }
    }

    // ── search ──────────────────────────────────────────────────────────────

    @Nested
    class Search {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(UserProfile.class);
        }

        @Test
        void should_searchKeycloakAndCacheResults() {
            UserRepresentation rep = buildRep(OIDC_ID, FIRST_NAME, LAST_NAME, EMAIL);
            when(usersResource.search(anyString(), eq(0), eq(10))).thenReturn(List.of(rep));
            when(UserProfile.update(anyString(), any(Object[].class))).thenReturn(1);

            List<UserIdentity> result = adapter.search("max", 10);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).oidcId()).isEqualTo(OIDC_ID);
            assertThat(result.get(0).firstName()).isEqualTo(FIRST_NAME);
        }

        @Test
        void should_returnEmptyList_when_keycloakReturnsNoResults() {
            when(usersResource.search(anyString(), eq(0), eq(5))).thenReturn(List.of());

            List<UserIdentity> result = adapter.search("nobody", 5);

            assertThat(result).isEmpty();
        }
    }

    // ── upsert ──────────────────────────────────────────────────────────────

    @Nested
    class Upsert {

        @BeforeEach
        void mockPanache() {
            PanacheMock.mock(UserProfile.class);
        }

        @Test
        void should_doNothing_when_oidcIdIsNull() {
            UserIdentity identity = new UserIdentity(null, "John", "Doe", "john@example.com", null);

            adapter.upsert(identity);

            PanacheMock.verifyNoInteractions(UserProfile.class);
        }

        @Test
        void should_updateExistingProfile_when_profileExists() {
            when(UserProfile.update(anyString(), any(Object[].class))).thenReturn(1);
            UserIdentity identity = new UserIdentity(OIDC_ID, FIRST_NAME, LAST_NAME, EMAIL, PICTURE_URL);

            adapter.upsert(identity);

            PanacheMock.verify(UserProfile.class).update(anyString(), any(Object[].class));
        }

        @Test
        void should_insertNewProfile_when_noExistingProfile() {
            when(UserProfile.update(anyString(), any(Object[].class))).thenReturn(0);
            UserIdentity identity = new UserIdentity(OIDC_ID, FIRST_NAME, LAST_NAME, EMAIL, PICTURE_URL);

            adapter.upsert(identity);

            PanacheMock.verify(UserProfile.class).update(anyString(), any(Object[].class));
        }
    }
}
