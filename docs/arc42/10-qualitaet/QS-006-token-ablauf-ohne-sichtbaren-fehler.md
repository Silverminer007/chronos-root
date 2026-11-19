---
id: QS-006
title: "Token-Ablauf ohne sichtbaren Fehler"
status: entwurf
typ: qualitaetsszenario
quellen:
  - frontend/public/push-sw.js
  - kb/adr/ADR-2025-09-28-005-service-worker.md
verknuepft: [QA-004]
---

# QS-006: Token-Ablauf ohne sichtbaren Fehler

**Szenario:** Das Access-Token läuft in weniger als 5 s ab. **Erwartung:** Der Service Worker erneuert es vorab (`POST /api/auth/refresh`); bei 401 wiederholt er den Request einmal. Der Nutzer sieht keinen Fehler. **Nachweis:** Automatischer Test dafür **(?)** nicht gefunden; E2E-Tests decken nur Redirects ohne Cookies ab.
