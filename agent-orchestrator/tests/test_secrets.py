from pathlib import Path


def test_secrets_configuration_documentation_exists():
    """Test that secrets configuration documentation exists."""
    doc_path = Path(__file__).parent.parent.parent / "deployment/SECRETS.md"
    assert doc_path.exists(), "SECRETS.md documentation not found"


def test_secrets_documentation_has_creation_instructions():
    """Test that secrets documentation includes creation instructions."""
    doc_path = Path(__file__).parent.parent.parent / "deployment/SECRETS.md"
    content = doc_path.read_text()

    assert "kubectl create secret" in content, "kubectl create secret instruction not found"
    assert "orchestrator-secrets" in content, "orchestrator-secrets name not found"


def test_secrets_documentation_documents_repo_url():
    """Test that secrets documentation documents GITHUB_REPO_URL secret."""
    doc_path = Path(__file__).parent.parent.parent / "deployment/SECRETS.md"
    content = doc_path.read_text()

    assert "repo-url" in content or "GITHUB_REPO_URL" in content, "repo-url documentation not found"


def test_secrets_documentation_documents_github_token():
    """Test that secrets documentation documents GH_TOKEN secret."""
    doc_path = Path(__file__).parent.parent.parent / "deployment/SECRETS.md"
    content = doc_path.read_text()

    assert "github-token" in content or "GH_TOKEN" in content, "github-token documentation not found"


def test_kubernetes_manifest_references_secrets():
    """Test that Kubernetes manifest references the secrets correctly."""
    manifest_path = Path(__file__).parent.parent.parent / "deployment/kubernetes/agent-orchestrator.yaml"
    content = manifest_path.read_text()

    assert "secretKeyRef:" in content, "secretKeyRef not found in manifest"
    assert "orchestrator-secrets" in content, "orchestrator-secrets not referenced in manifest"
