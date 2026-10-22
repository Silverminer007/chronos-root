# ADR-2026-10-22-024: Öffentliche Seiten – Anmelden-Button, Überschrift-Icons, Farbe, Kartenrundung

Status: akzeptiert (Ticket #235, Abschluss der Angleichung nach #230/#233)

## Kontext
Seit #230 wich Angular vom alten Frontend ab: „Anmelden" war weiß gefüllt (`btn-light bg-white text-primary`), die
Icons in den Abschnittsüberschriften fehlten, Datenschutz-Überschriften nutzten `text-body` (grau statt gray-800), und
`.glass-card` rundet global mit `--bs-border-radius-xxl` (32 px) statt 16 px. Ursache: Klassen wurden pro Seite
frei gewählt statt gegen berechnete Werte des alten Frontends geprüft.

## Entscheidung
- „Anmelden": `btn-outline-white border-2` (eigene Klasse, da Bootstrap-Light #f8f9fa statt Weiß ist) (transparent, 2 px weiß).
- Neue Klassen `.legal-heading` (Flex, Icon oben links, Text bricht um, gray-800, Icon lila) und
  `.glass-card.legal-card` (16 px) in `_theme.scss`. Die globale `.glass-card`-Rundung bleibt, andere Seiten sind nicht betroffen.
- Innenabstand ist bereits `p-4` = 24 px.

## Test
`frontend-angular/e2e/public-pages-parity.spec.ts` (getComputedStyle, 390x844).
