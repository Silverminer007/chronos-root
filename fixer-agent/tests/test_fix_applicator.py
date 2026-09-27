"""Tests for the fix applicator component."""

import unittest
import tempfile
import os
from pathlib import Path
from src.finding_parser import Finding, Severity
from src.fix_applicator import FixApplicator, FixResult


class TestFixApplicator(unittest.TestCase):
    """Test the fix applicator that applies fixes to source files."""

    def setUp(self):
        """Create temporary directory for test files."""
        self.test_dir = tempfile.mkdtemp()

    def tearDown(self):
        """Clean up temporary directory."""
        import shutil
        shutil.rmtree(self.test_dir)

    def test_apply_simple_fix(self):
        """Apply a simple one-line fix."""
        # Create a test file
        test_file = Path(self.test_dir) / "test.py"
        test_file.write_text("x = 1\ny = 2\nz = 3\n")

        applicator = FixApplicator(self.test_dir)
        finding = Finding(
            severity=Severity.CRITICAL,
            file="test.py",
            line=2,
            summary="Change y to 3"
        )

        result = applicator.apply(
            finding,
            lambda content, line_num: content.replace("y = 2", "y = 3")
        )

        self.assertEqual(result, FixResult.SUCCESS)
        self.assertEqual(test_file.read_text(), "x = 1\ny = 3\nz = 3\n")

    def test_apply_fix_with_rollback_on_test_failure(self):
        """Rollback fix if test fails."""
        test_file = Path(self.test_dir) / "test.py"
        test_file.write_text("def func():\n    return 1\n")

        applicator = FixApplicator(self.test_dir)
        finding = Finding(
            severity=Severity.MAJOR,
            file="test.py",
            line=2,
            summary="Bad fix"
        )

        # Apply a fix that will cause test to fail
        result = applicator.apply(
            finding,
            lambda content, line_num: content.replace("return 1", "return 'invalid'"),
            test_callback=lambda: False  # Simulate test failure
        )

        self.assertEqual(result, FixResult.TEST_FAILED)
        # File should be restored to original state
        self.assertEqual(test_file.read_text(), "def func():\n    return 1\n")

    def test_apply_fix_with_retry(self):
        """Retry fix application with different approach."""
        test_file = Path(self.test_dir) / "test.py"
        test_file.write_text("x = 1\n")

        applicator = FixApplicator(self.test_dir)
        finding = Finding(
            severity=Severity.MINOR,
            file="test.py",
            line=1,
            summary="Change x"
        )

        attempts = [0]
        def fix_with_retry(content, line_num):
            attempts[0] += 1
            if attempts[0] == 1:
                return content.replace("x = 1", "x = INVALID")
            else:
                return content.replace("x = 1", "x = 2")

        # First attempt will fail, second should succeed
        result = applicator.apply(
            finding,
            fix_with_retry,
            test_callback=lambda: attempts[0] > 1,  # Only pass on retry
            max_retries=2
        )

        self.assertEqual(result, FixResult.SUCCESS)
        self.assertEqual(test_file.read_text(), "x = 2\n")

    def test_apply_fix_to_nonexistent_file(self):
        """Handle nonexistent files gracefully."""
        applicator = FixApplicator(self.test_dir)
        finding = Finding(
            severity=Severity.CRITICAL,
            file="nonexistent.py",
            line=1,
            summary="Fix"
        )

        result = applicator.apply(
            finding,
            lambda content, line_num: content
        )

        self.assertEqual(result, FixResult.FILE_NOT_FOUND)

    def test_apply_fix_to_invalid_line_number(self):
        """Handle invalid line numbers gracefully."""
        test_file = Path(self.test_dir) / "test.py"
        test_file.write_text("x = 1\n")

        applicator = FixApplicator(self.test_dir)
        finding = Finding(
            severity=Severity.MAJOR,
            file="test.py",
            line=999,  # Line doesn't exist
            summary="Fix"
        )

        result = applicator.apply(
            finding,
            lambda content, line_num: content
        )

        self.assertEqual(result, FixResult.LINE_NOT_FOUND)

    def test_fix_with_context_lines(self):
        """Apply fix with surrounding context."""
        test_file = Path(self.test_dir) / "test.py"
        test_file.write_text("def foo():\n    x = 1\n    y = 2\n    return x\n")

        applicator = FixApplicator(self.test_dir)
        finding = Finding(
            severity=Severity.CRITICAL,
            file="test.py",
            line=2,
            summary="Fix x"
        )

        # Fix function receives both content and context
        def fix_func(content, line_num):
            lines = content.split('\n')
            # Get context: line before and after
            context_start = max(0, line_num - 2)
            context_end = min(len(lines), line_num + 1)
            # Modify only this line
            lines[line_num - 1] = "    x = 10"
            return '\n'.join(lines)

        result = applicator.apply(finding, fix_func)
        self.assertEqual(result, FixResult.SUCCESS)
        self.assertEqual(test_file.read_text(), "def foo():\n    x = 10\n    y = 2\n    return x\n")


if __name__ == '__main__':
    unittest.main()
