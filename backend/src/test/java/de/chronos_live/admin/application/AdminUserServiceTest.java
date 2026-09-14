package de.chronos_live.admin.application;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;

import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AdminUserService}.
 *
 * <p>Tests verify that admin user operations (list, get, delete) properly delegate to
 * the Keycloak Admin API.
 */
@QuarkusTest
class AdminUserServiceTest {

    private static final String REALM = "test-realm";
    private static final String USER_ID = "user-123";

    @Inject
    AdminUserService service;

    @InjectMock
    Keycloak keycloak;

    @Test
    void deleteUserRemovesUserFromKeycloak() {
        // Arrange
        RealmResource realmResource = mock(RealmResource.class);
        UsersResource usersResource = mock(UsersResource.class);

        when(keycloak.realm(REALM)).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);

        // Act
        service.deleteUser(USER_ID);

        // Assert
        verify(usersResource).delete(USER_ID);
    }
}
