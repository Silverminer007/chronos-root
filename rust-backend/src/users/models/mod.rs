//! Users are identified by their OIDC subject; their profile is cached in `user_profiles`
//! (ADR-2026-10-12-014). There is no `users` table.

pub use crate::appointments::models::UserProfile;

#[cfg(test)]
mod tests {
    use super::*;
    use chrono::Utc;

    #[test]
    fn test_user_profile_is_keyed_by_oidc_subject() {
        let profile = UserProfile {
            oidc_id: "keycloak-sub-1".to_string(),
            first_name: Some("John".to_string()),
            last_name: Some("Doe".to_string()),
            email: Some("user@example.com".to_string()),
            profile_picture_url: None,
            updated_at: Utc::now(),
        };

        assert_eq!(profile.oidc_id, "keycloak-sub-1");
        assert!(profile.first_name.is_some());
    }

    #[test]
    fn test_user_profile_without_name() {
        let profile = UserProfile {
            oidc_id: "keycloak-sub-2".to_string(),
            first_name: None,
            last_name: None,
            email: None,
            profile_picture_url: None,
            updated_at: Utc::now(),
        };

        assert!(profile.first_name.is_none());
        assert!(profile.last_name.is_none());
    }
}
