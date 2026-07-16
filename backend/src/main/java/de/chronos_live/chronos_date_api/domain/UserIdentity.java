package de.chronos_live.chronos_date_api.domain;

/**
 * Represents a resolved user identity.
 * Use {@link #deleted(String)} to construct a sentinel for users removed from Keycloak.
 */
public record UserIdentity(
        String oidcId,
        String firstName,
        String lastName,
        String email,
        String profilePictureUrl
) {
    /** Sentinel for a Keycloak user that no longer exists (404). Never persisted to user_profiles. */
    public static UserIdentity deleted(String oidcId) {
        return new UserIdentity(oidcId, null, null, null, null);
    }

    /** True when this identity represents a user deleted from Keycloak. */
    public boolean isDeleted() {
        return firstName == null && lastName == null && email == null;
    }

    public String getName() {
        if (isDeleted()) return "Gelöschter Benutzer";
        return ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).strip();
    }
}