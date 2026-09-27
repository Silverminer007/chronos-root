from pathlib import Path


def test_kubernetes_manifest_file_exists():
    """Test that Kubernetes deployment manifest file exists."""
    manifest_path = Path(__file__).parent.parent.parent / "deployment/kubernetes/agent-orchestrator.yaml"
    assert manifest_path.exists(), "agent-orchestrator.yaml not found"


def test_kubernetes_manifest_has_cronjob():
    """Test that manifest contains a CronJob resource."""
    manifest_path = Path(__file__).parent.parent.parent / "deployment/kubernetes/agent-orchestrator.yaml"
    content = manifest_path.read_text()

    assert "kind: CronJob" in content, "CronJob resource not found in manifest"


def test_kubernetes_cronjob_has_schedule():
    """Test that CronJob has valid schedule."""
    manifest_path = Path(__file__).parent.parent.parent / "deployment/kubernetes/agent-orchestrator.yaml"
    content = manifest_path.read_text()

    assert 'schedule: "*/5 * * * *"' in content or "schedule: '*/5 * * * *'" in content, "5-minute schedule not found"


def test_kubernetes_cronjob_has_container():
    """Test that CronJob has container specification."""
    manifest_path = Path(__file__).parent.parent.parent / "deployment/kubernetes/agent-orchestrator.yaml"
    content = manifest_path.read_text()

    assert "name: orchestrator" in content, "orchestrator container not found"
    assert "containers:" in content, "containers not found"


def test_kubernetes_cronjob_has_env_vars():
    """Test that CronJob container has environment variables."""
    manifest_path = Path(__file__).parent.parent.parent / "deployment/kubernetes/agent-orchestrator.yaml"
    content = manifest_path.read_text()

    assert "GITHUB_REPO_URL" in content, "GITHUB_REPO_URL env var not found"
    assert "GH_TOKEN" in content, "GH_TOKEN env var not found"
    assert "env:" in content, "env section not found"


def test_kubernetes_manifest_has_serviceaccount():
    """Test that manifest contains ServiceAccount."""
    manifest_path = Path(__file__).parent.parent.parent / "deployment/kubernetes/agent-orchestrator.yaml"
    content = manifest_path.read_text()

    assert "kind: ServiceAccount" in content, "ServiceAccount not found in manifest"


def test_kubernetes_manifest_has_role():
    """Test that manifest contains Role."""
    manifest_path = Path(__file__).parent.parent.parent / "deployment/kubernetes/agent-orchestrator.yaml"
    content = manifest_path.read_text()

    assert "kind: Role" in content, "Role not found in manifest"


def test_kubernetes_manifest_has_rolebinding():
    """Test that manifest contains RoleBinding."""
    manifest_path = Path(__file__).parent.parent.parent / "deployment/kubernetes/agent-orchestrator.yaml"
    content = manifest_path.read_text()

    assert "kind: RoleBinding" in content, "RoleBinding not found in manifest"


def test_kubernetes_cronjob_uses_correct_namespace():
    """Test that CronJob is in chronos-prod namespace."""
    manifest_path = Path(__file__).parent.parent.parent / "deployment/kubernetes/agent-orchestrator.yaml"
    content = manifest_path.read_text()

    assert "namespace: chronos-prod" in content, "chronos-prod namespace not found"


def test_kubernetes_cronjob_has_resource_limits():
    """Test that CronJob container has resource limits."""
    manifest_path = Path(__file__).parent.parent.parent / "deployment/kubernetes/agent-orchestrator.yaml"
    content = manifest_path.read_text()

    assert "limits:" in content, "Resource limits not found"
    assert "requests:" in content, "Resource requests not found"
    assert "memory:" in content, "Memory resource not found"
    assert "cpu:" in content, "CPU resource not found"
