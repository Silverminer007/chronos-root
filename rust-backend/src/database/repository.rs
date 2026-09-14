use async_trait::async_trait;
use uuid::Uuid;

/// Base repository trait defining common CRUD operations
#[async_trait]
pub trait Repository<T: Send + Sync>: Send + Sync {
    /// Find an entity by its ID
    async fn find_by_id(&self, id: Uuid) -> Result<Option<T>, RepositoryError>;

    /// Find all entities
    async fn find_all(&self) -> Result<Vec<T>, RepositoryError>;

    /// Create a new entity
    async fn create(&self, entity: T) -> Result<T, RepositoryError>;

    /// Update an existing entity
    async fn update(&self, id: Uuid, entity: T) -> Result<Option<T>, RepositoryError>;

    /// Delete an entity
    async fn delete(&self, id: Uuid) -> Result<bool, RepositoryError>;
}

/// Repository errors
#[derive(Debug, Clone)]
pub enum RepositoryError {
    NotFound,
    InvalidInput(String),
    DatabaseError(String),
    ConflictError(String),
}

impl std::fmt::Display for RepositoryError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            RepositoryError::NotFound => write!(f, "Entity not found"),
            RepositoryError::InvalidInput(msg) => write!(f, "Invalid input: {}", msg),
            RepositoryError::DatabaseError(msg) => write!(f, "Database error: {}", msg),
            RepositoryError::ConflictError(msg) => write!(f, "Conflict: {}", msg),
        }
    }
}

impl std::error::Error for RepositoryError {}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_repository_error_display() {
        let err = RepositoryError::NotFound;
        assert_eq!(err.to_string(), "Entity not found");

        let err = RepositoryError::InvalidInput("invalid_field".to_string());
        assert!(err.to_string().contains("Invalid input"));
    }
}
