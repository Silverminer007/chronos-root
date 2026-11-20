---
id: QS-006
title: "Token-Ablauf ohne sichtbaren Fehler"
status: entwurf
typ: qualitaetsszenario
quellen:
  - frontend/public/push-sw.js
  - frontend/server/api/auth/refresh.post.ts
  - frontend/e2e/auth-redirects.spec.ts
verknuepft: [QA-004, QA-009, AE-008, R-003]
---

# QS-006: Token-Ablauf ohne sichtbaren Fehler

| | |
|---|---|
| **Reiz** | Das Access-Token läuft in weniger als 5 s ab (oder ist abgelaufen), während Nutzer:in die App benutzt. |
| **Antwort** | Der Service Worker erneuert das Token vorab (`POST /api/auth/refresh`, außerdem per Timer 30 s vor Ablauf), führt höchstens einen Refresh gleichzeitig aus und wiederholt eine Anfrage mit 401 einmal. Scheitert der Refresh, löscht der Server die Cookies und antwortet mit 401; die Sitzung gilt als abgelaufen und die App führt zur Landingpage ([QA-004](../../req42/06-qualitaet/QA-004-bedienbarkeit-sitzung-ohne-sichtbaren-token-ablauf.md)). |
| **Messung** | Kein sichtbarer Fehler und keine fehlgeschlagene `/api/v2/*`-Anfrage durch Ablauf, solange das Refresh-Token gültig ist. |
| **Nachweis** | Kein automatischer Test **(?)**; E2E deckt nur Weiterleitungen ohne Cookies ab. |
