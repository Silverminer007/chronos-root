---
title: Stakeholder
status: entwurf
---

# Stakeholder

Rollen und Personen mit Interesse an Chronos, mit Einfluss und Beitrag zur Anforderungsanalyse (Rolle vor Person). Die Rollen der Teilnehmer entsprechen `UserRole` im Code (Rechte: [M-006](../05-modelle/M-006-rollenmatrix.md)). Namen der Personen, Größe der Nutzergruppen und Einfluss der Anwender sind aus dem Code nicht ableitbar und stehen in [RA-013](../12-risiken-annahmen/RA-013-vom-product-owner-festzulegende-werte-und-entscheidungen.md).

| ID | Rolle | Person | Thema | Einfluss |
|---|---|---|---|---|
| [SH-001](SH-001-product-owner.md) | Product Owner | GitHub-Account `@Silverminer007` | Richtung, Priorität, Freigabe von `docs/` | hoch (Code-Owner) |
| [SH-002](SH-002-gruppenleitung-rolle-responsible.md) | Gruppenleitung (RESPONSIBLE) | Nutzerkreis | Termine anlegen und steuern | Anwender, über Rückmeldung und Umfrage |
| [SH-003](SH-003-teilnehmer-rollen-attendant-guest.md) | Teilnehmer (ATTENDANT, GUEST) | Nutzerkreis | Zu-/Absage, Nachrichten | Anwender, über Rückmeldung und Umfrage |
| [SH-004](SH-004-helfer-rolle-helper.md) | Helfer (HELPER) | Nutzerkreis | Unterstützung, Benachrichtigung | Anwender, über Rückmeldung und Umfrage |
| [SH-005](SH-005-administrator-rolle-admin-api.md) | Administrator (ADMIN_API) | Nutzer mit Keycloak-Rolle `ADMIN_API` | Betreuung, Fehlersuche | hoch auf die Daten (umgeht Prüfungen) |
| [SH-006](SH-006-betrieb.md) | Betrieb | Betreiber laut Impressum | Deployment, Monitoring, Backup | hoch auf Verfügbarkeit und Datenschutz |
| [SH-007](SH-007-entwicklung-und-agenten.md) | Entwicklung und Agenten | Entwickler, KI-Agenten | Umsetzung, Wiki | mittel (Merge nur nach Freigabe) |
