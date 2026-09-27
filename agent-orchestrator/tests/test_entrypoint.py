from pathlib import Path
import subprocess


def test_entrypoint_exists():
    """Test that entrypoint.sh exists."""
    entrypoint_path = Path(__file__).parent.parent / "entrypoint.sh"
    assert entrypoint_path.exists(), "entrypoint.sh not found"


def test_entrypoint_is_executable():
    """Test that entrypoint.sh is executable."""
    entrypoint_path = Path(__file__).parent.parent / "entrypoint.sh"
    assert entrypoint_path.stat().st_mode & 0o111, "entrypoint.sh is not executable"


def test_entrypoint_has_shebang():
    """Test that entrypoint.sh has bash shebang."""
    entrypoint_path = Path(__file__).parent.parent / "entrypoint.sh"
    content = entrypoint_path.read_text()
    assert content.startswith("#!/bin/bash"), "entrypoint.sh missing bash shebang"


def test_entrypoint_checks_required_env_vars():
    """Test that entrypoint.sh checks for required environment variables."""
    entrypoint_path = Path(__file__).parent.parent / "entrypoint.sh"
    content = entrypoint_path.read_text()

    required_vars = ["GITHUB_REPO_URL", "GH_TOKEN"]

    for var in required_vars:
        assert var in content, f"Environment variable '{var}' check not found in entrypoint.sh"


def test_entrypoint_clones_or_updates_repo():
    """Test that entrypoint.sh clones or updates the repository."""
    entrypoint_path = Path(__file__).parent.parent / "entrypoint.sh"
    content = entrypoint_path.read_text()

    assert "git clone" in content, "git clone not found in entrypoint.sh"
    assert "git pull" in content, "git pull not found in entrypoint.sh"


def test_entrypoint_runs_poller():
    """Test that entrypoint.sh runs the poller script."""
    entrypoint_path = Path(__file__).parent.parent / "entrypoint.sh"
    content = entrypoint_path.read_text()

    assert "poller.sh" in content, "poller.sh execution not found in entrypoint.sh"


def test_entrypoint_syntax():
    """Test that entrypoint.sh has valid bash syntax."""
    entrypoint_path = Path(__file__).parent.parent / "entrypoint.sh"
    result = subprocess.run(
        ["bash", "-n", str(entrypoint_path)],
        capture_output=True,
        text=True
    )
    assert result.returncode == 0, f"Bash syntax error: {result.stderr}"
