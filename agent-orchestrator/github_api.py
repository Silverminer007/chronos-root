import json
import subprocess
from typing import List, Dict, Any


class GitHubAPI:
    def __init__(self, repo: str):
        self.repo = repo

    def list_ready_for_agent(self) -> List[Dict[str, Any]]:
        """Query issues with ready-for-agent label via gh CLI."""
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
        """Apply label to issue via gh CLI."""
        cmd = [
            "gh", "issue", "edit",
            str(issue_number),
            "--repo", self.repo,
            "--add-label", label
        ]
        result = subprocess.run(cmd, capture_output=True, text=True)
        return result.returncode == 0

    def remove_label(self, issue_number: int, label: str) -> bool:
        """Remove label from issue via gh CLI."""
        cmd = [
            "gh", "issue", "edit",
            str(issue_number),
            "--repo", self.repo,
            "--remove-label", label
        ]
        result = subprocess.run(cmd, capture_output=True, text=True)
        return result.returncode == 0

    def post_comment(self, issue_number: int, body: str) -> bool:
        """Add comment to issue via gh CLI."""
        cmd = [
            "gh", "issue", "comment",
            str(issue_number),
            "--repo", self.repo,
            "--body", body
        ]
        result = subprocess.run(cmd, capture_output=True, text=True)
        return result.returncode == 0
