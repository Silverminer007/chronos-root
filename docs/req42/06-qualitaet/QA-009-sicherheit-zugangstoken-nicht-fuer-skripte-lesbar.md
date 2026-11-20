---
id: QA-009
title: Sicherheit: Zugangstoken nicht für Skripte lesbar
status: entwurf
typ: qualitaetsanforderung
quellen: [frontend/server/api/auth/callback.get.ts, frontend/server/api/auth/refresh.post.ts]
verknuepft: [FA-013]
---

# QA-009: Sicherheit: Zugangstoken nicht für Skripte lesbar

**Qualitätsmerkmal (ISO 25010):** Sicherheit (Vertraulichkeit)

## Szenario
Skripte, die im Browser der Person laufen, können Zugriffs- und Refresh-Token nicht auslesen; nur der Ablaufzeitpunkt des Zugriffstokens (Cookie `kc_expires`) ist lesbar; die Token-Cookies sind `httpOnly` und `secure`. Zugriff auf Termindaten bleibt Teilnehmern vorbehalten ([FA-001](../04-product-backlog/anforderungen/FA-001-termine-anlegen-aendern-verschieben-absagen-loeschen.md)), auf Administration der Rolle `ADMIN_API` ([FA-007](../04-product-backlog/anforderungen/FA-007-admin-api.md)).

## Messbar durch
Kein automatisierter Test. Weitere Datenschutzanforderungen (Löschkonzept, Auftragsverarbeitung): [RA-004](../12-risiken-annahmen/RA-004-datenschutzerklaerung-verspricht-mehr-als-die-anwendung-leistet.md).
