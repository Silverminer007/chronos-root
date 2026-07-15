package de.chronos_live.chronos_date_api.infrastructure;

import de.chronos_live.chronos_date_api.application.ports.IdentityPort;
import de.chronos_live.chronos_date_api.domain.UserIdentity;
import de.chronos_live.chronos_date_api.domain.UserProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.UserRepresentation;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Implements IdentityPort against a local PostgreSQL cache (user_profiles table).
 * Cache misses fall back to the Keycloak Admin API and are persisted automatically.
 * This is the only class in the codebase that imports Keycloak types — all other
 * classes depend only on UserIdentity and IdentityPort.
 */
@ApplicationScoped
public class LocalDbIdentityAdapter implements IdentityPort {

    private static final Logger LOGGER = Logger.getLogger(LocalDbIdentityAdapter.class);

    @Inject
    Keycloak keycloak;

    @ConfigProperty(name = "quarkus.keycloak.admin-client.realm")
    String realm;

    @Override
    @Transactional
    public UserIdentity findById(String oidcId) {
        return UserProfile.<UserProfile>find("oidcId", oidcId)
                .firstResultOptional()
                .map(this::toIdentity)
                .orElseGet(() -> fetchFromKeycloakAndCache(oidcId));
    }

    @Override
    @Transactional
    public Map<String, UserIdentity> findByIds(Collection<String> oidcIds) {
        List<String> ids = oidcIds.stream().distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }

        // Single DB query for all cached profiles — avoids N+1
        Map<String, UserIdentity> result = new HashMap<>();
        UserProfile.<UserProfile>list("oidcId IN ?1", ids)
                .forEach(p -> result.put(p.oidcId, toIdentity(p)));

        // Fetch cache misses from Keycloak in parallel (HTTP only, no JPA in parallel)
        List<String> missing = ids.stream()
                .filter(id -> !result.containsKey(id))
                .toList();

        if (!missing.isEmpty()) {
            List<UserIdentity> fetched = missing.parallelStream()
                    .map(this::fetchFromKeycloak)
                    .filter(Objects::nonNull)
                    .toList();
            // Persist all cache misses sequentially in the current transaction
            fetched.forEach(identity -> {
                persistProfile(identity);
                result.put(identity.oidcId(), identity);
            });
        }

        return result;
    }

    @Override
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void upsert(UserIdentity identity) {
        if (identity.oidcId() == null) {
            return;
        }
        int updated = UserProfile.update(
                "firstName = ?1, lastName = ?2, email = ?3, profilePictureUrl = ?4, updatedAt = ?5"
                        + " WHERE oidcId = ?6",
                identity.firstName(), identity.lastName(), identity.email(),
                identity.profilePictureUrl(), Instant.now(), identity.oidcId()
        );
        if (updated == 0) {
            persistProfile(identity);
        }
    }

    private void persistProfile(UserIdentity identity) {
        UserProfile p = new UserProfile();
        p.oidcId = identity.oidcId();
        p.firstName = identity.firstName();
        p.lastName = identity.lastName();
        p.email = identity.email();
        p.profilePictureUrl = identity.profilePictureUrl();
        p.updatedAt = Instant.now();
        p.persist();
    }

    private UserIdentity fetchFromKeycloakAndCache(String oidcId) {
        UserIdentity identity = fetchFromKeycloak(oidcId);
        if (identity != null) {
            persistProfile(identity);
        }
        return identity != null ? identity : new UserIdentity(oidcId, null, null, null, null);
    }

    private UserIdentity fetchFromKeycloak(String oidcId) {
        try {
            UserRepresentation rep = keycloak.realm(realm).users().get(oidcId).toRepresentation();
            return new UserIdentity(
                    rep.getId(),
                    rep.getFirstName(),
                    rep.getLastName(),
                    rep.getEmail(),
                    rep.getAttributes() != null
                            ? rep.getAttributes().getOrDefault("picture", List.of()).stream()
                                    .findFirst().orElse(null)
                            : null
            );
        } catch (Exception e) {
            LOGGER.warnf("Failed to fetch user %s from Keycloak: %s", oidcId, e.getMessage());
            return null;
        }
    }

    private UserIdentity toIdentity(UserProfile p) {
        return new UserIdentity(p.oidcId, p.firstName, p.lastName, p.email, p.profilePictureUrl);
    }
}
