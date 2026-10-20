use axum::{
    async_trait,
    extract::{
        rejection::{JsonRejection, PathRejection},
        FromRequest, FromRequestParts, Request,
    },
    http::{request::Parts, StatusCode},
    middleware::Next,
    response::{IntoResponse, Response},
    Json,
};
use chrono::Local;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;

/// Standard error response for all API errors
/// Matches the Java backend error response format exactly
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ErrorResponse {
    /// ISO 8601 timestamp of the error occurrence
    pub timestamp: String,
    /// HTTP status code
    pub status: u16,
    /// HTTP status text (e.g., "Not Found", "Forbidden")
    pub error: String,
    /// Machine-readable error code (e.g., "RESOURCE_NOT_FOUND", "FORBIDDEN")
    /// Omitted when empty (Java: errors that do not come from a `BusinessException` have no code)
    #[serde(
        rename = "errorCode",
        default,
        skip_serializing_if = "String::is_empty"
    )]
    pub error_code: String,
    /// Human-readable error message in German
    pub message: String,
    /// The request path that caused the error
    pub path: String,
    /// Field-specific validation errors (optional)
    #[serde(skip_serializing_if = "Option::is_none")]
    #[serde(rename = "fieldErrors")]
    pub field_errors: Option<HashMap<String, String>>,
}

impl ErrorResponse {
    /// Create a new error response
    pub fn new(
        status: u16,
        error: String,
        error_code: String,
        message: String,
        path: String,
    ) -> Self {
        Self {
            timestamp: Local::now().format("%Y-%m-%dT%H:%M:%S%.3f").to_string(),
            status,
            error,
            error_code,
            message,
            path,
            field_errors: None,
        }
    }

    /// Add field-specific validation errors
    pub fn with_field_errors(mut self, field_errors: HashMap<String, String>) -> Self {
        self.field_errors = Some(field_errors);
        self
    }

    /// Builder for fluent construction
    pub fn builder() -> ErrorResponseBuilder {
        ErrorResponseBuilder::new()
    }
}

/// Builder for ErrorResponse
pub struct ErrorResponseBuilder {
    status: u16,
    error: String,
    error_code: String,
    message: String,
    path: String,
    field_errors: Option<HashMap<String, String>>,
}

impl ErrorResponseBuilder {
    /// Create a new builder
    pub fn new() -> Self {
        Self {
            status: 500,
            error: "Internal Server Error".to_string(),
            error_code: "INTERNAL_ERROR".to_string(),
            message: "Ein unerwarteter Fehler ist aufgetreten".to_string(),
            path: "/".to_string(),
            field_errors: None,
        }
    }

    pub fn status(mut self, status: u16) -> Self {
        self.status = status;
        self
    }

    pub fn error(mut self, error: String) -> Self {
        self.error = error;
        self
    }

    pub fn error_code(mut self, error_code: String) -> Self {
        self.error_code = error_code;
        self
    }

    pub fn message(mut self, message: String) -> Self {
        self.message = message;
        self
    }

    pub fn path(mut self, path: String) -> Self {
        self.path = path;
        self
    }

    pub fn field_errors(mut self, field_errors: HashMap<String, String>) -> Self {
        self.field_errors = Some(field_errors);
        self
    }

    pub fn build(self) -> ErrorResponse {
        let mut response = ErrorResponse {
            timestamp: Local::now().format("%Y-%m-%dT%H:%M:%S%.3f").to_string(),
            status: self.status,
            error: self.error,
            error_code: self.error_code,
            message: self.message,
            path: self.path,
            field_errors: None,
        };
        if let Some(field_errors) = self.field_errors {
            response.field_errors = Some(field_errors);
        }
        response
    }
}

impl Default for ErrorResponseBuilder {
    fn default() -> Self {
        Self::new()
    }
}

