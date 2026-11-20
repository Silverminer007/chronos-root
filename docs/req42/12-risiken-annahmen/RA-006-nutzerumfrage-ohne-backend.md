---
id: RA-006
title: Nutzerumfrage ohne Backend
status: entwurf
typ: risiko
quellen: [frontend/app/pages/survey/[surveyId].vue, frontend/app/pages/admin/index.vue]
verknuepft: [Z-006, F-016, FA-015]
---

# RA-006: Nutzerumfrage ohne Backend

**Risiko:** Die Umfrage-Seite und die Admin-Einladung rufen `survey/{id}/eligible`, `survey/{id}/responses` und `admin/survey/{id}/notify` auf; im Backend gibt es dafür keinen Code. Antworten werden daher nicht gespeichert und die Einladung nicht versendet, das Absenden endet mit einer Fehlermeldung ([FA-015](../04-product-backlog/anforderungen/FA-015-nutzerumfrage-beantworten.md)). Ein Stand außerhalb des Repositorys mit Umsetzung ist nicht belegt.

**Wahrscheinlichkeit / Schaden:** Eintritt sicher (kein Backend-Code) / niedrig bis mittel (Fehlbedienung, Ziel [Z-006](../01-ziele/Z-006-nutzungserkenntnisse-fuer-die-produktentwicklung-gewinnen.md) verfehlt). **Maßnahme:** Backend ergänzen oder Funktion entfernen.
