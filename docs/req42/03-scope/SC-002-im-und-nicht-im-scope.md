---
id: SC-002
title: Im und nicht im Scope
status: entwurf
typ: scope
quellen: [docs/vision/product-vision.md, frontend/app/pages/, backend/src/main/java/de/chronos_live/chronos_date_api/presentation/]
verknuepft: []
---

# SC-002: Im und nicht im Scope

Die Grenze des Produkts verläuft zwischen dem System und den Nachbarn aus [SC-001](SC-001-systemkontext-akteure-und-nachbarsysteme.md). Der Product Owner bestätigt die Abgrenzung ([RA-013](../12-risiken-annahmen/RA-013-vom-product-owner-festzulegende-werte-und-entscheidungen.md)).

**Im Scope** (Features: [04-product-backlog/features](../04-product-backlog/features/README.md)):
- Termine anlegen, ändern, verschieben, absagen und suchen (F-001, F-002, F-003).
- Personen und Gruppen zu Terminen einladen und Rollen vergeben (F-004, F-007), Zu- und Absage (F-005).
- Freundschaften und Gruppen verwalten (F-006, F-008).
- Nachrichten im Kontext eines Termins (F-009).
- Push-Benachrichtigungen, Erinnerungen und deren Einstellungen (F-010 bis F-012).
- Anmeldung über Keycloak, Profilpflege, Administration (F-013 bis F-015).
- Nutzerumfrage, PWA-Installation, Impressum und Datenschutzerklärung, Teilen und Neuigkeiten (F-016 bis F-020).

**Bewusst ausgeschlossen** (laut [Produktvision](../../vision/product-vision.md), noch nicht freigegeben):
- Chronos ist kein allgemeiner Kalender.
- Chronos ist kein Chat-Ersatz; Nachrichten gibt es nur im Kontext eines Termins.

**Im Code nicht vorhanden** (keine Absicht erkennbar):
- Benachrichtigung per E-Mail; es gibt nur Web Push ([RB-006](../07-randbedingungen/RB-006-benachrichtigung-nur-ueber-web-push.md)).
- Eigene Benutzer- und Passwortverwaltung; sie liegt bei Keycloak ([RB-002](../07-randbedingungen/RB-002-identitaetsprovider-ist-keycloak.md)).
- Löschen des eigenen Kontos in der App; der Löschaufruf wird mit `405` abgelehnt, nur Administratoren löschen Benutzer ([FA-014](../04-product-backlog/anforderungen/FA-014-profil-und-konto-verwalten.md)).
- Mehrsprachigkeit ([RB-001](../07-randbedingungen/RB-001-alle-ui-texte-sind-deutsch.md)).
- Fehlerberichte an Sentry ([SC-001](SC-001-systemkontext-akteure-und-nachbarsysteme.md)).

**Geplant, aber noch nicht umgesetzt:** Teams statt Freunde, Einladungslinks, Termin-Typen, Terminfindung, Kalender-Feed, externe Teilnehmer, Rückmeldeoption „Zeitweise“ (siehe [Roadmap](../11-roadmaps/README.md)).
