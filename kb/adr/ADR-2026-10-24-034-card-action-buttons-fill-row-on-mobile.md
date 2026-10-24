# ADR-034: Aktions-Buttons der Termin-Karten füllen die Zeile bis `sm`

- Status: akzeptiert (Ticket #248)

## Kontext
Die Buttons Zusagen/Nachricht/Absagen der Agenda-Karte (und der Demo-Karte der Landingpage) waren in einem
`d-flex flex-wrap gap-2`-Container ohne Wachstum. Bei 390 px blieben sie kleine Icon-Buttons (~42 px) am linken
Rand; im Nuxt-Frontend gilt `flex-1 sm:flex-initial`.

## Entscheidung
Jeder Button erhält `flex-grow-1 flex-sm-grow-0`: bis `sm` teilen sich die drei Buttons die Zeile zu gleichen
Teilen (gleiche Basisbreite, gleiches Wachstum), ab `sm` Inhaltsbreite. Gleicher Defekt in
`appointment-demo-card.component.html` mitbehoben. Abgesichert durch `e2e/agenda-card-actions-width.spec.ts`.