/// Custom error type for the application
/// All variants convert to appropriate HTTP responses
#[derive(Debug)]
pub enum AppError {
    /// Resource not found - 404 Not Found
    NotFound { resource_type: String, id: String },
    /// Custom not found message
    NotFoundMessage(String),
    /// Unauthorized - 401 Unauthorized
    Unauthorized(String),
    /// Forbidden - 403 Forbidden
    Forbidden(String),
    /// Bad request - 400 Bad Request
    BadRequest(String),
    /// Validation error - 400 Bad Request with field errors
    ValidationError {
        message: String,
        field_errors: HashMap<String, String>,
    },
    /// Internal server error - 500 Internal Server Error
    InternalError(String),
    /// Unmatched or malformed path parameter - 404 without error code (Java `NotFoundExceptionMapper`)
    RouteNotFound,
}

impl AppError {
    /// Create a not found error with resource type and id
    pub fn not_found(resource_type: impl Into<String>, id: impl Into<String>) -> Self {
        Self::NotFound {
            resource_type: resource_type.into(),
            id: id.into(),
        }
    }

    /// Create a not found error with a custom message
    pub fn not_found_message(message: impl Into<String>) -> Self {
        Self::NotFoundMessage(message.into())
    }

    /// Create an unauthorized error
    pub fn unauthorized(message: impl Into<String>) -> Self {
        Self::Unauthorized(message.into())
    }

    /// Create a forbidden error
    pub fn forbidden(message: impl Into<String>) -> Self {
        Self::Forbidden(message.into())
    }

    /// Create a bad request error
    pub fn bad_request(message: impl Into<String>) -> Self {
        Self::BadRequest(message.into())
    }

    /// Create a validation error with field-specific errors
    pub fn validation_error(
        message: impl Into<String>,
        field_errors: HashMap<String, String>,
    ) -> Self {
        Self::ValidationError {
            message: message.into(),
            field_errors,
        }
    }

    /// Create an internal server error
    pub fn internal_error(message: impl Into<String>) -> Self {
        Self::InternalError(message.into())
    }

    /// The generic 500 of the Java `GenericExceptionMapper` (details are only logged, never sent)
    pub fn unexpected() -> Self {
        Self::InternalError(INTERNAL_ERROR_MESSAGE.to_string())
    }
}

/// Message of the Java `GenericExceptionMapper`
pub const INTERNAL_ERROR_MESSAGE: &str = "Ein interner Fehler ist aufgetreten";

/// Answer to a request body that cannot be read as the expected JSON
const BAD_BODY_MESSAGE: &str = "Ungültiger Request-Body";

/// Message of Jakarta REST's `NotFoundException` for an unmatched resource
const ROUTE_NOT_FOUND_MESSAGE: &str = "HTTP 404 Not Found";

impl From<sqlx::Error> for AppError {
    fn from(err: sqlx::Error) -> Self {
        tracing::error!("Database error: {}", err);
        AppError::unexpected()
    }
}

impl std::fmt::Display for AppError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            AppError::NotFound { resource_type, id } => {
                write!(f, "{} mit ID {} wurde nicht gefunden", resource_type, id)
            }
            AppError::NotFoundMessage(msg) => write!(f, "{}", msg),
            AppError::Unauthorized(msg) => write!(f, "{}", msg),
            AppError::Forbidden(msg) => write!(f, "{}", msg),
            AppError::BadRequest(msg) => write!(f, "{}", msg),
            AppError::ValidationError { message, .. } => write!(f, "{}", message),
            AppError::InternalError(msg) => write!(f, "{}", msg),
            AppError::RouteNotFound => write!(f, "{}", ROUTE_NOT_FOUND_MESSAGE),
        }
    }
}

