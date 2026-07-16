package de.chronos_live.chronos_date_api.application.ports;

import de.chronos_live.chronos_date_api.domain.UserIdentity;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Anti-corruption layer between the domain and the identity provider (Keycloak).
 * The domain never imports Keycloak types — only this interface.
 */
public interface IdentityPort {

    /**
     * Resolves a single user by oidcId. Reads from the local cache (user_profiles),
     * falling back to the identity provider on a cache miss.
     */
    UserIdentity findById(String oidcId);

    /**
     * Resolves multiple users in a single operation.
     * Reads from the local cache using one IN query; fetches cache misses from
     * the identity provider in parallel, then persists them.
     * Returns a map keyed by oidcId for O(1) lookup by callers.
     */
    Map<String, UserIdentity> findByIds(Collection<String> oidcIds);

    /**
     * Writes (or refreshes) a user profile into the local cache.
     * Called on every authenticated request with the JWT claims to keep names current.
     */
    void upsert(UserIdentity identity);

    /**
     * Returns true if a user with the given oidcId exists in the local cache or the identity provider.
     * Used to validate that a target user exists before performing actions on their behalf.
     */
    boolean existsById(String oidcId);

    /**
     * Searches for users by name or email. Delegates to the identity provider to ensure
     * completeness (users not yet in local cache are still findable), and caches results.
     */
    List<UserIdentity> search(String query, int limit);
}
