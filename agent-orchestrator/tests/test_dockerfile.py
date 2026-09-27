from pathlib import Path


def test_dockerfile_orchestrator_exists():
    """Test that Dockerfile.orchestrator exists."""
    dockerfile_path = Path(__file__).parent.parent / "Dockerfile.orchestrator"
    assert dockerfile_path.exists(), "Dockerfile.orchestrator not found"


def test_dockerfile_orchestrator_has_required_tools():
    """Test that Dockerfile.orchestrator installs all required tools."""
    dockerfile_path = Path(__file__).parent.parent / "Dockerfile.orchestrator"
    content = dockerfile_path.read_text()

    required_tools = ["git", "curl", "jq", "gh"]

    for tool in required_tools:
        assert tool in content, f"Tool '{tool}' installation not found in Dockerfile"


def test_dockerfile_orchestrator_creates_user():
    """Test that Dockerfile.orchestrator creates orchestrator user."""
    dockerfile_path = Path(__file__).parent.parent / "Dockerfile.orchestrator"
    content = dockerfile_path.read_text()

    assert "useradd" in content, "User creation not found in Dockerfile"
    assert "orchestrator" in content, "orchestrator user not found in Dockerfile"


def test_dockerfile_orchestrator_sets_entrypoint():
    """Test that Dockerfile.orchestrator has ENTRYPOINT."""
    dockerfile_path = Path(__file__).parent.parent / "Dockerfile.orchestrator"
    content = dockerfile_path.read_text()

    assert "ENTRYPOINT" in content, "ENTRYPOINT not found in Dockerfile"
    assert "entrypoint.sh" in content, "entrypoint.sh not referenced in Dockerfile"