impl IntoResponse for AppError {
    fn into_response(self) -> Response {
        let (status_code, error_response) = match self {
            AppError::NotFound { resource_type, id } => {
                let message = format!("{} mit ID {} wurde nicht gefunden", resource_type, id);
                let response = ErrorResponse::new(
                    404,
                    "Not Found".to_string(),
                    "RESOURCE_NOT_FOUND".to_string(),
                    message,
                    "/".to_string(),
                );
                (StatusCode::NOT_FOUND, response)
            }
            AppError::NotFoundMessage(msg) => {
                let response = ErrorResponse::new(
                    404,
                    "Not Found".to_string(),
                    "RESOURCE_NOT_FOUND".to_string(),
                    msg,
                    "/".to_string(),
                );
                (StatusCode::NOT_FOUND, response)
            }
            AppError::Unauthorized(msg) => {
                let response = ErrorResponse::new(
                    401,
                    "Unauthorized".to_string(),
                    "UNAUTHORIZED".to_string(),
                    msg,
                    "/".to_string(),
                );
                (StatusCode::UNAUTHORIZED, response)
            }
            AppError::Forbidden(msg) => {
                let response = ErrorResponse::new(
                    403,
                    "Forbidden".to_string(),
                    "FORBIDDEN".to_string(),
                    msg,
                    "/".to_string(),
                );
                (StatusCode::FORBIDDEN, response)
            }
            AppError::BadRequest(msg) => {
                let response = ErrorResponse::new(
                    400,
                    "Bad Request".to_string(),
                    "BAD_REQUEST".to_string(),
                    msg,
                    "/".to_string(),
                );
                (StatusCode::BAD_REQUEST, response)
            }
            AppError::ValidationError {
                message,
                field_errors,
            } => {
                let response = ErrorResponse::new(
                    400,
                    "Bad Request".to_string(),
                    "VALIDATION_ERROR".to_string(),
                    message,
                    "/".to_string(),
                )
                .with_field_errors(field_errors);
                (StatusCode::BAD_REQUEST, response)
            }
            AppError::InternalError(msg) => {
                let response = ErrorResponse::new(
                    500,
                    "Internal Server Error".to_string(),
                    "INTERNAL_ERROR".to_string(),
                    msg,
                    "/".to_string(),
                );
                (StatusCode::INTERNAL_SERVER_ERROR, response)
            }
            AppError::RouteNotFound => {
                let response = ErrorResponse::new(
                    404,
                    "Not Found".to_string(),
                    String::new(),
                    ROUTE_NOT_FOUND_MESSAGE.to_string(),
                    "/".to_string(),
                );
                (StatusCode::NOT_FOUND, response)
            }
        };

        // The body is rebuilt with the real request path by `fill_error_path`
        let mut response = (status_code, Json(error_response.clone())).into_response();
        response.extensions_mut().insert(error_response);
        response
    }
}

/// Middleware that sets `path` of every `AppError` response to the request path
/// (Java: `uriInfo.getPath()` in every exception mapper)
pub async fn fill_error_path(request: Request, next: Next) -> Response {
    let path = request.uri().path().to_string();
    let mut response = next.run(request).await;
    match response.extensions_mut().remove::<ErrorResponse>() {
        Some(mut error_response) => {
            error_response.path = path;
            (response.status(), Json(error_response)).into_response()
        }
        None => response,
    }
}

/// `Json` extractor whose rejection is an `AppError`, so an unreadable body gets the JSON
/// `ErrorResponse` instead of axum's plain-text answer
pub struct AppJson<T>(pub T);

#[async_trait]
impl<S, T> FromRequest<S> for AppJson<T>
where
    S: Send + Sync,
    Json<T>: FromRequest<S, Rejection = JsonRejection>,
{
    type Rejection = AppError;

    async fn from_request(request: Request, state: &S) -> Result<Self, Self::Rejection> {
        match Json::<T>::from_request(request, state).await {
            Ok(Json(value)) => Ok(Self(value)),
            Err(rejection) => {
                tracing::warn!("Unreadable request body: {}", rejection);
                Err(AppError::bad_request(BAD_BODY_MESSAGE))
            }
        }
    }
}

/// `Path` extractor whose rejection is `AppError::RouteNotFound`: a path parameter that cannot be
/// converted is a 404 in Jakarta REST
pub struct AppPath<T>(pub T);

#[async_trait]
impl<S, T> FromRequestParts<S> for AppPath<T>
where
    S: Send + Sync,
    axum::extract::Path<T>: FromRequestParts<S, Rejection = PathRejection>,
{
    type Rejection = AppError;

    async fn from_request_parts(parts: &mut Parts, state: &S) -> Result<Self, Self::Rejection> {
        axum::extract::Path::<T>::from_request_parts(parts, state)
            .await
            .map(|axum::extract::Path(value)| Self(value))
            .map_err(|rejection| {
                tracing::warn!("Unusable path parameter: {}", rejection);
                AppError::RouteNotFound
            })
    }
}

