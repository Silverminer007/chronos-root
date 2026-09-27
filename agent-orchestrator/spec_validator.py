"""
Spec Validator Agent - Validates GitHub ticket completeness before implementation.

Checks for:
- Acceptance criteria (defined and testable)
- Scope (clearly bounded)
- Dependencies (documented)
- Ambiguities (unclear terminology)
- Testability (can criteria be verified)
"""

import subprocess
import json
import re
import sys
import os
from typing import Dict, List, Optional, Any
from dataclasses import dataclass, field


@dataclass
class ValidationResult:
    """Result of ticket specification validation."""

    is_valid: bool
    questions: List[str] = field(default_factory=list)

    def as_github_comment(self) -> str:
        """Format result as GitHub issue comment."""
        if self.is_valid:
            return ""

        comment = "## Ticket underspecified\n\n"
        comment += "The following questions should be answered before implementation:\n\n"

        for i, question in enumerate(self.questions, 1):
            comment += f"{i}. {question}\n"

        comment += "\n---\n"
        comment += "Please reply in the ticket comments, then re-apply the `ready-for-agent` label to retry.\n"
        comment += "*This check was performed by spec-validator agent.*"

        return comment


class SpecValidator:
    """Validates GitHub ticket specifications for completeness."""

    ACCEPTANCE_CRITERIA_PATTERNS = [
        r'(?:##\s+)?Acceptance\s+[Cc]riteria',
        r'(?:##\s+)?Definition\s+of\s+Done',
        r'(?:##\s+)?Requirements',
        r'-\s*\[\s*[x\s]\s*\]',  # Checkbox pattern
    ]

    SCOPE_KEYWORDS = [
        'scope', 'boundaries', 'bounded', 'out of scope',
        'not included', 'limited to', 'focuses on'
    ]

    DEPENDENCY_PATTERNS = [
        r'(?:##\s+)?[Dd]ependencies',
        r'(?:##\s+)?Related\s+(?:to|issues)',
        r'external\s+(?:dependencies|packages|libraries)',
    ]

    VAGUE_WORDS = [
        'better', 'faster', 'improved', 'nice',
        'thing', 'stuff', 'widget',
        'etc', 'easy',
        'robust', 'stable', 'performant',  # Without metrics
    ]

    def __init__(self, kb_path: Optional[str] = None):
        """Initialize validator.

        Args:
            kb_path: Path to knowledge base JSON file (optional)
        """
        self.kb_path = kb_path or self._find_kb()
        self.kb = self.load_kb()

    def _find_kb(self) -> Optional[str]:
        """Find knowledge base file in standard locations."""
        search_paths = [
            'knowledge-base.json',
            'kb.json',
            os.path.join(os.path.dirname(__file__), 'knowledge-base.json'),
        ]
        for path in search_paths:
            if os.path.exists(path):
                return path
        return None

    def load_kb(self) -> Dict[str, Any]:
        """Load knowledge base from file or return default."""
        if not self.kb_path or not os.path.exists(self.kb_path):
            return self._default_kb()

        try:
            with open(self.kb_path, 'r') as f:
                return json.load(f)
        except Exception:
            return self._default_kb()

    def _default_kb(self) -> Dict[str, Any]:
        """Return default knowledge base."""
        return {
            "domain": "chronos",
            "patterns": {
                "acceptance_criteria": [
                    "Acceptance Criteria",
                    "Definition of Done",
                    "Requirements"
                ],
                "scope": ["Scope", "Boundaries", "Out of Scope"],
                "dependencies": ["Dependencies", "Related to", "Related issues"],
            }
        }

    def validate(self, spec: Dict[str, str]) -> ValidationResult:
        """Validate ticket specification.

        Args:
            spec: Dictionary with 'title' and 'body' keys

        Returns:
            ValidationResult with is_valid flag and list of questions
        """
        questions = []
        body = (spec.get('body') or '').lower()
        title = (spec.get('title') or '').lower()

        # Check for acceptance criteria
        if not self._has_acceptance_criteria(spec):
            questions.append(
                "Are there specific acceptance criteria that define 'done'? "
                "Please list them as checkboxes (- [ ] Criterion) or in a dedicated section."
            )

        # Check for scope
        if not self._has_scope(spec):
            questions.append(
                "What is the scope of this work? Should include: what is IN scope, "
                "what is explicitly OUT of scope, and any clear boundaries."
            )

        # Check for dependencies
        if not self._has_dependencies_section(spec):
            questions.append(
                "Are there any dependencies (external services, PRs, infrastructure)? "
                "Please list them explicitly."
            )

        # Check for ambiguous language
        ambiguous = self._find_ambiguous_terms(spec)
        if ambiguous:
            questions.append(
                f"Please clarify these terms: {', '.join(ambiguous)}. "
                "Use specific, measurable language."
            )

        # Check testability - if acceptance criteria exist but no test hints
        if self._has_acceptance_criteria(spec) and not self._has_criteria_hints(spec):
            questions.append(
                "How will the acceptance criteria be tested/verified? "
                "Should mention testing approach (unit tests, integration tests, manual verification)."
            )

        return ValidationResult(
            is_valid=len(questions) == 0,
            questions=questions
        )

    def _extract_text(self, spec: Dict[str, str], lowercase: bool = False) -> str:
        """Extract concatenated title and body from spec.

        Args:
            spec: Dictionary with 'title' and 'body' keys
            lowercase: If True, return lowercase text

        Returns:
            Concatenated text from title and body
        """
        text = f"{spec.get('title', '')} {spec.get('body', '')}"
        return text.lower() if lowercase else text

    def _has_acceptance_criteria(self, spec: Dict[str, str]) -> bool:
        """Check if spec has acceptance criteria."""
        text = self._extract_text(spec)

        # Check for explicit section with ## heading
        if re.search(r'##\s+(?:Acceptance\s+[Cc]riteria|Definition\s+of\s+Done|Requirements)', text, re.IGNORECASE):
            return True

        # Check for checkboxes (- [ ])
        if re.search(r'-\s*\[\s*[x\s]\s*\]', text):
            return True

        return False

    def _has_scope(self, spec: Dict[str, str]) -> bool:
        """Check if spec has clear scope definition."""
        text = self._extract_text(spec, lowercase=True)

        # Check for scope section
        if re.search(r'##\s+scope', text, re.IGNORECASE):
            return True

        # Check for scope keywords
        for keyword in self.SCOPE_KEYWORDS:
            if keyword in text:
                return True

        # Check if title is specific enough
        title = spec.get('title', '').lower()

        # Bug fixes and focused features usually have clear scope from title
        if re.search(r'\b(fix|bug|resolve|correct|patch)\b', title):
            return True

        # Titles with 4+ words suggesting specificity
        if len(title.split()) >= 4:
            return True

        return False

    def _has_dependencies_section(self, spec: Dict[str, str]) -> bool:
        """Check if spec mentions dependencies."""
        text = self._extract_text(spec, lowercase=True)

        # Check for explicit dependencies section
        for pattern in self.DEPENDENCY_PATTERNS:
            if re.search(pattern, text, re.IGNORECASE):
                return True

        # Check for dependency keywords
        if any(word in text for word in ['depends on', 'requires', 'needs', 'prerequisite']):
            return True

        return False

    def _find_ambiguous_terms(self, spec: Dict[str, str]) -> List[str]:
        """Find ambiguous or vague terms in spec."""
        text = f"{spec.get('body', '')}".lower()

        ambiguous = []
        for word in self.VAGUE_WORDS:
            pattern = rf'\b{word}\b'
            if re.search(pattern, text):
                # Check if it's used in context that makes it clearer
                # e.g., "faster (< 100ms)" is OK, but "faster" alone is vague
                context = re.search(rf'\b{word}\s*(?:\(|\[|:)?', text)
                if context and not re.search(rf'{word}\s*\(\s*<?\s*[0-9]', text):
                    if word not in ambiguous and len(ambiguous) < 3:
                        ambiguous.append(word)

        return ambiguous

    def _has_criteria_hints(self, spec: Dict[str, str]) -> bool:
        """Check if spec hints at how criteria will be tested."""
        text = f"{spec.get('body', '')}".lower()

        test_keywords = [
            'test', 'verify', 'check', 'assert', 'unit test', 'integration test',
            'manual', 'validate', 'confirm', 'ensure', 'pytest', 'jest'
        ]

        for keyword in test_keywords:
            if keyword in text:
                return True

        return False

    def _handle_gh_error(self, operation: str, issue_number: int, error: subprocess.CalledProcessError) -> None:
        """Handle GitHub CLI errors uniformly.

        Args:
            operation: Description of the operation that failed
            issue_number: GitHub issue number
            error: The subprocess error
        """
        stderr = error.stderr.decode() if isinstance(error.stderr, bytes) else error.stderr
        print(f"Error {operation} issue #{issue_number}: {stderr}", file=sys.stderr)
        sys.exit(1)

    def fetch_issue(self, issue_number: int) -> Dict[str, Any]:
        """Fetch issue from GitHub using gh CLI.

        Args:
            issue_number: GitHub issue number

        Returns:
            Issue data dictionary
        """
        try:
            result = subprocess.run(
                ['gh', 'issue', 'view', str(issue_number), '--json', 'number,title,body'],
                capture_output=True,
                text=True,
                check=True
            )
            return json.loads(result.stdout)
        except subprocess.CalledProcessError as e:
            self._handle_gh_error('fetching', issue_number, e)

    def post_comment(self, issue_number: int, result: ValidationResult) -> None:
        """Post validation comment to GitHub issue.

        Args:
            issue_number: GitHub issue number
            result: ValidationResult object
        """
        comment_text = result.as_github_comment()
        if not comment_text:
            return

        try:
            subprocess.run(
                ['gh', 'issue', 'comment', str(issue_number), '--body', comment_text],
                check=True,
                capture_output=True
            )
        except subprocess.CalledProcessError as e:
            self._handle_gh_error('posting comment to', issue_number, e)

    def add_label(self, issue_number: int, label: str) -> None:
        """Add label to GitHub issue.

        Args:
            issue_number: GitHub issue number
            label: Label to add
        """
        try:
            subprocess.run(
                ['gh', 'issue', 'edit', str(issue_number), '--add-label', label],
                check=True,
                capture_output=True
            )
        except subprocess.CalledProcessError as e:
            self._handle_gh_error('adding label to', issue_number, e)

    def validate_and_respond(self, issue_number: int) -> int:
        """Fetch issue, validate, and post comment if needed.

        Args:
            issue_number: GitHub issue number

        Returns:
            Exit code (0 on success)
        """
        # Fetch issue
        issue = self.fetch_issue(issue_number)

        # Validate
        result = self.validate(issue)

        # Post comment if invalid
        if not result.is_valid:
            self.post_comment(issue_number, result)
            self.add_label(issue_number, 'agent-needs-input')

        return 0


def main():
    """CLI entry point."""
    if len(sys.argv) < 2:
        print("Usage: python spec_validator.py <issue_number>", file=sys.stderr)
        sys.exit(1)

    try:
        issue_number = int(sys.argv[1])
    except ValueError:
        print("Issue number must be an integer", file=sys.stderr)
        sys.exit(1)

    validator = SpecValidator()
    exit_code = validator.validate_and_respond(issue_number)
    sys.exit(exit_code)


if __name__ == '__main__':
    main()
