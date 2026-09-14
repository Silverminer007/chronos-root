/// Test HTTP client wrapper for integration tests
pub struct TestHttpClient {
    base_url: String,
    client: reqwest::Client,
}

impl TestHttpClient {
    /// Create a new test HTTP client
    pub fn new(base_url: String) -> Self {
        Self {
            base_url,
            client: reqwest::Client::new(),
        }
    }

    /// Make a GET request
    pub async fn get(&self, path: &str) -> Result<reqwest::Response, reqwest::Error> {
        self.client
            .get(format!("{}{}", self.base_url, path))
            .send()
            .await
    }

    /// Make a GET request with Authorization header
    pub async fn get_with_auth(
        &self,
        path: &str,
        auth_header: &str,
    ) -> Result<reqwest::Response, reqwest::Error> {
        self.client
            .get(format!("{}{}", self.base_url, path))
            .header("Authorization", auth_header)
            .send()
            .await
    }

    /// Make a POST request
    pub async fn post<T: serde::Serialize>(
        &self,
        path: &str,
        body: T,
    ) -> Result<reqwest::Response, reqwest::Error> {
        self.client
            .post(format!("{}{}", self.base_url, path))
            .json(&body)
            .send()
            .await
    }

    /// Make a POST request with Authorization header
    pub async fn post_with_auth<T: serde::Serialize>(
        &self,
        path: &str,
        body: T,
        auth_header: &str,
    ) -> Result<reqwest::Response, reqwest::Error> {
        self.client
            .post(format!("{}{}", self.base_url, path))
            .header("Authorization", auth_header)
            .json(&body)
            .send()
            .await
    }

    /// Make a PUT request
    pub async fn put<T: serde::Serialize>(
        &self,
        path: &str,
        body: T,
    ) -> Result<reqwest::Response, reqwest::Error> {
        self.client
            .put(format!("{}{}", self.base_url, path))
            .json(&body)
            .send()
            .await
    }

    /// Make a PUT request with Authorization header
    pub async fn put_with_auth<T: serde::Serialize>(
        &self,
        path: &str,
        body: T,
        auth_header: &str,
    ) -> Result<reqwest::Response, reqwest::Error> {
        self.client
            .put(format!("{}{}", self.base_url, path))
            .header("Authorization", auth_header)
            .json(&body)
            .send()
            .await
    }

    /// Make a DELETE request
    pub async fn delete(&self, path: &str) -> Result<reqwest::Response, reqwest::Error> {
        self.client
            .delete(format!("{}{}", self.base_url, path))
            .send()
            .await
    }

    /// Make a DELETE request with Authorization header
    pub async fn delete_with_auth(
        &self,
        path: &str,
        auth_header: &str,
    ) -> Result<reqwest::Response, reqwest::Error> {
        self.client
            .delete(format!("{}{}", self.base_url, path))
            .header("Authorization", auth_header)
            .send()
            .await
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_http_client_creation() {
        let client = TestHttpClient::new("http://localhost:8000".to_string());
        assert_eq!(client.base_url, "http://localhost:8000");
    }

    #[test]
    fn test_http_client_url_construction() {
        let client = TestHttpClient::new("http://localhost:8000".to_string());
        // Just verify it can be constructed - actual HTTP calls are tested in integration tests
        assert!(!client.base_url.is_empty());
    }
}
