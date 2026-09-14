// Integration tests for database layer
#[cfg(test)]
mod database_tests {
    use chronos_date_api::database::{init_pool, DatabaseConfig};
    use chronos_date_api::database::repository::{Repository, RepositoryError};

    #[test]
    fn test_pool_memory_efficient() {
        let config = DatabaseConfig::default();
        assert!(config.max_connections <= 32, "Connection pool too large");
    }

    #[test]
    fn test_pool_custom_config() {
        let config = DatabaseConfig {
            url: "postgres://user:pass@host:5432/db".to_string(),
            max_connections: 8,
            min_idle: Some(1),
            connection_timeout: std::time::Duration::from_secs(10),
            max_lifetime: std::time::Duration::from_secs(3600),
        };

        assert_eq!(config.max_connections, 8);
        assert_eq!(config.min_idle, Some(1));
    }

    #[test]
    fn test_repository_error_types() {
        let err = RepositoryError::NotFound;
        assert_eq!(err.to_string(), "Entity not found");

        let err = RepositoryError::InvalidInput("test".to_string());
        assert!(err.to_string().contains("Invalid input"));

        let err = RepositoryError::DatabaseError("connection failed".to_string());
        assert!(err.to_string().contains("Database error"));
    }

    #[test]
    fn test_connection_timeout_config() {
        let config = DatabaseConfig::default();
        assert_eq!(config.connection_timeout.as_secs(), 5);
    }

    #[test]
    fn test_max_lifetime_config() {
        let config = DatabaseConfig::default();
        assert_eq!(config.max_lifetime.as_secs(), 1800);
    }
}
