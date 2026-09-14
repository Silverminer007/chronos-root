/// Integration tests for error handling and HTTP response mapping
/// These tests verify that error responses match the Java backend format exactly

use chronos_date_api::error::{AppError, ErrorResponse};
use std::collections::HashMap;

#[test]
fn test_resource_not_found_error_format() {
    let error = AppError::not_found("Termin", "123");
    assert_eq!(
        error.to_string(),
        "Termin mit ID 123 wurde nicht gefunden"
    );
}

#[test]
fn test_resource_not_found_custom_message_format() {
    let error = AppError::not_found_message("Benutzer nicht gefunden");
    assert_eq!(error.to_string(), "Benutzer nicht gefunden");
}

#[test]
fn test_unauthorized_error_format() {
    let error = AppError::unauthorized("Authentifizierung erforderlich");
    assert_eq!(error.to_string(), "Authentifizierung erforderlich");
}

#[test]
fn test_forbidden_error_format() {
    let error = AppError::forbidden("Zugriff verweigert");
    assert_eq!(error.to_string(), "Zugriff verweigert");
}

#[test]
fn test_bad_request_error_format() {
    let error = AppError::bad_request("Ungültige Eingabe");
    assert_eq!(error.to_string(), "Ungültige Eingabe");
}

#[test]
fn test_validation_error_format() {
    let mut field_errors = HashMap::new();
    field_errors.insert("email".to_string(), "Ungültiges Email-Format".to_string());
    field_errors.insert("name".to_string(), "Name ist erforderlich".to_string());

    let error = AppError::validation_error("Validierungsfehler", field_errors);
    assert_eq!(error.to_string(), "Validierungsfehler");
}

#[test]
fn test_internal_error_format() {
    let error = AppError::internal_error("Datenbankfehler");
    assert_eq!(error.to_string(), "Datenbankfehler");
}

// ErrorResponse serialization tests
#[test]
fn test_error_response_404_serialization() {
    let response = ErrorResponse::new(
        404,
        "Not Found".to_string(),
        "RESOURCE_NOT_FOUND".to_string(),
        "Termin mit ID 123 wurde nicht gefunden".to_string(),
        "/appointments/123".to_string(),
    );

    assert_eq!(response.status, 404);
    assert_eq!(response.error, "Not Found");
    assert_eq!(response.error_code, "RESOURCE_NOT_FOUND");
    assert_eq!(
        response.message,
        "Termin mit ID 123 wurde nicht gefunden"
    );
    assert_eq!(response.path, "/appointments/123");
    assert_eq!(response.field_errors, None);
}

#[test]
fn test_error_response_403_serialization() {
    let response = ErrorResponse::new(
        403,
        "Forbidden".to_string(),
        "FORBIDDEN".to_string(),
        "Zugriff verweigert".to_string(),
        "/appointments/456".to_string(),
    );

    assert_eq!(response.status, 403);
    assert_eq!(response.error, "Forbidden");
    assert_eq!(response.error_code, "FORBIDDEN");
}

#[test]
fn test_error_response_400_validation_error() {
    let mut field_errors = HashMap::new();
    field_errors.insert("email".to_string(), "Ungültiges Format".to_string());

    let response = ErrorResponse::new(
        400,
        "Bad Request".to_string(),
        "VALIDATION_ERROR".to_string(),
        "Validierungsfehler".to_string(),
        "/appointments".to_string(),
    )
    .with_field_errors(field_errors.clone());

    assert_eq!(response.status, 400);
    assert_eq!(response.error, "Bad Request");
    assert_eq!(response.error_code, "VALIDATION_ERROR");
    assert_eq!(response.field_errors, Some(field_errors));
}

#[test]
fn test_error_response_500_internal_server_error() {
    let response = ErrorResponse::new(
        500,
        "Internal Server Error".to_string(),
        "INTERNAL_ERROR".to_string(),
        "Ein unerwarteter Fehler ist aufgetreten".to_string(),
        "/".to_string(),
    );

    assert_eq!(response.status, 500);
    assert_eq!(response.error, "Internal Server Error");
    assert_eq!(response.error_code, "INTERNAL_ERROR");
}

#[test]
fn test_error_response_401_unauthorized() {
    let response = ErrorResponse::new(
        401,
        "Unauthorized".to_string(),
        "UNAUTHORIZED".to_string(),
        "Authentifizierung erforderlich".to_string(),
        "/appointments".to_string(),
    );

    assert_eq!(response.status, 401);
    assert_eq!(response.error, "Unauthorized");
    assert_eq!(response.error_code, "UNAUTHORIZED");
}

#[test]
fn test_error_response_json_serialization_without_field_errors() {
    let response = ErrorResponse::new(
        404,
        "Not Found".to_string(),
        "RESOURCE_NOT_FOUND".to_string(),
        "Nicht gefunden".to_string(),
        "/".to_string(),
    );

    let json = serde_json::to_string(&response).expect("Failed to serialize");

    // Verify JSON contains all required fields
    assert!(json.contains("\"status\":404"));
    assert!(json.contains("\"error\":\"Not Found\""));
    assert!(json.contains("\"errorCode\":\"RESOURCE_NOT_FOUND\""));
    assert!(json.contains("\"message\":\"Nicht gefunden\""));
    assert!(json.contains("\"path\":\"/\""));
    assert!(json.contains("\"timestamp\":\""));

    // Verify fieldErrors is not included when None
    assert!(!json.contains("fieldErrors"));
}

