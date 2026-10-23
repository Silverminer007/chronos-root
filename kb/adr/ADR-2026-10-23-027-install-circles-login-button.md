# ADR-2026-10-23-027: Install-Ziffernkreise hell, 'Anmelden'-Button weiß gefüllt

Status: akzeptiert (Ticket #241)

## Kontext
`.step-num` war voll violett mit weißer Zahl, `.btn-outline-white` transparent mit weißem Rahmen/weißer Schrift. Das alte
Frontend (`install.vue`, `index.vue`) nutzt `bg-purple-100 text-purple-600` bzw. `bg-white text-purple-600 border-2
border-purple-600 rounded-lg`. Ursache wie bei #230/#235/#239: Werte nicht gegen das alte Frontend geprüft (ADR-026
erwähnte den Header-Rahmen ausdrücklich als offen).

## Entscheidung
`.step-num`: purple-100-Grund, lila Zahl, 14 px/500. `.btn-outline-white` (nur in `landing-layout` verwendet, daher
keine Auswirkung auf andere Seiten): weißer Grund, lila Schrift und Rahmen, 0,5 rem Radius, Hover purple-50.

## Test
`frontend-angular/e2e/install-header-circles-parity.spec.ts`.
