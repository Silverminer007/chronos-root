---
id: RB-001
title: Alle UI-Texte sind deutsch
status: entwurf
typ: randbedingung
kategorie: organisatorisch
quellen: [frontend/e2e/landing.spec.ts, frontend/app/utils/errors.ts]
verknuepft: []
---

# RB-001: Alle UI-Texte sind deutsch

Alle nutzersichtbaren Texte, Fehler- und Hinweismeldungen sind auf Deutsch. Begründung: Zielgruppe sind Jugendorganisationen im deutschsprachigen Raum ([RA-001](../12-risiken-annahmen/RA-001-zielgruppe-jugendorganisationen-im-deutschsprachigen-raum.md)); eine Oberfläche in anderer Sprache gilt als Nutzungshindernis. Code-Namen bleiben englisch.

Bekannte Abweichung: Das Frontend zeigt Fehlertexte des Backends teils unverändert an (`getErrorMessage` bevorzugt `data.message`), und diese sind teils englisch (z. B. „You have to be responsible for this appointment“); die Randbedingung ist dort nicht durchgängig erfüllt.
