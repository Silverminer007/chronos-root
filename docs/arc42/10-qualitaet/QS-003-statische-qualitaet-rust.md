---
id: QS-003
title: "Statische Qualität Rust"
status: entwurf
typ: qualitaetsszenario
quellen:
  - .github/workflows/backend-rust-test.yml
  - kb/adr/0012-rust-ci-quality-gates.md
verknuepft: [QA-002]
---

# QS-003: Statische Qualität Rust

**Szenario:** Ein PR ändert `rust-backend/`. **Erwartung:** fmt, clippy (Warnungen als Fehler), Unit- und Integrationstests, Coverage-Ratchet und Dependency-Prüfung sind grün. **Nachweis:** Jobs in `backend-rust-test.yml`.
