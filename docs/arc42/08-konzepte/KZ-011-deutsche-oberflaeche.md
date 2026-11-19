---
id: KZ-011
title: Deutsche Oberfläche
status: entwurf
typ: konzept
quellen:
  - kb/constraints/C2025-09-28-001-german-ui.md
verknuepft: [RB-001, KZ-005]
---

# Deutsche Oberfläche

Alle nutzersichtbaren Texte, Fehlermeldungen und Toasts sind deutsch; keine i18n-Bibliothek. Fehlercodes des Backends werden im Frontend gemappt. E2E-Tests prüfen deutsche Texte. Code, Typen und API-Namen bleiben englisch (Zuordnung: `kb/glossary/begriffe.md`).
