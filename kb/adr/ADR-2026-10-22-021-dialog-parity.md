# ADR-2026-10-22-021: Dialoge und Anlegen-Stepper wie im alten Frontend

**Status:** Accepted (Ticket #222)

## Ursache
Das Theme passte nur Karten/Formulare an; Bootstrap-Defaults für `.modal-title` (Gewicht 500, 1.25rem), `.modal-footer`
(Buttons nebeneinander rechtsbündig) und der Platzhalter (`--bs-secondary-color`) blieben wirksam.

## Entscheidung
Globale Regeln in `styles/_theme.scss`: `.modal-title` 18 px/700, `.modal-footer` als Spalte (Spalte, `order: -1` setzt die
Primäraktion oben steht, Buttons volle Breite), 16 px Modal-Rand, Rundung 1rem; `.line-input::placeholder`
gray-300. Globale statt je Dialog-Komponente, damit alle Dialoge einheitlich bleiben.

## Konsequenzen
Neue Dialoge nutzen die Standard-Bootstrap-Struktur ohne eigene Footer-Klassen; Primär-Button steht im DOM nach
"Abbrechen". Absicherung: `e2e/dialog-parity.spec.ts`. Zusätzlich umgesetzt: Dialog-Eingabefelder 52 px/2-px-grauer Rahmen (`.modal .form-control`, Icon links über `.input-group`), Stepper mit größerer Kachel (64 px, 12 px Rundung, Symbol "T"), lila dickerem Namens-Unterstrich, grauer "Abbrechen"-Schaltfläche, hellgrau deaktiviertem "Weiter" und grauem Schließen-Symbol/Schrittzähler.
