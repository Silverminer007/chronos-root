---
id: QS-009
title: "Lastverhalten"
status: entwurf
typ: qualitaetsszenario
quellen:
  - backend/performance-tests/k6/config/options.js
  - backend/performance-tests/README.md
verknuepft: [QA-005]
---

# QS-009: Lastverhalten

| | |
|---|---|
| **Reiz** | 50 virtuelle Benutzer (konfigurierbar `K6_VUS`) rufen 2 min lang die fachlichen Backend-Endpunkte (ohne Admin-API) auf (Termine, Teilnahmen, Nachrichten, Gruppen, Freunde, Einstellungen, Push); die k6-Szenarien liegen in `backend/performance-tests/k6/scenarios/`. |
| **Antwort** | Das Backend beantwortet die Anfragen fehlerfrei. |
| **Messung** | Gesamt p95 < 2000 ms, p99 < 5000 ms, Fehlerrate < 5 %; je Endpunkt p95: Terminliste < 1000 ms, Termin anlegen < 1500 ms, Termin lesen < 500 ms, Benutzer und Einstellungen < 300 ms, Gruppen < 800 ms, Freunde < 600 ms. |
| **Nachweis** | `backend/performance-tests/scripts/run-perf-tests.sh` (lokal mit Docker Compose, Keycloak, PostgreSQL). Eine Pipeline, die ihn ausführt, existiert nicht, obwohl das README es behauptet **(?)**. |
