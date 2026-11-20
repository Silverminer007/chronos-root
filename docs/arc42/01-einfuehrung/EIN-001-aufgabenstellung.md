---
id: EIN-001
title: "Aufgabenstellung"
status: entwurf
typ: einfuehrung
quellen:
  - docs/vision/product-vision.md
  - backend/pom.xml
  - frontend/package.json
  - deployment/Chart.yaml
  - Makefile
verknuepft: [EIN-002, EIN-003, EIN-004, BB-019, Z-001]
---

# EIN-001: Aufgabenstellung

Chronos ist eine Anwendung zur Gruppenplanung und Terminverwaltung für Jugendorganisationen. Berechtigte legen Termine an, laden Freunde und ganze Gruppen ein, verfolgen Zu- und Absagen gegen eine Mindestteilnehmerzahl und werden per Web Push benachrichtigt und erinnert. Zielgruppe, Vision und Erfolgskriterien: [docs/vision/product-vision.md](../../vision/product-vision.md) (Entwurf); Ziele und Anforderungen: [req42](../../req42/README.md).

Das System besteht aus vier selbst entwickelten Teilen (Aufbau: [BB-019](../05-bausteine/BB-019-whitebox-gesamtsystem.md)):

| Teil | Verzeichnis | Technik |
|---|---|---|
| Frontend (PWA) | `frontend/` | Nuxt 4, Vue 3, als SPA ohne SSR |
| Backend | `backend/` | Quarkus 3.37 auf Java 21, REST unter `/api/v2` |
| Deployment | `deployment/` | Helm-Chart für Kubernetes |
| Entwicklungsumgebung | `Makefile`, `backend/docker-compose.yaml` | siehe [VT-007](../07-verteilung/VT-007-lokale-entwicklungsumgebung.md) |

Hinzu kommen extern betriebene Systeme (Keycloak, Push-Dienste, S3 u. a.), siehe [Kapitel 3](../03-kontext/README.md). Bedienoberfläche und nutzersichtbare Texte sind deutsch (RB-001, [KZ-011](../08-konzepte/KZ-011-deutsche-oberflaeche.md)).
