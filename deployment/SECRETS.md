# Agent Orchestrator Secrets Management

This document describes how to manage secrets for the Chronos Agent Orchestrator.

## Required Secrets

The agent orchestrator requires the following secrets to be configured in the Kubernetes cluster:

### orchestrator-secrets

A Kubernetes Secret containing:
- `repo-url`: The GitHub repository URL (e.g., `https://github.com/org/chronos-root`)
- `github-token`: A GitHub Personal Access Token (PAT) with appropriate permissions

## Creating Secrets

### Create the orchestrator-secrets

```bash
kubectl create secret generic orchestrator-secrets \
  --from-literal=repo-url=https://github.com/org/chronos-root \
  --from-literal=github-token=ghp_xxxxx \
  -n chronos-prod
```

### Verify Secret Creation

```bash
kubectl get secret orchestrator-secrets -n chronos-prod
kubectl describe secret orchestrator-secrets -n chronos-prod
```

## Secret Injection

The secrets are injected into the agent orchestrator pod via environment variables:

- `GITHUB_REPO_URL` - injected from `repo-url` secret key
- `GH_TOKEN` - injected from `github-token` secret key

These environment variables are referenced in:
- `deployment/kubernetes/agent-orchestrator.yaml` - CronJob specification

## Updating Secrets

To update an existing secret:

```bash
kubectl delete secret orchestrator-secrets -n chronos-prod
kubectl create secret generic orchestrator-secrets \
  --from-literal=repo-url=<new-repo-url> \
  --from-literal=github-token=<new-token> \
  -n chronos-prod
```

## Security Considerations

- Store GitHub tokens securely (use environment variables or secret management tools)
- Rotate GitHub tokens periodically
- Use minimal required scopes for GitHub tokens
- Consider using Sealed Secrets or External Secrets Operator for production
