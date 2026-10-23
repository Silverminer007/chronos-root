# ADR-2026-10-22-023: Überschriften öffentlicher Seiten brechen um

Status: akzeptiert (Ticket #233, Regression nach #230)

## Kontext
Die Klasse `.section-heading` (`white-space: nowrap; overflow: hidden; text-overflow: ellipsis`) wurde fälschlich als
"kompakt, ohne Umbruch (Nuxt)" in `_theme.scss` eingeführt. Bei 390 px wurde z. B. „Verantwortlich für den Inhalt"
auf Impressum/Datenschutz mit „…" abgeschnitten. Das alte Frontend bricht diese Überschriften auf zwei Zeilen um.

## Entscheidung
Die Klasse und alle Verwendungen (Impressum, Datenschutz) werden entfernt; Überschriften verwenden nur Bootstrap-Klassen
(`h5 fw-bold mb-3`) und brechen normal um. Überschriften dürfen nie per Ellipsis gekürzt werden; `text-truncate` bleibt
Namen/E-Mail-Zeilen in Listenkarten vorbehalten.

## Test
`frontend-angular/e2e/public-headings-wrap.spec.ts` (390x844: Impressum, Datenschutz, Install).
