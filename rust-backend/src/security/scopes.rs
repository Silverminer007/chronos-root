/// Scope and role validation for JWT tokens
/// This module provides utilities for validating scopes and roles in JWT tokens
/// Currently, the main token validation focuses on JWT signature validation.
/// Future work can extend this to enforce specific scopes and roles.

use serde::{Deserialize, Serialize};

/// Token scopes that may be included in a Keycloak JWT
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub enum TokenScope {
    /// Read appointments
    ReadAppointments,
    /// Create appointments
    WriteAppointments,
    /// Read groups
    ReadGroups,
    /// Manage groups
    WriteGroups,
    /// Read push notifications
    ReadNotifications,
    /// Send push notifications
    WriteNotifications,
}

impl TokenScope {
    /// Parse a scope string from a JWT token
    pub fn from_string(s: &str) -> Option<Self> {
        match s {
            "chronos:appointments:read" => Some(Self::ReadAppointments),
            "chronos:appointments:write" => Some(Self::WriteAppointments),
            "chronos:groups:read" => Some(Self::ReadGroups),
            "chronos:groups:write" => Some(Self::WriteGroups),
            "chronos:notifications:read" => Some(Self::ReadNotifications),
            "chronos:notifications:write" => Some(Self::WriteNotifications),
            _ => None,
        }
    }

    /// Convert scope to string representation
    pub fn to_string(&self) -> &'static str {
        match self {
            Self::ReadAppointments => "chronos:appointments:read",
            Self::WriteAppointments => "chronos:appointments:write",
            Self::ReadGroups => "chronos:groups:read",
            Self::WriteGroups => "chronos:groups:write",
            Self::ReadNotifications => "chronos:notifications:read",
            Self::WriteNotifications => "chronos:notifications:write",
        }
    }
}

/// Error type for scope validation
#[derive(Debug)]
pub enum ScopeError {
    MissingScope(String),
    InsufficientPermissions,
}

impl std::fmt::Display for ScopeError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            ScopeError::MissingScope(scope) => write!(f, "Missing scope: {}", scope),
            ScopeError::InsufficientPermissions => write!(f, "Insufficient permissions"),
        }
    }
}

impl std::error::Error for ScopeError {}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_scope_from_string() {
        assert_eq!(
            TokenScope::from_string("chronos:appointments:read"),
            Some(TokenScope::ReadAppointments)
        );
        assert_eq!(
            TokenScope::from_string("chronos:appointments:write"),
            Some(TokenScope::WriteAppointments)
        );
        assert_eq!(TokenScope::from_string("invalid"), None);
    }

    #[test]
    fn test_scope_to_string() {
        assert_eq!(
            TokenScope::ReadAppointments.to_string(),
            "chronos:appointments:read"
        );
        assert_eq!(
            TokenScope::WriteAppointments.to_string(),
            "chronos:appointments:write"
        );
    }
}
