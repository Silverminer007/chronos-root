---
id: FA-013
title: Anmeldung über Keycloak und Onboarding
status: entwurf
typ: anforderung
quellen: [frontend/app/middleware/auth.global.ts, frontend/server/api/auth/, frontend/app/pages/onboarding.vue, frontend/e2e/auth-redirects.spec.ts]
verknuepft: [F-013]
---

# FA-013: Anmeldung über Keycloak und Onboarding

*Als Benutzer möchte ich mich über Keycloak anmelden und kurz eingeführt werden, damit ich Chronos sofort nutzen kann.*

## Akzeptanzkriterien
- Die Landingpage `/` zeigt den Slogan „Terminplanung, die funktioniert“, drei Beispieltermine, vier Nutzenkacheln (Termine organisieren, Verfügbarkeit abfragen, Push-Benachrichtigungen, Einfache Zusagen), „Mehr erfahren“ (springt zu den Kacheln) und eine Fußzeile mit Impressum und Datenschutz ([FA-017](FA-017-impressum-und-datenschutzerklaerung-bereitstellen.md)).
- Die Landingpage bietet „Anmelden“ (Keycloak-Anmeldung) und „Registrieren“ bzw. „Jetzt kostenlos starten“ (Keycloak-Registrierung); nach erfolgreicher Anmeldung öffnet sich die Agenda.
- Ohne gültige Sitzung führt jeder geschützte Pfad auf die Landingpage `/`; die Pfade unter `/public` bleiben ohne Anmeldung erreichbar (E2E-Test).
- Angemeldete Personen werden von `/` auf `/agenda` geleitet.
- Beim ersten Besuch in einem Browser (noch nicht als gesehen markiert) öffnet sich vor jeder anderen Seite die Einführung mit sechs Schritten (Willkommen, Termine, Freunde, Gruppen, Rollen, Benachrichtigungen); „Überspringen“ und „Loslegen“ markieren sie als gesehen und führen zur Agenda. Die Markierung liegt im Browser (`localStorage`), nicht am Konto; auf jedem neuen Gerät oder nach dem Löschen der Browserdaten erscheint die Einführung erneut.
- Abmelden beendet die Keycloak-Sitzung und löscht die Sitzungsdaten im Browser.
- Die Sitzung bleibt ohne sichtbare Unterbrechung bestehen ([QA-004](../../06-qualitaet/QA-004-bedienbarkeit-sitzung-ohne-sichtbaren-token-ablauf.md)).
