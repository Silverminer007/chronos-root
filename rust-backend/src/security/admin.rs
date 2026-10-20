//! Authorization of the admin endpoints (Java: `@RolesAllowed("ADMIN_API")`).

use axum::extract::FromRequestParts;
use axum::http::request::Parts;
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};

use super::principal::{PrincipalContext, PrincipalError};

/// Realm role that grants access to `/api/v2/admin/*`
pub const ADMIN_ROLE: &str = "ADMIN_API";

/// The authenticated principal, proven to hold [`ADMIN_ROLE`]. Taking it as handler argument
/// enforces the role; everybody else gets Quarkus' bare 403.
#[derive(Debug, Clone)]
pub struct AdminPrincipal(pub PrincipalContext);

impl AdminPrincipal {
    pub fn user_id(&self) -> &str {
        self.0.user_id()
    }
}

/// Why the admin check failed
#[derive(Debug)]
pub enum AdminRejection {
    /// No authenticated principal (401)
    Unauthenticated,
    /// Authenticated without the admin role: an empty 403, as `@RolesAllowed` answers in Quarkus
    Forbidden,
}

impl IntoResponse for AdminRejection {
    fn into_response(self) -> Response {
        match self {
            AdminRejection::Unauthenticated => StatusCode::UNAUTHORIZED.into_response(),
            AdminRejection::Forbidden => StatusCode::FORBIDDEN.into_response(),
        }
    }
}

#[async_trait::async_trait]
impl<S> FromRequestParts<S> for AdminPrincipal
where
    S: Send + Sync,
{
    type Rejection = AdminRejection;

    async fn from_request_parts(parts: &mut Parts, state: &S) -> Result<Self, Self::Rejection> {
        let principal = PrincipalContext::from_request_parts(parts, state)
            .await
            .map_err(|PrincipalError| AdminRejection::Unauthenticated)?;
        if principal.has_role(ADMIN_ROLE) {
            Ok(Self(principal))
        } else {
            Err(AdminRejection::Forbidden)
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use axum::http::Request;
    use std::sync::Arc;

    async fn extract(
        principal: Option<PrincipalContext>,
    ) -> Result<AdminPrincipal, AdminRejection> {
        let mut request = Request::builder().body(()).unwrap();
        if let Some(principal) = principal {
            request.extensions_mut().insert(Arc::new(principal));
        }
        let (mut parts, _) = request.into_parts();
        AdminPrincipal::from_request_parts(&mut parts, &()).await
    }

    #[tokio::test]
    async fn admin_role_is_accepted() {
        let principal = PrincipalContext::new("a").with_roles(vec![ADMIN_ROLE.to_string()]);
        assert_eq!(extract(Some(principal)).await.unwrap().user_id(), "a");
    }

    #[tokio::test]
    async fn other_roles_are_forbidden() {
        let principal = PrincipalContext::new("u").with_roles(vec!["offline_access".to_string()]);
        assert!(matches!(
            extract(Some(principal)).await,
            Err(AdminRejection::Forbidden)
        ));
        assert!(matches!(
            extract(Some(PrincipalContext::new("u"))).await,
            Err(AdminRejection::Forbidden)
        ));
    }

    #[tokio::test]
    async fn missing_principal_is_unauthenticated() {
        assert!(matches!(
            extract(None).await,
            Err(AdminRejection::Unauthenticated)
        ));
    }

    #[test]
    fn rejections_answer_with_empty_bodies() {
        assert_eq!(
            AdminRejection::Forbidden.into_response().status(),
            StatusCode::FORBIDDEN
        );
        assert_eq!(
            AdminRejection::Unauthenticated.into_response().status(),
            StatusCode::UNAUTHORIZED
        );
    }
}
