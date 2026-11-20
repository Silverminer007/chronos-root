---
id: RA-013
title: Vom Product Owner festzulegende Werte und Entscheidungen
status: entwurf
typ: annahme
quellen: [docs/vision/product-vision.md, .github/CODEOWNERS, GitHub-Tickets #380]
verknuepft: [SH-001]
---

# RA-013: Vom Product Owner festzulegende Werte und Entscheidungen

Annahme: Solange nichts anderes festgelegt ist, gelten die aus dem Code abgeleiteten Inhalte dieses req42 als Arbeitsstand. Die folgenden Punkte lassen sich nicht aus Code, Konfiguration oder Tickets ableiten und muss ein Mensch festlegen. Sie stehen **nur hier** (und, soweit zeitlich, in der [Roadmap](../11-roadmaps/README.md)); die übrigen Dateien verweisen auf diese Datei, statt die Frage zu wiederholen.

| Bereich | Offene Festlegung **(?)** | Betrifft |
|---|---|---|
| Ziele | Zielwerte und Zeitraum je Metrik (die Messgrößen stehen in den Zielen) | [Z-001](../01-ziele/Z-001-termine-gemeinsam-planen-und-teilnahme-nachverfolgen.md) bis [Z-006](../01-ziele/Z-006-nutzungserkenntnisse-fuer-die-produktentwicklung-gewinnen.md) |
| Ziele | Bestätigung der aus dem Code abgeleiteten Ziele, insbesondere [Z-006](../01-ziele/Z-006-nutzungserkenntnisse-fuer-die-produktentwicklung-gewinnen.md) (abgeleitet aus den Umfragefragen) | Ziele |
| Stakeholder | Namen und Einfluss der Nutzergruppen sowie Personen für Administration und Betrieb; Größe der Nutzergruppen | [SH-002](../02-stakeholder/SH-002-gruppenleitung-rolle-responsible.md) bis [SH-007](../02-stakeholder/SH-007-entwicklung-und-agenten.md) |
| Scope | Freigabe der Produktvision und Bestätigung, dass E-Mail-Benachrichtigung, Kontolöschung in der App und Mehrsprachigkeit bewusst fehlen | [SC-002](../03-scope/SC-002-im-und-nicht-im-scope.md) |
| Backlog | Rang und Priorität der Features und Anforderungen; ob „Termin löschen“ in der Oberfläche angeboten wird (Ticket #380) | [Product Backlog](../04-product-backlog/README.md) |
| Qualität | Zielwerte: Sitzungsdauer ([QA-004](../06-qualitaet/QA-004-bedienbarkeit-sitzung-ohne-sichtbaren-token-ablauf.md)), erwartete Nutzerzahl, Antwortzeit und Verfügbarkeit ([QA-005](../06-qualitaet/QA-005-performance-unter-last.md), [RA-009](RA-009-nutzungsumfang-und-last-sind-unbekannt.md)), maximaler Datenverlust und Wiederanlaufzeit samt Wiederherstellungstest ([QA-006](../06-qualitaet/QA-006-zuverlaessigkeit-datensicherung.md)), Alarmierungskanal ([QA-007](../06-qualitaet/QA-007-beobachtbarkeit.md)), Zielgeräte und Mindestbreiten ([QA-011](../06-qualitaet/QA-011-bedienbarkeit-auf-dem-smartphone.md)), ob die Lighthouse-Prüfung in die CI aufgenommen wird ([QA-010](../06-qualitaet/QA-010-web-qualitaet-der-oeffentlichen-seiten.md)) | Qualität |
| Randbedingungen | Rechtliche Einordnung und Umfang von Datenschutz und Impressum ([RB-014](../07-randbedingungen/RB-014-datenschutz-nach-dsgvo-und-impressumspflicht.md), [RA-004](RA-004-datenschutzerklaerung-verspricht-mehr-als-die-anwendung-leistet.md)); Einrichtung eines Branch-Schutzes für `main` und `develop` ([RB-005](../07-randbedingungen/RB-005-aenderungen-an-main-nur-per-pull-request.md)) | Randbedingungen |
| Betriebsmittel | Budget, Zeitrahmen und Endtermin, Betriebskosten, Vertragslaufzeiten, Betreiber von Keycloak, Kosten der Modellnutzung der Agenten, Lizenz des Repositorys und der Abhängigkeiten, Fortbestand von OpenProject | [BM-001](../09-betriebsmittel-personal/BM-001-laufzeitinfrastruktur.md) bis [BM-003](../09-betriebsmittel-personal/BM-003-agentenplattform.md) |
| Team | Weitere Teammitglieder, Teilteams und Vertretungsregeln ([RA-010](RA-010-betrieb-und-entwicklung-haengen-an-einer-person.md)) | [TS-001](../10-teamstruktur/TS-001-rollen-und-zusammenarbeit.md) |
| Roadmap | Zeitplan, Reihenfolge und Releasetermine aller Roadmap-Einträge; ob das Angular-Frontend das Nuxt-Frontend ersetzt ([RM-001](../11-roadmaps/RM-001-angular-frontend-nach-figma.md)) | [Roadmap](../11-roadmaps/README.md) |

Fachliche Einzelentscheidungen mit eigener Datei: [RA-004](RA-004-datenschutzerklaerung-verspricht-mehr-als-die-anwendung-leistet.md) (Datenschutzerklärung), [RA-005](RA-005-erinnerungen-teilweise-ohne-wirkung.md) (Terminerinnerung), [RA-006](RA-006-nutzerumfrage-ohne-backend.md) (Umfrage), [RA-007](RA-007-berechtigung-zum-aendern-von-terminen-ist-weit-gefasst.md) (Rechte von `ATTENDANT` und `HELPER`), [RA-011](RA-011-nutzer-registrieren-sich-selbst-bei-keycloak.md) (Registrierung), [RA-012](RA-012-standardeinstellungen-unterdruecken-einladungsbenachrichtigung.md) (Einladungsbenachrichtigung).
