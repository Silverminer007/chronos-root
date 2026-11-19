---
id: QA-005
title: Performance unter Last
status: entwurf
typ: qualitaetsanforderung
quellen: [load-tests/staging.js]
verknuepft: []
---

# QA-005: Performance unter Last

**Qualitätsmerkmal:** Performance

## Szenario
Das Rust-Backend bleibt unter einem 30-minütigen Lasttest (Aufbau 2 min, 25 min stabil mit 10 virtuellen Nutzern, Abbau 3 min) funktionsfähig. **(?)** Grenzwerte für Antwortzeiten und Fehlerrate festlegen.

## Messbar durch
`load-tests/staging.js` (k6) gegen Staging.
