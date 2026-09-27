from pathlib import Path
import re


def test_systemd_service_file_exists():
    """Test that agent-orchestrator.service file exists."""
    service_path = Path(__file__).parent.parent.parent / "deployment/systemd/agent-orchestrator.service"
    assert service_path.exists(), "agent-orchestrator.service not found"


def test_systemd_timer_file_exists():
    """Test that agent-orchestrator.timer file exists."""
    timer_path = Path(__file__).parent.parent.parent / "deployment/systemd/agent-orchestrator.timer"
    assert timer_path.exists(), "agent-orchestrator.timer not found"


def test_systemd_service_has_required_sections():
    """Test that service file has required sections."""
    service_path = Path(__file__).parent.parent.parent / "deployment/systemd/agent-orchestrator.service"
    content = service_path.read_text()

    required_sections = ["[Unit]", "[Service]", "[Install]"]

    for section in required_sections:
        assert section in content, f"Section '{section}' not found in service file"


def test_systemd_service_has_description():
    """Test that service file has Description."""
    service_path = Path(__file__).parent.parent.parent / "deployment/systemd/agent-orchestrator.service"
    content = service_path.read_text()

    assert "Description=" in content, "Description not found in service file"
    assert "Agent Orchestrator" in content, "Agent Orchestrator name not found"


def test_systemd_service_runs_poller():
    """Test that service file runs the poller script."""
    service_path = Path(__file__).parent.parent.parent / "deployment/systemd/agent-orchestrator.service"
    content = service_path.read_text()

    assert "ExecStart=" in content, "ExecStart not found in service file"
    assert "poller.sh" in content, "poller.sh not found in service file"


def test_systemd_service_has_timeout():
    """Test that service file has timeout configuration."""
    service_path = Path(__file__).parent.parent.parent / "deployment/systemd/agent-orchestrator.service"
    content = service_path.read_text()

    assert "TimeoutStartSec=" in content, "TimeoutStartSec not found in service file"


def test_systemd_service_has_restart_policy():
    """Test that service file has restart policy."""
    service_path = Path(__file__).parent.parent.parent / "deployment/systemd/agent-orchestrator.service"
    content = service_path.read_text()

    assert "Restart=" in content, "Restart policy not found in service file"


def test_systemd_timer_has_required_sections():
    """Test that timer file has required sections."""
    timer_path = Path(__file__).parent.parent.parent / "deployment/systemd/agent-orchestrator.timer"
    content = timer_path.read_text()

    required_sections = ["[Unit]", "[Timer]", "[Install]"]

    for section in required_sections:
        assert section in content, f"Section '{section}' not found in timer file"


def test_systemd_timer_has_schedule():
    """Test that timer file has OnUnitActiveSec schedule."""
    timer_path = Path(__file__).parent.parent.parent / "deployment/systemd/agent-orchestrator.timer"
    content = timer_path.read_text()

    assert "OnUnitActiveSec=" in content, "OnUnitActiveSec not found in timer file"
    assert "5m" in content, "5 minute schedule not found in timer file"


def test_systemd_timer_has_boot_delay():
    """Test that timer file has initial boot delay."""
    timer_path = Path(__file__).parent.parent.parent / "deployment/systemd/agent-orchestrator.timer"
    content = timer_path.read_text()

    assert "OnBootSec=" in content, "OnBootSec not found in timer file"


def test_systemd_timer_references_service():
    """Test that timer file references the service."""
    timer_path = Path(__file__).parent.parent.parent / "deployment/systemd/agent-orchestrator.timer"
    content = timer_path.read_text()

    assert "Requires=" in content, "Requires not found in timer file"
    assert "agent-orchestrator.service" in content, "Service reference not found in timer file"


def test_systemd_service_file_syntax():
    """Test that service file has valid systemd syntax."""
    service_path = Path(__file__).parent.parent.parent / "deployment/systemd/agent-orchestrator.service"
    content = service_path.read_text()

    # Check for basic syntax: should have = in key=value pairs
    lines = content.strip().split('\n')
    for line in lines:
        line = line.strip()
        if line and not line.startswith('[') and not line.startswith(';') and not line.startswith('#'):
            assert '=' in line, f"Invalid syntax in line: {line}"


def test_systemd_timer_file_syntax():
    """Test that timer file has valid systemd syntax."""
    timer_path = Path(__file__).parent.parent.parent / "deployment/systemd/agent-orchestrator.timer"
    content = timer_path.read_text()

    # Check for basic syntax: should have = in key=value pairs
    lines = content.strip().split('\n')
    for line in lines:
        line = line.strip()
        if line and not line.startswith('[') and not line.startswith(';') and not line.startswith('#'):
            assert '=' in line, f"Invalid syntax in line: {line}"
