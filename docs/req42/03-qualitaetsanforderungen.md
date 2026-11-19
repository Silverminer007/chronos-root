---
title: Qualitätsanforderungen
status: entwurf
---

# Qualitätsanforderungen

| ID | Qualitätsmerkmal | Szenario | Messbar durch |
|---|---|---|---|
| QA-001 | Kompatibilität | Rust- und Java-Backend liefern dieselbe API; der Contract-Harness weist keine unerwarteten Abweichungen aus. | `contract-tests/` |
| QA-002 | Wartbarkeit | Jeder PR besteht Checkstyle, SpotBugs, PMD bzw. `cargo fmt`/`clippy`, ESLint und Typecheck. | CI |
| QA-003 | Bedienbarkeit | Die gesamte Oberfläche ist auf Deutsch. | E2E, Review |
| QA-004 | Sicherheit | Der Token-Ablauf führt nicht zu sichtbaren 401-Fehlern. | (?) |

Weitere Ziele (Performance, Verfügbarkeit): **(?)** mit dem Product Owner klären; `load-tests/` liefert Ausgangswerte.
