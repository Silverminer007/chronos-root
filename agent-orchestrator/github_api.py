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

    def get_pr_checks(self, pr_number: int) -> Dict[str, Any]:
        """Get PR CI status and check runs."""
        cmd = [
            "gh", "pr", "checks",
            str(pr_number),
            "--repo", self.repo,
            "--json", "status,conclusion,name,state"
        ]
        result = subprocess.run(cmd, capture_output=True, text=True)
        if result.returncode != 0:
            return {"status": "unknown", "check_runs": []}

        try:
            check_runs = json.loads(result.stdout)
            status = "success"
            if any(run.get("state") == "FAILURE" for run in check_runs):
                status = "failure"
            elif any(run.get("state") in ("PENDING", "IN_PROGRESS") for run in check_runs):
                status = "pending"

            return {
                "status": status,
                "check_runs": check_runs
            }
        except (json.JSONDecodeError, KeyError):
            return {"status": "unknown", "check_runs": []}
