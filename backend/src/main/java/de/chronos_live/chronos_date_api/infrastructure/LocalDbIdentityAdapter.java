package de.chronos_live.chronos_date_api.infrastructure;

import de.chronos_live.chronos_date_api.application.ports.IdentityPort;
import de.chronos_live.chronos_date_api.domain.UserIdentity;
import de.chronos_live.chronos_date_api.domain.UserProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ServiceUnavailableException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.UserRepresentation;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The sole class allowed to import Keycloak types for identity reads.
 * All other domain/application classes depend only on IdentityPort and UserIdentity.
 *
 * Read path:   user_profiles table (single IN query for batches)
 *              → Keycloak Admin API fallback on cache miss (sequential, then persisted via REQUIRES_NEW)
 * Write path:  JWT claims upserted on every authenticated request (REQUIRES_NEW transaction)
 * Search path: Keycloak Admin API (complete result set), results cached as side effect
 */
@ApplicationScoped
public class LocalDbIdentityAdapter implements IdentityPort {

    private static final Logger LOGGER = Logger.getLogger(LocalDbIdentityAdapter.class);

    @Inject
    Keycloak keycloak;

    // Self-injection so that internal callers go through the CDI proxy,
    // ensuring @Transactional(REQUIRES_NEW) on upsert() is honoured.
    @Inject
    LocalDbIdentityAdapter self;

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
            // fetchFromKeycloak throws ServiceUnavailableException on infrastructure failure;
            // it never returns null — deleted users come back as a sentinel.
            List<UserIdentity> fetched = missing.stream()
                    .map(this::fetchFromKeycloak)
                    .toList();
            // Each upsert runs in its own REQUIRES_NEW transaction via the CDI proxy,
            // so cache writes survive an outer transaction rollback.
            // Deleted sentinels are not persisted — Keycloak remains authoritative for absence.
            for (UserIdentity identity : fetched) {
                if (!identity.isDeleted()) {
                    self.upsert(identity);
                }
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
        // Cache results as a side effect so future reads avoid Keycloak.
        // Each upsert runs in its own REQUIRES_NEW transaction via the CDI proxy.
        results.forEach(self::upsert);
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

    /** Atomic upsert via INSERT ... ON CONFLICT to avoid the UPDATE-then-INSERT race on first login. */
    private void doUpsert(UserIdentity identity) {
        UserProfile.getEntityManager().createNativeQuery(
                "INSERT INTO user_profiles (oidc_id, first_name, last_name, email, profile_picture_url, updated_at) "
                + "VALUES (:oidcId, :firstName, :lastName, :email, :profilePictureUrl, :updatedAt) "
                + "ON CONFLICT (oidc_id) DO UPDATE SET "
                + "first_name = EXCLUDED.first_name, last_name = EXCLUDED.last_name, "
                + "email = EXCLUDED.email, profile_picture_url = EXCLUDED.profile_picture_url, "
                + "updated_at = EXCLUDED.updated_at")
                .setParameter("oidcId", identity.oidcId())
                .setParameter("firstName", identity.firstName())
                .setParameter("lastName", identity.lastName())
                .setParameter("email", identity.email())
                .setParameter("profilePictureUrl", identity.profilePictureUrl())
                .setParameter("updatedAt", java.sql.Timestamp.from(Instant.now()))
                .executeUpdate();
    }

    @Override
    @Transactional
    public boolean existsById(String oidcId) {
        if (UserProfile.count("oidcId", oidcId) > 0) return true;
        return fetchFromKeycloak(oidcId) != null;
    }

    private UserIdentity fetchFromKeycloakAndCache(String oidcId) {
        UserIdentity identity = fetchFromKeycloak(oidcId);
        if (!identity.isDeleted()) {
            self.upsert(identity);
        }
        return identity;
    }

    private UserIdentity fetchFromKeycloak(String oidcId) {
        try {
            return repToIdentity(keycloak.realm(realm).users().get(oidcId).toRepresentation());
        } catch (NotFoundException e) {
            LOGGER.infof("User %s not found in Keycloak — returning deleted sentinel", oidcId);
            return UserIdentity.deleted(oidcId);
        } catch (Exception e) {
            LOGGER.errorf("Keycloak unavailable while fetching user %s: %s", oidcId, e.getMessage());
            throw new ServiceUnavailableException("Keycloak nicht erreichbar");
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
