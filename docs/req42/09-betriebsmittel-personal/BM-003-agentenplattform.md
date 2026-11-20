---
id: BM-003
title: Agentenplattform
status: entwurf
typ: betriebsmittel
quellen: [.agent-orchestrator/README.md, .agent-orchestrator/project.env, .agent-orchestrator/nightly.sh]
verknuepft: [SH-007]
---

# BM-003: Agentenplattform

KI-Agenten arbeiten über den Agent Orchestrator (eigenes Repository) auf einem Kubernetes-Cluster in den Namespaces `chronos-agents` und `chronos-nightly`. Quoten laut Plattform-Dokumentation: Agenten 11 CPU / 16 Gi (Requests), 18 CPU / 52 Gi (Limits), 14 Pods; Nightly 2 CPU / 4 Gi (Requests), 4 CPU / 9 Gi (Limits), 2 Pods. Zugang zu Modellen und GitHub erfolgt über Cluster-Secrets (nicht im Repository). Nächtlich laufen Frontend-Prüfung (Installation, Lint, Typprüfung, Build) und statische Java-Prüfung ohne Testausführung. Kosten der Modellnutzung: [RA-013](../12-risiken-annahmen/RA-013-vom-product-owner-festzulegende-werte-und-entscheidungen.md).
