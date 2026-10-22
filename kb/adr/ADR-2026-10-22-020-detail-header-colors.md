# ADR-2026-10-22-020: Farben im Verlaufskopf und Icon-Kachel (Angular, #221)

## Ursache
- Der Termindetail-Titel ist ein `h2.h3`; Bootstraps Überschriftenregel (`--bs-heading-color`) überschreibt das vom
  Container geerbte `color: #fff` von `.bg-gradient-brand`. Datum/Uhrzeit hatten zusätzlich `opacity-75`.
- `btn-light` setzt Hintergrund über `--bs-btn-bg`; `bg-opacity-25` greift dort nicht, die Knöpfe blieben deckend weiß.
- `.icon-tile` kannte nur die lila Variante, das Gruppendetail nutzt in Nuxt blue-100→indigo-100 / blue-600.

## Entscheidung
- Text im Kopf explizit `text-white`; Knöpfe über die neue Klasse `.btn-glass` (Bootstrap-Button-Variablen, Nuxt `bg-white/20`).
- Neue Variante `.icon-tile-blue` in `_theme.scss`, im Gruppendetail verwendet.
- Regel: Farben auf Bootstrap-Komponenten (Überschriften, Buttons) über deren CSS-Variablen/Klassen setzen, nicht über Utility-Opacity.
- Abgesichert durch `e2e/detail-colors.spec.ts` (`getComputedStyle`).
