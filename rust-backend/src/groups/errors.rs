use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde_json::json;
use std::fmt;

/// Domain-specific error type for group operations
#[derive(Debug)]
pub enum GroupServiceError {
    NotFound(String),
    NotAuthorized(String),
    InvalidOperation(String),
    DatabaseError(String),
}

impl fmt::Display for GroupServiceError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            GroupServiceError::NotFound(msg) => write!(f, "Not found: {}", msg),
            GroupServiceError::NotAuthorized(msg) => write!(f, "Not authorized: {}", msg),
            GroupServiceError::InvalidOperation(msg) => write!(f, "Invalid operation: {}", msg),
            GroupServiceError::DatabaseError(msg) => write!(f, "Database error: {}", msg),
        }
    }
}

impl std::error::Error for GroupServiceError {}

impl IntoResponse for GroupServiceError {
    fn into_response(self) -> Response {
        let (status, message) = match self {
            GroupServiceError::NotFound(ref msg) => (StatusCode::NOT_FOUND, msg.clone()),
            GroupServiceError::NotAuthorized(_) => {
                (StatusCode::FORBIDDEN, "Not authorized to perform this action".to_string())
            }
            GroupServiceError::InvalidOperation(ref msg) => (StatusCode::BAD_REQUEST, msg.clone()),
            GroupServiceError::DatabaseError(_) => {
                (StatusCode::INTERNAL_SERVER_ERROR, "Database error".to_string())
            }
        };

        (status, Json(json!({"error": message}))).into_response()
    }
}

impl From<sqlx::Error> for GroupServiceError {
    fn from(err: sqlx::Error) -> Self {
        GroupServiceError::DatabaseError(err.to_string())
    }
}

impl From<String> for GroupServiceError {
    fn from(msg: String) -> Self {
        GroupServiceError::InvalidOperation(msg)
    }
}

impl From<&str> for GroupServiceError {
    fn from(msg: &str) -> Self {
        GroupServiceError::InvalidOperation(msg.to_string())
    }
}

/// Friendship status enumeration
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum FriendshipStatus {
    Pending,
    Accepted,
    Declined,
}

impl fmt::Display for FriendshipStatus {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            FriendshipStatus::Pending => write!(f, "PENDING"),
            FriendshipStatus::Accepted => write!(f, "ACCEPTED"),
            FriendshipStatus::Declined => write!(f, "DECLINED"),
        }
    }
}

impl From<&str> for FriendshipStatus {
    fn from(s: &str) -> Self {
        match s {
            "ACCEPTED" => FriendshipStatus::Accepted,
            "DECLINED" => FriendshipStatus::Declined,
            _ => FriendshipStatus::Pending,
        }
    }
}

impl From<String> for FriendshipStatus {
    fn from(s: String) -> Self {
        FriendshipStatus::from(s.as_str())
    }
}
