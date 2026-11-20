---
title: req42/arc42 rückwärts aus dem Code extrahieren (Vorgehen, Quellen, Scope)
type: lesson
sources:
  - docs/arc42/*/00-kapitelbeschreibung.md
  - docs/req42/*/00-kapitelbeschreibung.md
  - https://arc42.org/dl/arc42-template-DE-withhelp-gitHubMarkdownMP.zip
  - https://github.com/Hruschka/req42-framework/raw/main/dist/req42-framework-DE-withhelp-gitHubMarkdownMP.zip
confidence: mittel
last-verified: 2026-11-20
---

# req42/arc42 rückwärts extrahieren

- **Scope:** `rust-backend/` war ein Experiment und ist nicht in Betrieb. Es wird in `docs/` nicht dokumentiert (Entscheidung des Nutzers, 2026-11-20). Betrieben werden Java/Quarkus-Backend und Nuxt-Frontend.
- **Kapitelbeschreibungen:** je Kapitelordner `00-kapitelbeschreibung.md`, aus den offiziellen Vorlagen mit Hilfetexten (arc42 DE: `arc42.org/dl/…-withhelp-gitHubMarkdownMP.zip`; req42 DE: Download-Seite von req42.de, Repo `Hruschka/req42-framework`). Die GitHub-Pfade `…/arc42-template/raw/master/dist/…` liefern HTML statt ZIP; die arc42.org-URL funktioniert.
- **req42 hat 12 Artefakte** (Ziele, Stakeholder, Scope, Product Backlog, Modelle, Qualität, Randbedingungen, Terminologie, Betriebsmittel/Personal, Teamstruktur, Roadmaps, Risiken/Annahmen). Ordner `docs/req42/01…12`; Features und Anforderungen liegen unter `04-product-backlog/`. Nicht aus Code ableitbare Artefakte (Roadmap, Teamstruktur) nur als README mit Hinweis, nichts erfinden.
- **Qualitätsmaßstab:** nichts entfernbar ohne Bedeutungsänderung; keine Redundanz zwischen Kapiteln, stattdessen verlinken.
