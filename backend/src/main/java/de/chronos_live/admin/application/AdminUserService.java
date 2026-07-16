package de.chronos_live.admin.application;

import de.chronos_live.admin.dto.AdminUserDto;
import de.chronos_live.admin.dto.AdminUserListResponse;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.UserRepresentation;

import java.util.List;

/**
 * User listing now delegates entirely to Keycloak Admin API.
 * No users table needed — Keycloak is the source of truth.
 */
@ApplicationScoped
@Transactional
@Timed("service.admin.users")
public class AdminUserService {

    @Inject
    Keycloak keycloak;

    @ConfigProperty(name = "quarkus.keycloak.admin-client.realm")
    String realm;

    public AdminUserListResponse listUsers(int page, int size) {
        int first = page * size;
        List<UserRepresentation> users = keycloak.realm(realm).users().list(first, size);
        long total = keycloak.realm(realm).users().count();

        List<AdminUserDto> items = users.stream()
                .map(u -> new AdminUserDto(
                        u.getId(),
                        u.getFirstName(),
                        u.getLastName(),
                        u.getEmail(),
                        u.getCreatedTimestamp() != null ? Instant.ofEpochMilli(u.getCreatedTimestamp()) : null
                ))
                .toList();

        return new AdminUserListResponse(items, page, size, total);
    }

    public AdminUserDto getUserByOidcId(String oidcId) {
        UserRepresentation u = keycloak.realm(realm).users().get(oidcId).toRepresentation();
        return new AdminUserDto(
                u.getId(),
                u.getFirstName(),
                u.getLastName(),
                u.getEmail(),
                u.getCreatedTimestamp() != null ? Instant.ofEpochMilli(u.getCreatedTimestamp()) : null
        );
    }
}
