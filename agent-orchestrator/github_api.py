import json
import subprocess
from typing import List, Dict, Any


class GitHubAPI:
    def __init__(self, repo: str):
        self.repo = repo

    def list_ready_for_agent(self) -> List[Dict[str, Any]]:
        """List all issues labeled 'ready-for-agent'."""
        cmd = [
            "gh", "issue", "list",
            "--repo", self.repo,
            "--label", "ready-for-agent",
            "--json", "number,title,body,labels"
        ]
        result = subprocess.run(cmd, capture_output=True, text=True)
        if result.returncode != 0:
            return []
        try:
            return json.loads(result.stdout)
        except json.JSONDecodeError:
            return []

    def add_label(self, issue_number: int, label: str) -> bool:
        """Add a label to an issue."""
        cmd = [
            "gh", "issue", "edit",
            str(issue_number),
            "--repo", self.repo,
            "--add-label", label
        ]
        result = subprocess.run(cmd, capture_output=True, text=True)
        return result.returncode == 0

    def remove_label(self, issue_number: int, label: str) -> bool:
        """Remove a label from an issue."""
        cmd = [
            "gh", "issue", "edit",
            str(issue_number),
            "--repo", self.repo,
            "--remove-label", label
        ]
        result = subprocess.run(cmd, capture_output=True, text=True)
        return result.returncode == 0

    def post_comment(self, issue_number: int, body: str) -> bool:
        """Post a comment on an issue."""
        cmd = [
            "gh", "issue", "comment",
            str(issue_number),
            "--repo", self.repo,
            "--body", body
        ]
        result = subprocess.run(cmd, capture_output=True, text=True)
        return result.returncode == 0
