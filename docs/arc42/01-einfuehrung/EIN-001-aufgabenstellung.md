---
id: EIN-001
title: "Aufgabenstellung"
status: entwurf
typ: einfuehrung
quellen:
  - CLAUDE.md
  - docs/vision/product-vision.md
verknuepft: [Z-001]
---

# EIN-001: Aufgabenstellung

Chronos ist eine Anwendung zur Gruppenplanung und Terminverwaltung für Jugendorganisationen. Benutzer legen Termine an, laden Freunde und Gruppen ein, verfolgen die Teilnahme und erhalten Push-Benachrichtigungen. Die gesamte Oberfläche ist deutsch (RB-001).

Das System besteht aus einem Nuxt-4-/Vue-3-PWA-Frontend (SPA), einem Quarkus-3-Backend (Java 21) als Referenz, einem Rust-Backend als Reimplementierung und einem Helm-Chart für Kubernetes. Ziel: siehe Z-001; ausführliche Vision: `docs/vision/product-vision.md` (Entwurf).
