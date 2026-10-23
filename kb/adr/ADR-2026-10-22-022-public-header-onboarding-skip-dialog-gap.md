# ADR-2026-10-22-022: Kopfzeile öffentlicher Seiten, "Überspringen", Dialog-Button-Abstand

**Status:** Accepted (Ticket #230)

## Ursache
- Landing-Kopfzeile: 40-px-Logo, `fs-3`-Titel und zwei Standard-Buttons ("Anmelden" mit Rahmen/Schatten, "Registrieren") sind bei 390 px zusammen breiter als der Viewport; die Button-Gruppe durfte nicht schrumpfen, "Registrieren" ragte rechts hinaus.
- "Überspringen" ist ein `btn-link` (Bootstrap unterstreicht Links).
- `.modal-footer > .btn { margin: 0 }` (ADR-021) entfernte Bootstraps Margin-Abstand, ohne ihn zu ersetzen; die gestapelten Buttons berührten sich.

## Entscheidung
- Kopfzeile: 32-px-Logo, `fs-4`-Titel, `btn-sm`, "Anmelden" bleibt weiß mit lila Rahmen (Theme-Parität #210), nur als `btn-sm`, Button-Gruppe `flex-shrink-0`.
- "Überspringen": `text-decoration-none`.
- `.modal-footer` bekommt `gap: 0.75rem` (alt ca. 12 px) statt Margins; Abstand kommt vom Container, nicht von Kind-Margins.

## Konsequenzen
Neue Spalten-Layouts mit `margin: 0` auf Kindern brauchen `gap` am Container. Absicherung: `e2e/public-header-onboarding-parity.spec.ts`.

## Weitere Punkte des Tickets (ebenfalls in diesem Fix)
- Onboarding: Konfetti-Symbol (Lucide `party-popper`) im ersten Schritt, satterer Diagonalverlauf (purple-600 → pink-500 → orange-400) und "Weiter"-Verlauf Lila → Magenta mit Schatten wie im alten Frontend.
- Impressum/Datenschutz: Kartenabstand, Abschnittsüberschriften und Hinweis "nicht konfiguriert" (amber) wie im alten Frontend.
- Stepper "Termin anlegen", Schritt 1: Lucide-`type`-Umriss-T statt fettem Buchstaben.