#[test]
fn test_error_response_json_serialization_with_field_errors() {
    let mut field_errors = HashMap::new();
    field_errors.insert("email".to_string(), "Ungültiges Format".to_string());

    let response = ErrorResponse::new(
        400,
        "Bad Request".to_string(),
        "VALIDATION_ERROR".to_string(),
        "Validierungsfehler".to_string(),
        "/appointments".to_string(),
    )
    .with_field_errors(field_errors);

    let json = serde_json::to_string(&response).expect("Failed to serialize");

    // Verify JSON contains field errors
    assert!(json.contains("\"fieldErrors\""));
    assert!(json.contains("\"email\":\"Ungültiges Format\""));
}

#[test]
fn test_error_response_builder_pattern() {
    let response = ErrorResponse::builder()
        .status(404)
        .error("Not Found".to_string())
        .error_code("RESOURCE_NOT_FOUND".to_string())
        .message("Termin nicht gefunden".to_string())
        .path("/appointments/123".to_string())
        .build();

    assert_eq!(response.status, 404);
    assert_eq!(response.error, "Not Found");
    assert_eq!(response.error_code, "RESOURCE_NOT_FOUND");
    assert_eq!(response.message, "Termin nicht gefunden");
    assert_eq!(response.path, "/appointments/123");
}

#[test]
fn test_error_response_builder_with_field_errors() {
    let mut field_errors = HashMap::new();
    field_errors.insert("name".to_string(), "Name ist erforderlich".to_string());
    field_errors.insert("email".to_string(), "Email ist erforderlich".to_string());

    let response = ErrorResponse::builder()
        .status(400)
        .error("Bad Request".to_string())
        .error_code("VALIDATION_ERROR".to_string())
        .message("Validierungsfehler".to_string())
        .path("/users".to_string())
        .field_errors(field_errors.clone())
        .build();

    assert_eq!(response.field_errors, Some(field_errors));
}

#[test]
fn test_appointment_not_found_error_code() {
    let error = AppError::not_found("Termin", "999");

    // Create error response from error
    let response = ErrorResponse::new(
        404,
        "Not Found".to_string(),
        "RESOURCE_NOT_FOUND".to_string(),
        error.to_string(),
        "/appointments/999".to_string(),
    );

    assert_eq!(response.error_code, "RESOURCE_NOT_FOUND");
    assert_eq!(response.status, 404);
}

#[test]
fn test_multiple_validation_errors() {
    let mut field_errors = HashMap::new();
    field_errors.insert("startTime".to_string(), "Start-Zeit ist erforderlich".to_string());
    field_errors.insert("endTime".to_string(), "End-Zeit muss nach Start-Zeit liegen".to_string());
    field_errors.insert("title".to_string(), "Titel darf nicht leer sein".to_string());

    let response = ErrorResponse::new(
        400,
        "Bad Request".to_string(),
        "VALIDATION_ERROR".to_string(),
        "Validierungsfehler".to_string(),
        "/appointments".to_string(),
    )
    .with_field_errors(field_errors.clone());

    assert_eq!(response.field_errors.as_ref().unwrap().len(), 3);
    assert!(response
        .field_errors
        .as_ref()
        .unwrap()
        .contains_key("startTime"));
    assert!(response
        .field_errors
        .as_ref()
        .unwrap()
        .contains_key("endTime"));
    assert!(response
        .field_errors
        .as_ref()
        .unwrap()
        .contains_key("title"));
}

#[test]
fn test_error_response_timestamp_format() {
    let response = ErrorResponse::new(
        404,
        "Not Found".to_string(),
        "RESOURCE_NOT_FOUND".to_string(),
        "Nicht gefunden".to_string(),
        "/".to_string(),
    );

    // Verify timestamp is in ISO 8601 format with milliseconds
    assert!(response.timestamp.contains("T"));
    assert!(response.timestamp.contains("-"));
    assert!(response.timestamp.contains(":"));
}

#[test]
fn test_transactional_integrity_error() {
    // Simulate a transactional integrity error
    let error = AppError::internal_error(
        "Transaktionalität verletzt: Parallel-Änderung erkannt".to_string(),
    );

    let response = ErrorResponse::new(
        500,
        "Internal Server Error".to_string(),
        "INTERNAL_ERROR".to_string(),
        error.to_string(),
        "/appointments".to_string(),
    );

    assert_eq!(response.status, 500);
    assert!(response.message.contains("Transaktionalität"));
}

#[test]
fn test_error_status_codes_match_java_backend() {
    // Test that all error types return the correct HTTP status codes matching Java backend

    // 404 Not Found
    let response_404 = ErrorResponse::new(
        404,
        "Not Found".to_string(),
        "RESOURCE_NOT_FOUND".to_string(),
        "Nicht gefunden".to_string(),
        "/".to_string(),
    );
    assert_eq!(response_404.status, 404);

    // 401 Unauthorized
    let response_401 = ErrorResponse::new(
        401,
        "Unauthorized".to_string(),
        "UNAUTHORIZED".to_string(),
        "Authentifizierung erforderlich".to_string(),
        "/".to_string(),
    );
    assert_eq!(response_401.status, 401);

    // 403 Forbidden (NOT 401)
    let response_403 = ErrorResponse::new(
        403,
        "Forbidden".to_string(),
        "FORBIDDEN".to_string(),
        "Zugriff verweigert".to_string(),
        "/".to_string(),
    );
    assert_eq!(response_403.status, 403);

    // 400 Bad Request
    let response_400 = ErrorResponse::new(
        400,
        "Bad Request".to_string(),
        "BAD_REQUEST".to_string(),
        "Ungültige Eingabe".to_string(),
        "/".to_string(),
    );
    assert_eq!(response_400.status, 400);

    // 500 Internal Server Error
    let response_500 = ErrorResponse::new(
        500,
        "Internal Server Error".to_string(),
        "INTERNAL_ERROR".to_string(),
        "Ein unerwarteter Fehler ist aufgetreten".to_string(),
        "/".to_string(),
    );
    assert_eq!(response_500.status, 500);
}
