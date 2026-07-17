package de.chronos_live.chronos_date_api.domain;

/**
 * Represents the currently authenticated user, populated purely from the Keycloak JWT.
 * No database lookup required — Keycloak is the source of truth.
 */
public record UserIdentity(
        String oidcId,
        String firstName,
        String lastName,
        String email,
        String profilePictureUrl
) {
    public String getName() {
        return firstName + " " + lastName;
    }
}