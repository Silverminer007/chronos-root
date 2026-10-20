use axum::{
    body::Bytes,
    extract::{OriginalUri, Path, Query, State},
    http::StatusCode,
    response::{IntoResponse, Response},
    Json,
};
use serde::{de::DeserializeOwned, Deserialize};
use std::sync::Arc;
use tracing::error;

use crate::error::{ErrorResponse, BAD_BODY_MESSAGE};
use crate::keycloak::KeycloakError;
use crate::security::PrincipalContext;
use crate::users::{
    models::{LinkedAccountDto, PrincipalDto, Settings, SettingsDto, UrlResponse},
    repository::UserRepository,
    services::{require_redirect_uri, ProfileError, ProfileService},
    UserState,
};

/// Body of Java's `GenericExceptionMapper` (unhandled exception → 500)
const INTERNAL_ERROR_MESSAGE: &str = "Ein interner Fehler ist aufgetreten";

/// Error response in the format of Java's exception mappers
pub struct ApiError {
    status: StatusCode,
    error: &'static str,
    error_code: &'static str,
    message: String,
    path: String,
}

impl ApiError {
    /// Java `GenericExceptionMapper`: any exception without its own mapper
    pub fn internal(path: &str) -> Self {
        Self {
            status: StatusCode::INTERNAL_SERVER_ERROR,
            error: "Internal Server Error",
            error_code: "INTERNAL_ERROR",
            message: INTERNAL_ERROR_MESSAGE.to_string(),
            path: path.to_string(),
        }
    }

    /// Java `BadRequestExceptionMapper`
    fn bad_request(message: String, path: &str) -> Self {
        Self {
            status: StatusCode::BAD_REQUEST,
            error: "Bad Request",
            error_code: "BAD_REQUEST",
            message,
            path: path.to_string(),
        }
    }

    fn from_keycloak(error: KeycloakError, path: &str) -> Self {
        // Ticket answer 5: Keycloak 404, 5xx and network errors are all unhandled → 500
        error!("Keycloak call failed for {}: {}", path, error);
        Self::internal(path)
    }

    fn from_profile(error: ProfileError, path: &str) -> Self {
        match error {
            ProfileError::BadRequest(message) => Self::bad_request(message, path),
            // Java: NullPointerException → GenericExceptionMapper (see ADR-2026-10-20-017)
            ProfileError::MissingRedirectUri => Self::internal(path),
            ProfileError::Keycloak(e) => Self::from_keycloak(e, path),
        }
    }
}

impl IntoResponse for ApiError {
    fn into_response(self) -> Response {
        let body = ErrorResponse::new(
            self.status.as_u16(),
            self.error.to_string(),
            self.error_code.to_string(),
            self.message,
            self.path,
        );
        (self.status, Json(body)).into_response()
    }
}

/// `redirectUri` / `passkeyId` query parameters
#[derive(Debug, Deserialize)]
pub struct ActionQuery {
    #[serde(rename = "redirectUri")]
    redirect_uri: Option<String>,
    #[serde(rename = "passkeyId")]
    passkey_id: Option<String>,
}

fn principal_dto(principal: &PrincipalContext) -> PrincipalDto {
    PrincipalDto {
        id: Some(principal.user_id().to_string()),
        first_name: principal.first_name.clone(),
        last_name: principal.last_name.clone(),
        email: principal.email.clone(),
    }
}

/// A body Jackson cannot turn into the DTO (empty, `null`, malformed) ends in an unhandled exception in Java
fn parse_body<T: DeserializeOwned>(body: &Bytes, path: &str) -> Result<T, ApiError> {
    serde_json::from_slice::<Option<T>>(body)
        .ok()
        .flatten()
        .ok_or_else(|| ApiError::internal(path))
}

/// Like `parse_body`, but syntactically broken JSON is a 400 (Quarkus maps Jackson's
/// `JsonParseException` of `SettingsResource` to a bad request).
fn parse_body_or_bad_request<T: DeserializeOwned>(body: &Bytes, path: &str) -> Result<T, ApiError> {
    if body.is_empty() {
        return Err(ApiError::internal(path));
    }
    // Parsed as a plain value first: serde reports a type mismatch before a later syntax error
    let value = serde_json::from_slice::<serde_json::Value>(body)
        .map_err(|_| ApiError::bad_request(BAD_BODY_MESSAGE.to_string(), path))?;
    serde_json::from_value::<Option<T>>(value)
        .ok()
        .flatten()
        .ok_or_else(|| ApiError::internal(path))
}

/// GET /api/v2/user and POST /api/v2/user – the principal as given by the access token
pub async fn get_user(principal: PrincipalContext) -> Json<PrincipalDto> {
    Json(principal_dto(&principal))
}

/// PATCH /api/v2/user
pub async fn patch_user(
    State(state): State<Arc<UserState>>,
    principal: PrincipalContext,
    OriginalUri(uri): OriginalUri,
    Query(query): Query<ActionQuery>,
    body: Bytes,
) -> Result<Json<crate::users::models::UpdatedUserDto>, ApiError> {
    let path = uri.path();
    let request: PrincipalDto = parse_body(&body, path)?;
    ProfileService::new(state.keycloak.as_ref(), &state.keycloak_urls)
        .update_user(principal.user_id(), &request, query.redirect_uri.as_deref())
        .await
        .map(Json)
        .map_err(|e| ApiError::from_profile(e, path))
}

/// DELETE /api/v2/user – Java answers an empty 405 (see ADR-2026-10-20-017)
pub async fn delete_user() -> StatusCode {
    StatusCode::METHOD_NOT_ALLOWED
}

