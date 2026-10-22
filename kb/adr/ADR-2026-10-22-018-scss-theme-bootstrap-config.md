# ADR-2026-10-22-018: Bootstrap-Variablen werden per `meta.load-css($with:)` konfiguriert (Ticket #210)

Status: umgesetzt. Betrifft `frontend-angular/src/styles.scss` und `src/styles/`.

**Ursache:** `styles.scss` band `_variables.scss` mit `@use 'styles/variables' as *` ein und danach Bootstrap. Bei Sass-Modulen konfiguriert das
Einbinden eigener Variablen Bootstrap nicht (anders als bei `@import`): `$primary` & Co. blieben wirkungslos, Bootstrap rechnete mit seinen
Standardwerten (Blau `#0d6efd`, weißer Hintergrund, Radius 6px). Das Theme sah daher nie wie das Nuxt-Frontend aus.

**Entscheidung:** `_variables.scss` baut die Map `$bootstrap-config` (Markenlila, `body-bg` gray-50, `body-color` gray-600, `headings-color` gray-900,
Radien 0.75/1rem, Eingabefeld-Rahmen gray-200); `styles.scss` übergibt sie mit `meta.load-css('bootstrap/scss/bootstrap', $with: ...)`. Neue
Bootstrap-Variablen gehören in diese Map. Kartenschatten und grau gefüllte Felder (nur hell) stehen in `_theme.scss`; `shadow-sm` an
`.appointment-card` entfiel, da `!important` den Kartenschatten überschrieben hätte.

**Offen:** `theme-parity.spec.ts` misst `h2`/`p` der Landing per `.first()`; das sind die weißen Hero-Texte auf dem Lila-Pink-Verlauf (wie im
Nuxt-Stand bewusst weiß). Dafür bleibt die Prüfung rot; die Farben auf hellem Grund (Fließtext, Überschriften) sind gray-600/gray-900.
