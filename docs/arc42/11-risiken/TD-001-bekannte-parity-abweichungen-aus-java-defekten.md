---
id: TD-001
title: "Bekannte Parity-Abweichungen aus Java-Defekten"
status: entwurf
typ: technische-schuld
quellen:
  - kb/adr/ADR-2026-10-20-028-small-parity-deviations.md
  - kb/adr/ADR-2026-10-20-029-final-parity-run-fixes.md
  - contract-tests/allowlist.json
verknuepft: [QA-001]
---

# TD-001: Bekannte Parity-Abweichungen aus Java-Defekten

Elf Fälle, in denen Java mit 500 antwortet (Java-Defekt, Ticket #162), stehen als Allowlist-Einträge im Harness; Rust kopiert den Defekt nicht (z. B. 405 statt 500 bei falscher Methode, 404 bei unbekanntem Admin-Benutzer). Fehlerkorrekturen im Java-Backend würden die Allowlist verkleinern.
