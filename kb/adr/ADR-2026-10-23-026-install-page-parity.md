# ADR-2026-10-23-026: Install-Seite – Überschrift, Badge, Ziffernkreise, Linkstil, Abschnittsüberschriften

Status: akzeptiert (Ticket #239)

## Kontext
`/public/install` nutzte generische Bootstrap-Klassen (`display-5`, `text-bg-primary`, `h6`, native `<ol>`) und wich
damit vom alten Frontend ab (h1 30,99 statt 30 px, dunkles Badge mit weißer Schrift, native Ziffern, unterstrichener
Link, h3 16 statt 18 px, schwarzes Icon). Ursache wie bei #230/#235: Klassen wurden frei gewählt statt gegen die
berechneten Werte des alten Frontends geprüft.

## Entscheidung
Eigene Klassen in `_theme.scss`: `.install-title` (30 px, schmale Breite → zwei Zeilen), `.install-subtitle`
(max. 330 px), `.install-badge` (purple-100/purple-700, Gewicht 500), `.install-heading` (18 px bold gray-800, lila
Icon), `.install-steps` (24-px-Ziffernkreis `.step-num`, Link fett lila ohne Unterstreichung). Die Ziffern stehen im
Template als `<span class="step-num">`, native Listenziffern sind aus.

## Offen
Hintergrund der öffentlichen Seiten (Verlauf/Lichtfleck) und Header-Rahmen sind nicht Teil dieses Fixes; der
Verlauf in `landing-layout` ist bereits diagonal Lila → Pink → Orange.

## Test
`frontend-angular/e2e/install-page-parity.spec.ts` (getComputedStyle, 390x844).
