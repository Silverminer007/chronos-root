# ADR-2026-10-22-019: Kartenüberschriften im Angular-Frontend mit `.card-heading` (18 px)

**Status:** Accepted (Ticket #219)

## Kontext
Nuxt setzt Kartenüberschriften mit `text-lg` (18 px). Das Angular-Frontend nutzte Bootstrap `.h5` (1.25rem = 20 px).
Dazu kam auf `/settings` bei 390 px Platzmangel: "Benachrichtigungen"/"Terminbenachrichtigungen" brachen mitten im Wort um.

## Ursache
Falsche Typo-Abbildung (`.h5` ≠ `text-lg`) und zu viel horizontales Innenpadding (p-4, Icon-Kachel, form-switch-Padding).

## Entscheidung
- Eigene Klasse `.card-heading` (1.125rem) in `styles/_theme.scss` für alle Kartenüberschriften statt `.h5`.
- Einstellungs-Karten auf < sm: `p-3`, Push-Switch ohne `ps-2.5em`, Icon-Kachel der Termin-/Gruppenkarte ausgeblendet
  ("Gruppenbenachrichtigungen" ist in der System-Schrift ~290 px breit). Nuxt nutzt eine schmalere Webfont.
- Header-Logo wie Nuxt: 32 px mobil/40 px ab sm, `rounded-lg`, ohne Kasten.

## Konsequenzen
Neue Kartenüberschriften verwenden `.card-heading`, nicht `.h5`. Absicherung: `e2e/card-headings.spec.ts`.
