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
 * The sole class allowed to import Keycloak types for identity reads.
 * All other domain/application classes depend only on IdentityPort and UserIdentity.
 *
 * Read path:   user_profiles table (single IN query for batches)
 *              → Keycloak Admin API fallback on cache miss (parallel HTTP, sequential persist)
 * Write path:  JWT claims upserted on every authenticated request (REQUIRES_NEW transaction)
 * Search path: Keycloak Admin API (complete result set), results cached as side effect
 */
@ApplicationScoped
public class LocalDbIdentityAdapter implements IdentityPort {

    private static final Logger LOGGER = Logger.getLogger(LocalDbIdentityAdapter.class);

    @Inject
    Keycloak keycloak;

    @ConfigProperty(name = "quarkus.keycloak.admin-client.realm")
    String realm;

    // ── Reads ────────────────────────────────────────────────────────────────

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

        // Single IN query — avoids N+1
        Map<String, UserIdentity> result = new HashMap<>();
        UserProfile.<UserProfile>list("oidcId IN ?1", ids)
                .forEach(p -> result.put(p.oidcId, toIdentity(p)));

        // Fetch cache misses from Keycloak in parallel (HTTP only, no JPA in parallel)
        List<String> missing = ids.stream()
                .filter(id -> !result.containsKey(id))
                .toList();

        if (!missing.isEmpty()) {
            // HTTP-only: parallel Keycloak calls are safe; no JPA inside this stream
            List<UserIdentity> fetched = missing.parallelStream()
                    .map(this::fetchFromKeycloak)
                    .filter(Objects::nonNull)
                    .toList();
            // JPA writes run sequentially on the calling thread within the current transaction
            for (UserIdentity identity : fetched) {
                doUpsert(identity);
                result.put(identity.oidcId(), identity);
            }
        }

        return result;
    }

    @Override
    @Transactional
    public List<UserIdentity> search(String query, int limit) {
        // Keycloak is authoritative for search — finds users not yet in local cache
        List<UserIdentity> results = keycloak.realm(realm).users().search(query, 0, limit)
                .stream()
                .map(this::repToIdentity)
                .toList();
        // Cache results as a side effect so future reads avoid Keycloak
        results.forEach(this::doUpsert);
        return results;
    }

    // ── Writes ───────────────────────────────────────────────────────────────

    /**
     * Public upsert runs in its own transaction so the filter's DB write
     * is independent of the main request transaction.
     */
    @Override
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void upsert(UserIdentity identity) {
        if (identity.oidcId() == null) {
            return;
        }
        doUpsert(identity);
    }

    // ── Internals ────────────────────────────────────────────────────────────

    /** Upsert logic without a transaction annotation — called within an existing TX. */
    private void doUpsert(UserIdentity identity) {
        int updated = UserProfile.update(
                "firstName = ?1, lastName = ?2, email = ?3, profilePictureUrl = ?4, updatedAt = ?5"
                        + " WHERE oidcId = ?6",
                identity.firstName(), identity.lastName(), identity.email(),
                identity.profilePictureUrl(), Instant.now(), identity.oidcId()
        );
        if (updated == 0) {
            UserProfile p = new UserProfile();
            p.oidcId = identity.oidcId();
            p.firstName = identity.firstName();
            p.lastName = identity.lastName();
            p.email = identity.email();
            p.profilePictureUrl = identity.profilePictureUrl();
            p.updatedAt = Instant.now();
            UserProfile.persist(p);
        }
    }

    private UserIdentity fetchFromKeycloakAndCache(String oidcId) {
        UserIdentity identity = fetchFromKeycloak(oidcId);
        if (identity != null) {
            doUpsert(identity);
            return identity;
        }
        return new UserIdentity(oidcId, null, null, null, null);
    }

    private UserIdentity fetchFromKeycloak(String oidcId) {
        try {
            return repToIdentity(keycloak.realm(realm).users().get(oidcId).toRepresentation());
        } catch (Exception e) {
            LOGGER.warnf("Failed to fetch user %s from Keycloak: %s", oidcId, e.getMessage());
            return null;
        }
    }

    private UserIdentity repToIdentity(UserRepresentation rep) {
        return new UserIdentity(
                rep.getId(),
                rep.getFirstName(),
                rep.getLastName(),
                rep.getEmail(),
                rep.getAttributes() != null
                        ? rep.getAttributes().getOrDefault("picture", List.of())
                                .stream().findFirst().orElse(null)
                        : null
        );
    }

    private UserIdentity toIdentity(UserProfile p) {
        return new UserIdentity(p.oidcId, p.firstName, p.lastName, p.email, p.profilePictureUrl);
    }
}
