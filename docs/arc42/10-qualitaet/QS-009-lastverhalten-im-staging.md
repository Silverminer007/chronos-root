---
id: QS-009
title: "Lastverhalten im Staging"
status: entwurf
typ: qualitaetsszenario
quellen:
  - load-tests/staging.js
verknuepft: []
---

# QS-009: Lastverhalten im Staging

**Szenario:** k6-Test mit 10 virtuellen Benutzern, 2 min Anlauf, 25 min Dauerlast, 3 min Auslauf gegen das Rust-Backend im Staging. **Erwartung:** Zielschwellen laut Skript; konkrete Anforderungen sind nicht festgelegt **(?)**. **Nachweis:** `load-tests/staging.js`.
