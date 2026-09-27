"""Applicator for applying code fixes to source files."""

from enum import Enum
from pathlib import Path
from typing import Callable, Optional
from src.finding_parser import Finding


class FixResult(Enum):
    """Result of fix application attempt."""
    SUCCESS = "success"
    TEST_FAILED = "test_failed"
    FILE_NOT_FOUND = "file_not_found"
    LINE_NOT_FOUND = "line_not_found"
    MAX_RETRIES_EXCEEDED = "max_retries_exceeded"


class FixApplicator:
    """Applies fixes to source files and verifies them with tests."""

    def __init__(self, base_dir: str):
        """Initialize the applicator with a base directory.

        Args:
            base_dir: The base directory for all source files
        """
        self.base_dir = Path(base_dir)

    def apply(
        self,
        finding: Finding,
        fix_func: Callable[[str, int], str],
        test_callback: Optional[Callable[[], bool]] = None,
        max_retries: int = 3,
    ) -> FixResult:
        """Apply a fix to a file for a given finding.

        Args:
            finding: The code review finding to fix
            fix_func: Function that takes (content, line_number) and returns fixed content
            test_callback: Optional function to verify the fix (returns True if tests pass)
            max_retries: Maximum number of retry attempts

        Returns:
            FixResult indicating success or failure reason
        """
        file_path = self.base_dir / finding.file

        # Check if file exists
        if not file_path.exists():
            return FixResult.FILE_NOT_FOUND

        # Read the original content
        try:
            original_content = file_path.read_text()
        except IOError:
            return FixResult.FILE_NOT_FOUND

        # Check if line number is valid
        lines = original_content.split('\n')
        if finding.line < 1 or finding.line > len(lines):
            return FixResult.LINE_NOT_FOUND

        # Try to apply the fix
        for attempt in range(max_retries):
            try:
                fixed_content = fix_func(original_content, finding.line)
            except Exception:
                # If the fix function raises an exception, it's a failed attempt
                if attempt == max_retries - 1:
                    return FixResult.MAX_RETRIES_EXCEEDED
                continue

            # Write the fixed content
            file_path.write_text(fixed_content)

            # Run tests if callback provided
            if test_callback is not None:
                try:
                    test_passed = test_callback()
                except Exception:
                    test_passed = False

                if test_passed:
                    return FixResult.SUCCESS
                else:
                    # Tests failed, restore original and retry
                    file_path.write_text(original_content)
                    if attempt == max_retries - 1:
                        return FixResult.TEST_FAILED
            else:
                # No test callback, assume success
                return FixResult.SUCCESS

        return FixResult.MAX_RETRIES_EXCEEDED
