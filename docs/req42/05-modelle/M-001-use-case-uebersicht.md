---
id: M-001
title: Use-Case-Übersicht
status: entwurf
typ: modell
quellen: [backend/src/main/java/de/chronos_live/chronos_date_api/application/AuthorizationService.java, frontend/app/pages/]
verknuepft: [F-001, F-002, F-004, F-005, F-006, F-008, F-009, F-010, F-012, F-013, F-014, F-015, F-016, F-017, F-018, F-019, F-020]
---

# M-001: Use-Case-Übersicht

```mermaid
flowchart LR
  V[Verantwortliche/r] --> U1(Termin anlegen, ändern, verschieben, absagen)
  V --> U2(Personen und Gruppen einladen, Rollen ändern)
  T[Teilnehmer, Helfer] --> U3(Zu- oder absagen)
  T --> U4(Nachrichten lesen und schreiben)
  T --> U5(Agenda durchsuchen, Termin ansehen und teilen)
  B[Benutzer] --> U6(Freunde und Gruppen verwalten)
  B --> U10(Umfrage beantworten, Neuigkeiten sehen)
  B --> U7(Benachrichtigungen einstellen)
  B --> U8(Profil und Anmeldung verwalten)
  A[Administrator] --> U9(Benutzer, Daten, Push betreuen)
  G[Besucher ohne Anmeldung] --> U11(Impressum, Datenschutz, Installationsanleitung lesen)
  G --> U12(Anmelden oder registrieren)
```

| Use Case | Akteur | Anforderung |
|---|---|---|
| Termin anlegen, ändern, verschieben, absagen | Verantwortliche/r (Ändern ab `ATTENDANT`, siehe [M-006](M-006-rollenmatrix.md)) | [FA-001](../04-product-backlog/anforderungen/FA-001-termine-anlegen-aendern-verschieben-absagen-loeschen.md), [FA-008](../04-product-backlog/anforderungen/FA-008-terminaenderungen-nachvollziehbar-mitteilen.md) |
| Personen, Gruppen einladen; Rollen ändern | Verantwortliche/r | [FA-002](../04-product-backlog/anforderungen/FA-002-personen-und-gruppen-einladen.md), [FA-010](../04-product-backlog/anforderungen/FA-010-rolle-eines-teilnehmers-aendern.md) |
| Zu- oder absagen | Teilnehmer | [FA-003](../04-product-backlog/anforderungen/FA-003-zu-und-absage-mit-mindestteilnehmerzahl.md) |
| Nachrichten | Teilnehmer | [FA-005](../04-product-backlog/anforderungen/FA-005-nachrichten-zu-einem-termin.md) |
| Agenda, Suche, Termin ansehen, Teilen | Teilnehmer | [FA-009](../04-product-backlog/anforderungen/FA-009-terminliste-paginiert-abrufen.md), [FA-020](../04-product-backlog/anforderungen/FA-020-termin-im-detail-ansehen.md), [FA-018](../04-product-backlog/anforderungen/FA-018-termin-teilen.md) |
| Freunde und Gruppen verwalten | Benutzer | [FA-006](../04-product-backlog/anforderungen/FA-006-freundschaften-und-gruppen-verwalten.md) |
| Benachrichtigungen einstellen und empfangen | Benutzer | [FA-004](../04-product-backlog/anforderungen/FA-004-push-benachrichtigungen.md), [FA-011](../04-product-backlog/anforderungen/FA-011-automatische-erinnerungen.md), [FA-012](../04-product-backlog/anforderungen/FA-012-benachrichtigungen-konfigurieren.md) |
| Anmelden, registrieren, Einführung | Besucher, Benutzer | [FA-013](../04-product-backlog/anforderungen/FA-013-anmeldung-ueber-keycloak-und-onboarding.md) |
| Profil und Konto verwalten | Benutzer | [FA-014](../04-product-backlog/anforderungen/FA-014-profil-und-konto-verwalten.md) |
| Umfrage beantworten, Neuigkeiten sehen | Benutzer | [FA-015](../04-product-backlog/anforderungen/FA-015-nutzerumfrage-beantworten.md), [FA-019](../04-product-backlog/anforderungen/FA-019-neuigkeiten-hinweis.md) |
| Öffentliche Seiten lesen, App installieren | Besucher | [FA-017](../04-product-backlog/anforderungen/FA-017-impressum-und-datenschutzerklaerung-bereitstellen.md), [FA-016](../04-product-backlog/anforderungen/FA-016-installation-als-pwa.md) |
| Betreuen | Administrator | [FA-007](../04-product-backlog/anforderungen/FA-007-admin-api.md) |