/// GET /api/v2/user/linked
pub async fn get_linked_accounts(
    State(state): State<Arc<UserState>>,
    principal: PrincipalContext,
    OriginalUri(uri): OriginalUri,
) -> Result<Json<Vec<LinkedAccountDto>>, ApiError> {
    let accounts = ProfileService::new(state.keycloak.as_ref(), &state.keycloak_urls)
        .linked_accounts(principal.user_id())
        .await
        .map_err(|e| ApiError::from_keycloak(e, uri.path()))?;
    Ok(Json(
        accounts
            .into_iter()
            .map(|a| LinkedAccountDto {
                provider: a.identity_provider,
            })
            .collect(),
    ))
}

/// DELETE /api/v2/user/linked/:providerId
pub async fn unlink_account(
    State(state): State<Arc<UserState>>,
    principal: PrincipalContext,
    OriginalUri(uri): OriginalUri,
    Path(provider_id): Path<String>,
) -> Result<StatusCode, ApiError> {
    ProfileService::new(state.keycloak.as_ref(), &state.keycloak_urls)
        .unlink_account(principal.user_id(), &provider_id)
        .await
        .map_err(|e| ApiError::from_keycloak(e, uri.path()))?;
    Ok(StatusCode::NO_CONTENT)
}

/// GET /api/v2/user/link/:provider
pub async fn get_link_url(
    State(state): State<Arc<UserState>>,
    OriginalUri(uri): OriginalUri,
    Path(provider): Path<String>,
    Query(query): Query<ActionQuery>,
) -> Result<Json<UrlResponse>, ApiError> {
    let redirect_uri = redirect_uri_or_500(&query, uri.path())?;
    Ok(Json(UrlResponse {
        url: state.keycloak_urls.link_url(&provider, redirect_uri),
    }))
}

/// GET /api/v2/user/link/passkey
pub async fn get_passkey_registration_url(
    State(state): State<Arc<UserState>>,
    OriginalUri(uri): OriginalUri,
    Query(query): Query<ActionQuery>,
) -> Result<Json<UrlResponse>, ApiError> {
    let redirect_uri = redirect_uri_or_500(&query, uri.path())?;
    Ok(Json(UrlResponse {
        url: state.keycloak_urls.passkey_registration_url(redirect_uri),
    }))
}

/// GET /api/v2/user/unlink/passkey
pub async fn get_passkey_deletion_url(
    State(state): State<Arc<UserState>>,
    OriginalUri(uri): OriginalUri,
    Query(query): Query<ActionQuery>,
) -> Result<Json<UrlResponse>, ApiError> {
    let redirect_uri = redirect_uri_or_500(&query, uri.path())?;
    // Java appends a missing passkeyId as the literal "null" (string concatenation)
    let passkey_id = query.passkey_id.as_deref().unwrap_or("null");
    Ok(Json(UrlResponse {
        url: state
            .keycloak_urls
            .delete_passkey_url(redirect_uri, passkey_id),
    }))
}

/// GET /api/v2/user/passkeys
pub async fn get_passkeys(
    State(state): State<Arc<UserState>>,
    principal: PrincipalContext,
    OriginalUri(uri): OriginalUri,
) -> Result<Json<Vec<crate::users::models::PasskeyDto>>, ApiError> {
    ProfileService::new(state.keycloak.as_ref(), &state.keycloak_urls)
        .passkeys(principal.user_id())
        .await
        .map(Json)
        .map_err(|e| ApiError::from_keycloak(e, uri.path()))
}

/// GET /api/v2/user/change-password
pub async fn get_change_password_url(
    State(state): State<Arc<UserState>>,
    OriginalUri(uri): OriginalUri,
    Query(query): Query<ActionQuery>,
) -> Result<Json<UrlResponse>, ApiError> {
    let redirect_uri = redirect_uri_or_500(&query, uri.path())?;
    Ok(Json(UrlResponse {
        url: state.keycloak_urls.change_password_url(redirect_uri),
    }))
}

fn redirect_uri_or_500<'a>(query: &'a ActionQuery, path: &str) -> Result<&'a str, ApiError> {
    require_redirect_uri(query.redirect_uri.as_deref()).map_err(|e| ApiError::from_profile(e, path))
}

/// GET /api/v2/settings – creates the defaults on first access
pub async fn get_settings(
    State(state): State<Arc<UserState>>,
    principal: PrincipalContext,
    OriginalUri(uri): OriginalUri,
) -> Result<Json<SettingsDto>, ApiError> {
    UserRepository::new(state.db_pool.clone())
        .get_or_create_settings(principal.user_id())
        .await
        .map(Json)
        .map_err(|e| database_error(e, uri.path()))
}

/// PUT /api/v2/settings – 200 with an empty body
pub async fn update_settings(
    State(state): State<Arc<UserState>>,
    principal: PrincipalContext,
    OriginalUri(uri): OriginalUri,
    body: Bytes,
) -> Result<StatusCode, ApiError> {
    let path = uri.path();
    let dto: SettingsDto = parse_body_or_bad_request(&body, path)?;
    // Java persists nulls into NOT NULL columns for blank/unknown values and fails with a 500
    let settings = Settings::from_dto(&dto).ok_or_else(|| ApiError::internal(path))?;
    UserRepository::new(state.db_pool.clone())
        .save_settings(principal.user_id(), &settings)
        .await
        .map_err(|e| database_error(e, path))?;
    Ok(StatusCode::OK)
}

fn database_error(error: sqlx::Error, path: &str) -> ApiError {
    error!("Database error for {}: {}", path, error);
    ApiError::internal(path)
}
