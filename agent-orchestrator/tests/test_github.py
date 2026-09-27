import json
import sys
from pathlib import Path
from unittest.mock import patch, MagicMock

sys.path.insert(0, str(Path(__file__).parent.parent))

from github_api import GitHubAPI


def test_list_ready_for_agent_issues():
    """Test querying GitHub for ready-for-agent labeled issues."""
    mock_output = json.dumps([
        {
            "number": 123,
            "title": "Feature A",
            "body": "Description of feature A",
            "labels": [{"name": "ready-for-agent"}]
        },
        {
            "number": 124,
            "title": "Feature B",
            "body": "Description of feature B",
            "labels": [{"name": "ready-for-agent"}]
        }
    ])

    with patch("github_api.subprocess.run") as mock_run:
        mock_run.return_value = MagicMock(stdout=mock_output, returncode=0)

        api = GitHubAPI(repo="test-org/test-repo")
        issues = api.list_ready_for_agent()

        assert len(issues) == 2
        assert issues[0]["number"] == 123
        assert issues[1]["number"] == 124
        assert all("ready-for-agent" in [l["name"] for l in issue["labels"]] for issue in issues)


def test_add_label():
    """Test adding a label to an issue."""
    with patch("github_api.subprocess.run") as mock_run:
        mock_run.return_value = MagicMock(returncode=0)

        api = GitHubAPI(repo="test-org/test-repo")
        result = api.add_label(123, "in-progress")

        assert result is True
        # Verify correct gh command was called
        mock_run.assert_called_once()


def test_remove_label():
    """Test removing a label from an issue."""
    with patch("github_api.subprocess.run") as mock_run:
        mock_run.return_value = MagicMock(returncode=0)

        api = GitHubAPI(repo="test-org/test-repo")
        result = api.remove_label(123, "ready-for-agent")

        assert result is True


if __name__ == "__main__":
    test_list_ready_for_agent_issues()
    test_add_label()
    test_remove_label()
    print("✓ All GitHub API tests passed")
