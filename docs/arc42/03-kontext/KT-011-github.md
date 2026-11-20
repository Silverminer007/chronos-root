---
id: KT-011
title: Nachbarsystem GitHub (Actions und Container-Registry)
status: entwurf
typ: nachbarsystem
quellen:
  - .github/workflows
  - deployment/values.yaml
  - deployment/github-actions-rbac.yaml
verknuepft: [VT-004, RA-010]
---

# Nachbarsystem GitHub (Actions und Container-Registry)

- **Rolle:** Quellcode, Pull Requests, CI/CD und Registry `ghcr.io`.
- **Schnittstellen:** GitHub Actions bauen und prüfen (`backend-ci.yml`, `frontend-ci.yml`, `deployment-helm-test.yml`, `repo-hygiene.yml`), pushen die Images `ghcr.io/silverminer007/chronos-backend` und `…/chronos-frontend` und lösen per `repository_dispatch` das Deployment aus ([VT-004](../07-verteilung/VT-004-ci-cd-kette.md)). Das Deployment authentifiziert sich am Cluster mit dem ServiceAccount `github-actions-deployer` (`KUBE_TOKEN`, `KUBE_CA_CERT`, `KUBE_SERVER` als Actions-Secrets). Der Cluster zieht Images mit dem Pull-Secret `chronos-docker-secret`.
