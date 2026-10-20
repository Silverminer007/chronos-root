//! Browser-facing Keycloak URLs (account linking, passkeys, password change, email verification).
//! Java: the `get*Url` methods of `KeycloakProfileService`.

/// Builds the authorization URLs that trigger a Keycloak required action (`kc_action`)
#[derive(Debug, Clone)]
pub struct KeycloakUrls {
    base_url: String,
    realm: String,
    client_id: String,
}

impl KeycloakUrls {
    pub fn new(base_url: &str, realm: &str, client_id: &str) -> Self {
        Self {
            base_url: base_url.to_string(),
            realm: realm.to_string(),
            client_id: client_id.to_string(),
        }
    }

    /// Java: `getLinkUrl` (the provider alias is appended unencoded, like in Java)
    pub fn link_url(&self, provider: &str, redirect_uri: &str) -> String {
        self.action_url(redirect_uri, &format!("idp_link:{}", provider))
    }

    pub fn passkey_registration_url(&self, redirect_uri: &str) -> String {
        self.action_url(redirect_uri, "webauthn-register-passwordless")
    }

    pub fn delete_passkey_url(&self, redirect_uri: &str, passkey_id: &str) -> String {
        self.action_url(redirect_uri, &format!("delete_credential:{}", passkey_id))
    }

    pub fn change_password_url(&self, redirect_uri: &str) -> String {
        self.action_url(redirect_uri, "UPDATE_PASSWORD")
    }

    pub fn verify_email_url(&self, redirect_uri: &str) -> String {
        self.action_url(redirect_uri, "VERIFY_EMAIL")
    }

    fn action_url(&self, redirect_uri: &str, kc_action: &str) -> String {
        format!(
            "{}/realms/{}/protocol/openid-connect/auth?client_id={}&redirect_uri={}&response_type=code&scope=openid&kc_action={}",
            self.base_url,
            self.realm,
            self.client_id,
            java_url_encode(redirect_uri),
            kc_action
        )
    }
}

/// Equivalent of Java's `URLEncoder.encode(value, UTF_8)` (`application/x-www-form-urlencoded`):
/// keeps `a-z A-Z 0-9 . - * _`, turns a space into `+`, percent-encodes everything else (uppercase hex).
pub fn java_url_encode(value: &str) -> String {
    let mut encoded = String::with_capacity(value.len());
    for byte in value.bytes() {
        match byte {
            b'a'..=b'z' | b'A'..=b'Z' | b'0'..=b'9' | b'.' | b'-' | b'*' | b'_' => {
                encoded.push(byte as char)
            }
            b' ' => encoded.push('+'),
            _ => encoded.push_str(&format!("%{:02X}", byte)),
        }
    }
    encoded
}

#[cfg(test)]
mod tests {
    use super::*;

    fn urls() -> KeycloakUrls {
        KeycloakUrls::new("https://auth.example.org", "chronos", "chronos-api")
    }

    #[test]
    fn java_url_encode_matches_url_encoder() {
        assert_eq!(java_url_encode("abcXYZ019.-*_"), "abcXYZ019.-*_");
        assert_eq!(java_url_encode("a b"), "a+b");
        assert_eq!(
            java_url_encode("https://app.example/cb?x=1&y=~"),
            "https%3A%2F%2Fapp.example%2Fcb%3Fx%3D1%26y%3D%7E"
        );
        assert_eq!(java_url_encode("ä€"), "%C3%A4%E2%82%AC");
        assert_eq!(java_url_encode(""), "");
    }

    #[test]
    fn link_url_has_the_java_shape() {
        assert_eq!(
            urls().link_url("github", "https://app.example/profile"),
            "https://auth.example.org/realms/chronos/protocol/openid-connect/auth?client_id=chronos-api&redirect_uri=https%3A%2F%2Fapp.example%2Fprofile&response_type=code&scope=openid&kc_action=idp_link:github"
        );
    }

    #[test]
    fn required_action_urls_use_their_kc_action() {
        let u = urls();
        let r = "https://app.example/p";
        assert!(u
            .passkey_registration_url(r)
            .ends_with("&kc_action=webauthn-register-passwordless"));
        assert!(u
            .delete_passkey_url(r, "cred-1")
            .ends_with("&kc_action=delete_credential:cred-1"));
        assert!(u
            .change_password_url(r)
            .ends_with("&kc_action=UPDATE_PASSWORD"));
        assert!(u.verify_email_url(r).ends_with("&kc_action=VERIFY_EMAIL"));
    }
}
