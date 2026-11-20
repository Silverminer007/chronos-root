---
id: TD-004
title: "Frontend-Unit-Tests fehlen"
status: entwurf
typ: technische-schuld
quellen:
  - frontend/package.json
  - frontend/e2e/
  - "Ticket #349 (offen)"
verknuepft: [RB-008, QS-002]
---

# TD-004: Frontend-Unit-Tests fehlen

Das Frontend hat nur drei Playwright-E2E-Dateien (Landing, öffentliche Seiten, Weiterleitungen ohne Anmeldung); Vitest ist nicht eingerichtet, Store- und Service-Worker-Logik sind ungetestet. Ticket #349 plant Vitest mit einem ersten Test für den `groups`-Store.

**Auswirkung:** Regressionen in Stores, Auth-Ablauf und Service Worker fallen erst in der Nutzung auf; Schnittstellenabweichungen zum Backend ([TD-014](TD-014-frontend-ruft-pfade-ohne-backend-gegenstueck.md)) blieben unentdeckt.

**Maßnahme:** Vitest einrichten und mit den Stores sowie der Service-Worker-Logik beginnen (Ticket #349).
