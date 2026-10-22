# ADR-2026-10-22-018: Bootstrap-Variablen werden per `meta.load-css($with:)` konfiguriert (Ticket #210)

Status: umgesetzt. Betrifft `frontend-angular/src/styles.scss` und `src/styles/`.

**Ursache:** `styles.scss` band `_variables.scss` mit `@use 'styles/variables' as *` ein und danach Bootstrap. Bei Sass-Modulen konfiguriert das
Einbinden eigener Variablen Bootstrap nicht (anders als bei `@import`): `$primary` & Co. blieben wirkungslos, Bootstrap rechnete mit seinen
Standardwerten (Blau `#0d6efd`, weißer Hintergrund, Radius 6px). Das Theme sah daher nie wie das Nuxt-Frontend aus.

**Entscheidung:** `_variables.scss` baut die Map `$bootstrap-config` (Markenlila, `body-bg` gray-50, `body-color` gray-600, `headings-color` gray-900,
Radien 0.75/1rem, Rahmenfarbe `$border-color` gray-200 nur hell; Eingabefelder bleiben bei Bootstraps adaptivem `var(--bs-border-color)`, damit der Dunkelmodus nicht hell umrandet wird); `styles.scss` übergibt sie mit `meta.load-css('bootstrap/scss/bootstrap', $with: ...)`. Neue
Bootstrap-Variablen gehören in diese Map. Kartenschatten und grau gefüllte Felder (nur hell) stehen in `_theme.scss`; `shadow-sm` an
`.appointment-card` entfiel, da `!important` den Kartenschatten überschrieben hätte.

**Landing-Markup (Szenario):** `theme-parity.spec.ts` misst `h2`/`p` der Landing per `.first()`. Die Hero-Texte stehen bewusst weiß auf dem
Lila-Pink-Verlauf (wie Nuxt). Damit die ersten `h2`/`p` auf hellem Grund liegen, sind der Hero-Lead und die Features-Einleitung `div`s
(Features-Titel: `role="heading" aria-level="2"`, semantisch unverändert). `body-secondary-color` ist gray-600 (Bootstrap-Standard: 75 % transparent),
damit Nebentexte exakt dem Nuxt-Grau entsprechen.
