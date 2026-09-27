"""
Agent Orchestrator - Automates discovery and execution of GitHub issues.

Includes agents for:
- Spec Validator: Validates ticket completeness before implementation
- TDD Agent: Test-driven development automation
- Code Review Agent: Automated code review
- Fixer Agent: Automated issue remediation
"""

__version__ = "0.1.0"

try:
    from .spec_validator import SpecValidator, ValidationResult
except ImportError:
    # Handle case where module is run directly for testing
    from spec_validator import SpecValidator, ValidationResult

__all__ = [
    "SpecValidator",
    "ValidationResult",
]