/// Fallback of the router for an unmatched path (Java `NotFoundExceptionMapper`)
pub async fn route_not_found() -> AppError {
    AppError::RouteNotFound
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_not_found_error_message_format() {
        let error = AppError::not_found("Termin", "123");
        assert_eq!(error.to_string(), "Termin mit ID 123 wurde nicht gefunden");
    }

    #[test]
    fn test_not_found_custom_message() {
        let error = AppError::not_found_message("Benutzer nicht gefunden");
        assert_eq!(error.to_string(), "Benutzer nicht gefunden");
    }

    #[test]
    fn test_unauthorized_error() {
        let error = AppError::unauthorized("Authentifizierung erforderlich");
        assert_eq!(error.to_string(), "Authentifizierung erforderlich");
    }

    #[test]
    fn test_forbidden_error() {
        let error = AppError::forbidden("Zugriff verweigert");
        assert_eq!(error.to_string(), "Zugriff verweigert");
    }

    #[test]
    fn test_bad_request_error() {
        let error = AppError::bad_request("Ungültige Eingabe");
        assert_eq!(error.to_string(), "Ungültige Eingabe");
    }

    #[test]
    fn test_validation_error_with_fields() {
        let mut field_errors = HashMap::new();
        field_errors.insert("email".to_string(), "Ungültiges Email-Format".to_string());
        field_errors.insert("name".to_string(), "Name ist erforderlich".to_string());

        let error = AppError::validation_error("Validierungsfehler", field_errors);
        assert_eq!(error.to_string(), "Validierungsfehler");
    }

    #[test]
    fn test_internal_error() {
        let error = AppError::internal_error("Datenbankfehler");
        assert_eq!(error.to_string(), "Datenbankfehler");
    }

    #[test]
    fn test_error_response_builder() {
        let response = ErrorResponse::builder()
            .status(404)
            .error("Not Found".to_string())
            .error_code("RESOURCE_NOT_FOUND".to_string())
            .message("Termin mit ID 123 wurde nicht gefunden".to_string())
            .path("/appointments/123".to_string())
            .build();

        assert_eq!(response.status, 404);
        assert_eq!(response.error, "Not Found");
        assert_eq!(response.error_code, "RESOURCE_NOT_FOUND");
        assert_eq!(response.message, "Termin mit ID 123 wurde nicht gefunden");
        assert_eq!(response.path, "/appointments/123");
    }

    #[test]
    fn test_error_response_with_field_errors() {
        let mut field_errors = HashMap::new();
        field_errors.insert("email".to_string(), "Ungültiges Format".to_string());

        let response = ErrorResponse::builder()
            .status(400)
            .error("Bad Request".to_string())
            .error_code("VALIDATION_ERROR".to_string())
            .message("Validierungsfehler".to_string())
            .path("/appointments".to_string())
            .field_errors(field_errors.clone())
            .build();

        assert_eq!(response.status, 400);
        assert_eq!(response.field_errors, Some(field_errors));
    }

    #[test]
    fn test_error_response_serialization() {
        let response = ErrorResponse::new(
            404,
            "Not Found".to_string(),
            "RESOURCE_NOT_FOUND".to_string(),
            "Termin nicht gefunden".to_string(),
            "/appointments/123".to_string(),
        );

        let json = serde_json::to_string(&response).expect("Failed to serialize");
        assert!(json.contains("\"status\":404"));
        assert!(json.contains("\"error\":\"Not Found\""));
        assert!(json.contains("\"errorCode\":\"RESOURCE_NOT_FOUND\""));
        assert!(json.contains("\"message\":\"Termin nicht gefunden\""));
    }

    #[test]
    fn test_error_response_omits_null_field_errors() {
        let response = ErrorResponse::new(
            404,
            "Not Found".to_string(),
            "RESOURCE_NOT_FOUND".to_string(),
            "Nicht gefunden".to_string(),
            "/".to_string(),
        );

        let json = serde_json::to_string(&response).expect("Failed to serialize");
        assert!(!json.contains("fieldErrors"));
    }
}
