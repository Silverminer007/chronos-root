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

## Hintergrund (Review #240)
Am alten Frontend (`chronos.localhost:3301`, 390x844, Landing und Install) gemessen: `linear-gradient(to right bottom,
purple-600 0 %, pink-500 50 %, orange-400 100 %)` (oklch), darüber ein Wrapper mit `opacity: 0.2`, darin zwei weiße
Blobs (`mix-blend-mode: overlay`, `blur(24px)`, Puls 0,2–0,4). Farbstopps und Richtung waren im Angular-Layout
gleich bzw. gleichwertig; abweichend war nur der fehlende Wrapper (Blobs wirkten mit 0,2–0,4 statt 0,04–0,08 zu
hell, das ergab den „Lichtfleck“). `landing-layout` hat nun `.blobs { opacity: .2 }` und `to bottom right`.
Der Header-Rahmen ist weiterhin nicht Teil dieses Fixes.

## Test
`frontend-angular/e2e/install-page-parity.spec.ts` (getComputedStyle, 390x844).
